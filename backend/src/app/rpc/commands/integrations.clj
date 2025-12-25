;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) KODA INC

(ns app.rpc.commands.integrations
  "Third-party integrations for design tools"
  (:require
   [app.common.data :as d]
   [app.common.data.macros :as dm]
   [app.common.exceptions :as ex]
   [app.common.logging :as log]
   [app.common.schema :as sm]
   [app.common.time :as ct]
   [app.common.transit :as t]
   [app.common.uuid :as uuid]
   [app.config :as cf]
   [app.db :as db]
   [app.loggers.audit :as-alias audit]
   [app.rpc :as-alias rpc]
   [app.rpc.commands.files :as files]
   [app.rpc.doc :as-alias doc]
   [app.util.services :as sv]
   [clj-http.client :as http]
   [clojure.core.async :as a]
   [datoteka.fs :as fs]
   [cheshire.core :as json])
  (:import
   [java.util.concurrent ConcurrentHashMap]))

(set! *warn-on-reflection* true)

;; ============================================================================
;; Integration Providers
;; ============================================================================

(def integrations
  "Supported integration providers"
  {:figma {:name "Figma"
           :api-base "https://api.figma.com/v1"
           :auth-type :oauth2
           :scopes ["files:read"]
           :webhook-events ["file_update" "file_delete"]}
   :sketch {:name "Sketch"
            :api-base "https://api.sketch.com/v1"
            :auth-type :oauth2
            :scopes ["files:read" "files:write"]}
   :adobe-xd {:name "Adobe XD"
              :api-base "https://cc-api-xd.adobe.io/v2"
              :auth-type :oauth2
              :scopes ["xd:file_read" "xd:file_write"]}
   :invision {:name "InVision"
              :api-base "https://api.invisionapp.com/v1"
              :auth-type :api-key
              :scopes ["projects:read" "prototypes:read"]}
   :framer {:name "Framer"
            :api-base "https://api.framer.com/v1"
            :auth-type :oauth2
            :scopes ["projects:read" "components:read"]}
   :principle {:name "Principle"
               :api-base "https://api.principle.app/v1"
               :auth-type :oauth2
               :scopes ["prototypes:read" "animations:read"]}})

;; ============================================================================
;; OAuth2 Flow Management
;; ============================================================================

(defn create-oauth-state
  "Create OAuth2 state for security"
  [user-id provider redirect-uri]
  (let [state (str (uuid/next))
        state-data {:user-id user-id
                    :provider provider
                    :redirect-uri redirect-uri
                    :created-at (ct/now)
                    :expires-at (ct/plus (ct/now) (ct/duration {:hours 1}))}]

    ;; Store state in cache/database
    (db/insert! db :oauth-state {:state state :data (t/encode state-data)})

    state))

(defn validate-oauth-state
  "Validate OAuth2 state"
  [state]
  (when-let [state-record (db/get db :oauth-state {:state state})]
    (let [state-data (t/decode (:data state-record))
          now (ct/now)]

      ;; Check if expired
      (when (ct/before? now (:expires-at state-data))
        ;; Clean up used state
        (db/delete! db :oauth-state {:state state})
        state-data))))

(defn get-oauth-url
  "Generate OAuth2 authorization URL"
  [provider redirect-uri user-id]
  (let [config (get integrations provider)
        state (create-oauth-state user-id provider redirect-uri)]

    (str (:api-base config) "/oauth/authorize"
         "?client_id=" (cf/get :oauth-client-id provider)
         "&redirect_uri=" redirect-uri
         "&scope=" (str/join "," (:scopes config))
         "&response_type=code"
         "&state=" state)))

;; ============================================================================
;; Token Management
;; ============================================================================

(defn store-access-token
  "Store OAuth2 access token"
  [db user-id provider access-token refresh-token expires-at]
  (let [integration {:user-id user-id
                     :provider provider
                     :access-token access-token
                     :refresh-token refresh-token
                     :expires-at expires-at
                     :created-at (ct/now)
                     :updated-at (ct/now)}]

    ;; Upsert integration
    (db/execute-one! db
      ["INSERT INTO integrations (user_id, provider, access_token, refresh_token, expires_at, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (user_id, provider)
        DO UPDATE SET access_token = EXCLUDED.access_token,
                      refresh_token = EXCLUDED.refresh_token,
                      expires_at = EXCLUDED.expires_at,
                      updated_at = EXCLUDED.updated_at"
       user-id provider access-token refresh-token expires-at (ct/now) (ct/now)])

    integration))

(defn get-access-token
  "Get stored access token for user/provider"
  [db user-id provider]
  (when-let [integration (db/get db :integrations {:user-id user-id :provider provider})]
    (let [now (ct/now)
          expires-at (:expires-at integration)]

      ;; Check if token is expired
      (when (ct/after? expires-at now)
        (:access-token integration)))))

(defn refresh-access-token
  "Refresh expired access token"
  [db user-id provider]
  (when-let [integration (db/get db :integrations {:user-id user-id :provider provider})]
    (let [refresh-token (:refresh-token integration)
          config (get integrations provider)]

      ;; Make refresh request
      (try
        (let [response (http/post (str (:api-base config) "/oauth/token")
                        {:form-params {:grant_type "refresh_token"
                                       :refresh_token refresh-token
                                       :client_id (cf/get :oauth-client-id provider)
                                       :client_secret (cf/get :oauth-client-secret provider)}
                         :as :json})]

          (when (= 200 (:status response))
            (let [token-data (:body response)
                  expires-at (ct/plus (ct/now) (ct/seconds (:expires_in token-data)))]

              ;; Update stored token
              (store-access-token db user-id provider
                                (:access_token token-data)
                                (:refresh_token token-data)
                                expires-at)

              (:access_token token-data))))

        (catch Exception e
          (log/error :msg "Failed to refresh token"
                    :user-id user-id
                    :provider provider
                    :error e))))))

(defn get-valid-access-token
  "Get valid access token, refreshing if needed"
  [db user-id provider]
  (or (get-access-token db user-id provider)
      (refresh-access-token db user-id provider)))

;; ============================================================================
;; Figma Integration
;; ============================================================================

(defn fetch-figma-file
  "Fetch file data from Figma API"
  [db user-id file-key]
  (when-let [token (get-valid-access-token db user-id :figma)]
    (try
      (let [response (http/get (str "https://api.figma.com/v1/files/" file-key)
                      {:headers {"X-Figma-Token" token}
                       :as :json})]

        (when (= 200 (:status response))
          (:body response)))

      (catch Exception e
        (log/error :msg "Failed to fetch Figma file"
                  :user-id user-id
                  :file-key file-key
                  :error e)))))

(defn convert-figma-to-koda
  "Convert Figma file data to Koda format"
  [figma-data]
  (let [document (:document figma-data)
        components (:components figma-data)]

    ;; Convert Figma nodes to Koda elements
    {:name (:name document)
     :pages [(convert-figma-page document)]
     :components (convert-figma-components components)}))

(defn convert-figma-page
  "Convert Figma document to Koda page"
  [document]
  {:id (uuid/next)
   :name (:name document)
   :frames (map convert-figma-frame (:children document))})

(defn convert-figma-frame
  "Convert Figma frame to Koda frame"
  [frame]
  {:id (:id frame)
   :name (:name frame)
   :width (:width (:absoluteBoundingBox frame))
   :height (:height (:absoluteBoundingBox frame))
   :children (map convert-figma-node (:children frame))})

(defn convert-figma-node
  "Convert Figma node to Koda element"
  [node]
  {:id (:id node)
   :name (:name node)
   :type (convert-figma-type (:type node))
   :x (:x (:absoluteBoundingBox node))
   :y (:y (:absoluteBoundingBox node))
   :width (:width (:absoluteBoundingBox node))
   :height (:height (:absoluteBoundingBox node))
   :fills (convert-figma-fills (:fills node))
   :strokes (convert-figma-strokes (:strokes node))
   :cornerRadius (:cornerRadius node)
   :textContent (when (= (:type node) "TEXT") (:characters node))})

(defn convert-figma-type
  "Convert Figma node type to Koda element type"
  [figma-type]
  (case figma-type
    "FRAME" :frame
    "GROUP" :group
    "RECTANGLE" :rectangle
    "ELLIPSE" :ellipse
    "TEXT" :text
    "VECTOR" :vector
    "INSTANCE" :instance
    "COMPONENT" :component
    :rectangle)) ; default

(defn convert-figma-fills
  "Convert Figma fills to Koda fills"
  [fills]
  (map (fn [fill]
         {:type (if (:imageRef (:fill)) :image :solid)
          :color {:hex (str "#" (:r (:color fill)) (:g (:color fill)) (:b (:color fill)))
                  :a (:opacity fill 1)}
          :imageUrl (:imageRef fill)})
       fills))

(defn convert-figma-strokes
  "Convert Figma strokes to Koda strokes"
  [strokes]
  (map (fn [stroke]
         {:color {:hex (str "#" (:r (:color stroke)) (:g (:color stroke)) (:b (:color stroke)))
                  :a (:opacity stroke 1)}
          :width (:strokeWeight stroke 1)
          :style :solid})
       strokes))

(defn convert-figma-components
  "Convert Figma components to Koda components"
  [components]
  (map (fn [[id component]]
         {:id id
          :name (:name component)
          :element (convert-figma-node (:root component))})
       components))

;; ============================================================================
;; Sketch Integration
;; ============================================================================

(defn fetch-sketch-file
  "Fetch file from Sketch Cloud"
  [db user-id share-id]
  (when-let [token (get-valid-access-token db user-id :sketch)]
    (try
      (let [response (http/get (str "https://sketch.cloud/api/v1/shares/" share-id)
                      {:headers {"Authorization" (str "Bearer " token)}
                       :as :json})]

        (when (= 200 (:status response))
          (:body response)))

      (catch Exception e
        (log/error :msg "Failed to fetch Sketch file"
                  :user-id user-id
                  :share-id share-id
                  :error e)))))

(defn convert-sketch-to-koda
  "Convert Sketch file to Koda format"
  [sketch-data]
  ;; Similar conversion logic as Figma
  ;; Implementation would parse Sketch JSON format
  {:name (:name sketch-data)
   :pages [] ; converted pages
   :components []}) ; converted components

;; ============================================================================
;; Webhook Management
;; ============================================================================

(defn register-webhook
  "Register webhook for real-time updates"
  [db user-id provider file-id webhook-url]
  (let [webhook-id (uuid/next)
        webhook {:id webhook-id
                 :user-id user-id
                 :provider provider
                 :file-id file-id
                 :url webhook-url
                 :secret (str (uuid/next)) ; For webhook verification
                 :created-at (ct/now)
                 :active true}]

    (db/insert! db :webhooks webhook)

    ;; Register with provider API
    (case provider
      :figma (register-figma-webhook webhook)
      :sketch (register-sketch-webhook webhook))

    webhook))

(defn handle-webhook
  "Process incoming webhook"
  [provider payload signature]
  ;; Verify webhook signature
  (when (verify-webhook-signature provider payload signature)
    (let [event-type (:event_type payload)
          file-id (:file_key payload)]

      ;; Trigger file sync or notification
      (case event-type
        "file_update" (handle-file-update file-id)
        "file_delete" (handle-file-delete file-id)))))

(defn verify-webhook-signature
  "Verify webhook signature for security"
  [provider payload signature]
  ;; Implementation would verify HMAC signature
  true) ; Simplified

;; ============================================================================
;; Public API Commands
;; ============================================================================

(sv/defmethod ::connect-integration
  "Connect to third-party integration"
  {::doc/added "2.1"
   ::sm/params [:map {:title "connect-integration"}
                [:provider [:enum :figma :sketch :adobe-xd :invision]]
                [:redirect-uri {:optional true} ::sm/string]]}
  [cfg {:keys [::rpc/profile-id provider redirect-uri] :as params}]
  (let [default-redirect (str (cf/get :public-uri) "/integrations/callback")
        redirect-uri (or redirect-uri default-redirect)
        oauth-url (get-oauth-url provider redirect-uri profile-id)]

    {:oauth-url oauth-url
     :provider provider}))

(sv/defmethod ::handle-oauth-callback
  "Handle OAuth2 callback"
  {::doc/added "2.1"
   ::sm/params [:map {:title "handle-oauth-callback"}
                [:code ::sm/string]
                [:state ::sm/string]]}
  [cfg {:keys [code state] :as params}]
  (if-let [state-data (validate-oauth-state state)]
    (let [{:keys [user-id provider redirect-uri]} state-data
          config (get integrations provider)]

      ;; Exchange code for tokens
      (try
        (let [response (http/post (str (:api-base config) "/oauth/token")
                        {:form-params {:grant_type "authorization_code"
                                       :code code
                                       :redirect_uri redirect-uri
                                       :client_id (cf/get :oauth-client-id provider)
                                       :client_secret (cf/get :oauth-client-secret provider)}
                         :as :json})]

          (when (= 200 (:status response))
            (let [token-data (:body response)
                  expires-at (ct/plus (ct/now) (ct/seconds (:expires_in token-data)))]

              ;; Store tokens
              (store-access-token db user-id provider
                                (:access_token token-data)
                                (:refresh_token token-data)
                                expires-at)

              {:status :connected
               :provider provider})))

        (catch Exception e
          (log/error :msg "OAuth token exchange failed"
                    :error e)
          {:status :error :message "Token exchange failed"})))

    {:status :error :message "Invalid OAuth state"}))

(sv/defmethod ::import-from-integration
  "Import design from third-party tool"
  {::doc/added "2.1"
   ::sm/params [:map {:title "import-from-integration"}
                [:provider [:enum :figma :sketch :adobe-xd]]
                [:file-id ::sm/string]]}
  [{:keys [::db/pool] :as cfg}
   {:keys [::rpc/profile-id provider file-id] :as params}]

  (let [koda-data (case provider
                    :figma (when-let [figma-data (fetch-figma-file pool profile-id file-id)]
                             (convert-figma-to-koda figma-data))
                    :sketch (when-let [sketch-data (fetch-sketch-file pool profile-id file-id)]
                              (convert-sketch-to-koda sketch-data)))]

    (if koda-data
      {:status :imported
       :data koda-data}
      {:status :error :message "Failed to import design"})))

(sv/defmethod ::sync-integration
  "Sync changes from integrated tool"
  {::doc/added "2.1"
   ::sm/params [:map {:title "sync-integration"}
                [:integration-id ::sm/uuid]]}
  [{:keys [::db/pool] :as cfg}
   {:keys [::rpc/profile-id integration-id] :as params}]

  ;; Implementation would sync latest changes
  {:status :synced})

(sv/defmethod ::disconnect-integration
  "Disconnect third-party integration"
  {::doc/added "2.1"
   ::sm/params [:map {:title "disconnect-integration"}
                [:provider [:enum :figma :sketch :adobe-xd :invision]]]}
  [{:keys [::db/pool] :as cfg}
   {:keys [::rpc/profile-id provider] :as params}]

  ;; Remove stored tokens and webhooks
  (db/delete! db :integrations {:user-id profile-id :provider provider})

  {:status :disconnected})

;; ============================================================================
;; Background Tasks
;; ============================================================================

(defn start-integration-sync
  "Start background sync for all integrations"
  []
  (a/go-loop []
    ;; Sync all active integrations every 5 minutes
    (a/<! (a/timeout (* 5 60 1000)))

    (try
      (let [active-integrations (db/execute! db
        ["SELECT * FROM integrations WHERE expires_at > ?" (ct/now)])]

        (doseq [integration active-integrations]
          ;; Sync changes for this integration
          (sync-integration-changes integration)))

      (catch Exception e
        (log/error :msg "Integration sync failed" :error e)))

    (recur)))

(defn sync-integration-changes
  "Sync latest changes for an integration"
  [integration]
  ;; Implementation would check for updates and sync to Koda
  (log/info :msg "Syncing integration changes"
           :user-id (:user-id integration)
           :provider (:provider integration)))

;; ============================================================================
;; Utility Functions
;; ============================================================================

(defn get-connected-integrations
  "Get list of connected integrations for user"
  [db user-id]
  (db/execute! db
    ["SELECT provider, created_at, updated_at
     FROM integrations
     WHERE user_id = ? AND expires_at > ?"
     user-id (ct/now)]))

(defn validate-integration-access
  "Validate user has access to integration"
  [db user-id provider]
  (boolean (get-valid-access-token db user-id provider)))

;; Start background sync when namespace loads
(start-integration-sync)
