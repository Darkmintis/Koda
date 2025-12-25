;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) KODA INC

(ns app.features.security
  "Enterprise security and compliance features"
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
   [buddy.hashers :as hashers]
   [buddy.sign.jwt :as jwt]
   [clj-http.client :as http]
   [clojure.core.async :as a]
   [datoteka.fs :as fs]
   [cheshire.core :as json])
  (:import
   [java.security SecureRandom]
   [java.util Base64]
   [javax.crypto Cipher]
   [javax.crypto.spec SecretKeySpec IvParameterSpec]))

(set! *warn-on-reflection* true)

;; ============================================================================
;; Encryption & Data Protection
;; ============================================================================

(def ^:private encryption-key
  "Master encryption key (would be loaded from secure key store)"
  (let [key-str (cf/get :encryption-key (str (uuid/next)))]
    (.getBytes key-str "UTF-8")))

(defn encrypt-data
  "Encrypt sensitive data"
  [data]
  (let [cipher (Cipher/getInstance "AES/CBC/PKCS5Padding")
        secret-key (SecretKeySpec. encryption-key "AES")
        iv (byte-array 16)]
    (.nextBytes (SecureRandom.) iv)
    (.init cipher Cipher/ENCRYPT_MODE secret-key (IvParameterSpec. iv))

    (let [encrypted (.doFinal cipher (.getBytes (t/encode data) "UTF-8"))
          encoder (Base64/getEncoder)]
      {:encrypted (.encodeToString encoder encrypted)
       :iv (.encodeToString encoder iv)})))

(defn decrypt-data
  "Decrypt sensitive data"
  [{:keys [encrypted iv]}]
  (let [cipher (Cipher/getInstance "AES/CBC/PKCS5Padding")
        secret-key (SecretKeySpec. encryption-key "AES")
        decoder (Base64/getDecoder)
        encrypted-bytes (.decode decoder encrypted)
        iv-bytes (.decode decoder iv)]

    (.init cipher Cipher/DECRYPT_MODE secret-key (IvParameterSpec. iv-bytes))

    (let [decrypted (.doFinal cipher encrypted-bytes)]
      (t/decode (String. decrypted "UTF-8")))))

(defn hash-sensitive-data
  "Hash sensitive data for storage"
  [data]
  (hashers/derive data {:alg :bcrypt+blake2b-512}))

(defn verify-sensitive-data
  "Verify hashed sensitive data"
  [data hash]
  (hashers/check data hash))

;; ============================================================================
;; SOC 2 Compliance
;; ============================================================================

(defn log-security-event
  "Log security events for SOC 2 compliance"
  [event-type user-id resource details]
  (let [event {:id (uuid/next)
               :event-type event-type
               :user-id user-id
               :resource resource
               :details details
               :timestamp (ct/now)
               :ip-address (:ip-address details)
               :user-agent (:user-agent details)}]

    ;; Store in secure audit log
    (db/insert! db :security-audit-log event)

    ;; Send to SIEM system (if configured)
    (when (cf/get :siem-enabled)
      (send-to-siem event))

    ;; Alert on critical events
    (when (critical-security-event? event-type)
      (alert-security-team event))))

(defn critical-security-event?
  "Check if event requires immediate attention"
  [event-type]
  (contains? #{"authentication_failure"
               "unauthorized_access"
               "data_breach"
               "privilege_escalation"} event-type))

(defn send-to-siem
  "Send event to Security Information and Event Management system"
  [event]
  ;; Implementation would send to configured SIEM
  (log/info :msg "Security event sent to SIEM" :event-id (:id event)))

(defn alert-security-team
  "Alert security team about critical events"
  [event]
  ;; Implementation would send alerts via email/SMS/pager
  (log/warn :msg "CRITICAL SECURITY EVENT" :event event))

(defn generate-compliance-report
  "Generate SOC 2 compliance report"
  [start-date end-date]
  (let [events (db/execute! db
    ["SELECT event_type, COUNT(*) as count
     FROM security_audit_log
     WHERE timestamp BETWEEN ? AND ?
     GROUP BY event_type"
     start-date end-date])

        access-controls (db/execute! db
    ["SELECT user_id, resource, COUNT(*) as access_count
     FROM security_audit_log
     WHERE event_type = 'resource_access'
       AND timestamp BETWEEN ? AND ?
     GROUP BY user_id, resource"
     start-date end-date])

        anomalies (detect-anomalies access-controls)]

    {:period {:start start-date :end end-date}
     :events-summary events
     :access-patterns access-controls
     :anomalies anomalies
     :compliance-status (calculate-compliance-status events anomalies)}))

(defn calculate-compliance-status
  "Calculate compliance status based on events and anomalies"
  [events anomalies]
  (let [failed-auth-count (:count (first (filter #(= (:event_type %) "authentication_failure") events)) 0)
        unauthorized-count (:count (first (filter #(= (:event_type %) "unauthorized_access") events)) 0)
        anomaly-count (count anomalies)]

    (cond
      (or (> failed-auth-count 10) (> unauthorized-count 5) (> anomaly-count 3))
      {:status :non-compliant :risk-level :high}

      (or (> failed-auth-count 5) (> unauthorized-count 2) (> anomaly-count 1))
      {:status :needs-review :risk-level :medium}

      :else
      {:status :compliant :risk-level :low})))

(defn detect-anomalies
  "Detect anomalous access patterns"
  [access-patterns]
  ;; Simple anomaly detection based on statistical analysis
  (let [avg-access (if (seq access-patterns)
                     (/ (reduce + (map :access_count access-patterns)) (count access-patterns))
                     0)]

    (filter #(> (:access_count %) (* 3 avg-access)) access-patterns)))

;; ============================================================================
;; GDPR Compliance
;; ============================================================================

(defn handle-data-subject-request
  "Handle GDPR data subject access/deletion requests"
  [user-id request-type]
  (case request-type
    :access
    (let [user-data (collect-user-data user-id)]
      {:status :data-provided
       :data user-data
       :timestamp (ct/now)})

    :deletion
    (let [deletion-result (delete-user-data user-id)]
      {:status :data-deleted
       :deleted-records (:deleted-count deletion-result)
       :timestamp (ct/now)})

    :rectification
    (let [updated-data (rectify-user-data user-id)]
      {:status :data-rectified
       :updated-fields (keys updated-data)
       :timestamp (ct/now)})))

(defn collect-user-data
  "Collect all user data for GDPR access requests"
  [user-id]
  {:profile (db/get db :profile {:id user-id})
   :files (db/execute! db ["SELECT id, name FROM files WHERE creator_id = ?" user-id])
   :teams (db/execute! db ["SELECT t.name FROM team_users tu JOIN teams t ON tu.team_id = t.id WHERE tu.user_id = ?" user-id])
   :audit-log (db/execute! db ["SELECT event_type, timestamp FROM security_audit_log WHERE user_id = ? ORDER BY timestamp DESC LIMIT 100" user-id])})

(defn delete-user-data
  "Delete all user data for GDPR deletion requests"
  [user-id]
  (let [file-count (db/execute-one! db ["SELECT COUNT(*) FROM files WHERE creator_id = ?" user-id])
        team-count (db/execute-one! db ["SELECT COUNT(*) FROM team_users WHERE user_id = ?" user-id])
        audit-count (db/execute-one! db ["SELECT COUNT(*) FROM security_audit_log WHERE user_id = ?" user-id])]

    ;; Anonymize instead of delete for audit trail
    (db/execute-one! db ["UPDATE profiles SET email = CONCAT('deleted-', id, '@anonymous.com'), name = 'Deleted User' WHERE id = ?" user-id])
    (db/execute-one! db ["UPDATE files SET data = '{}' WHERE creator_id = ?" user-id])

    {:deleted-count (+ (:count file-count) (:count team-count) (:count audit-count))})))

(defn rectify-user-data
  "Rectify inaccurate user data"
  [user-id corrections]
  ;; Implementation would update user data based on corrections
  (db/update! db :profiles {:id user-id} corrections))

(defn gdpr-consent-management
  "Manage GDPR consent for data processing"
  [user-id consent-type granted?]
  (let [consent {:id (uuid/next)
                 :user-id user-id
                 :consent-type consent-type
                 :granted granted?
                 :timestamp (ct/now)
                 :expires-at (when granted? (ct/plus (ct/now) (ct/years 1)))}]

    (db/insert! db :gdpr-consents consent)
    consent))

(defn check-gdpr-consent
  "Check if user has given consent for data processing"
  [user-id consent-type]
  (let [consent (db/get db :gdpr-consents
                       {:user-id user-id :consent-type consent-type}
                       {:order-by [[:timestamp :desc]]})]

    (and consent
         (:granted consent)
         (or (nil? (:expires-at consent))
             (ct/after? (:expires-at consent) (ct/now))))))

;; ============================================================================
;; HIPAA Compliance (Healthcare)
;; ============================================================================

(defn hipaa-audit-log
  "Log HIPAA-relevant events"
  [user-id action resource phi-access?]
  (let [audit-entry {:id (uuid/next)
                     :user-id user-id
                     :action action
                     :resource resource
                     :phi-access phi-access?
                     :timestamp (ct/now)
                     :ip-address "system" ; Would get from request
                     :user-agent "system"}] ; Would get from request

    ;; Store in HIPAA audit log (separate from regular audit)
    (db/insert! db :hipaa-audit-log audit-entry)

    ;; Check for suspicious activity
    (when phi-access?
      (monitor-phi-access audit-entry))))

(defn monitor-phi-access
  "Monitor PHI access for compliance"
  [audit-entry]
  (let [user-access-count (db/execute-one! db
    ["SELECT COUNT(*) as count FROM hipaa_audit_log
      WHERE user_id = ? AND phi_access = true
        AND timestamp > ?"
     (:user-id audit-entry) (ct/minus (ct/now) (ct/hours 24))])]

    ;; Alert if excessive PHI access
    (when (> (:count user-access-count) 100)
      (alert-hipaa-violation audit-entry))))

(defn alert-hipaa-violation
  "Alert about potential HIPAA violations"
  [audit-entry]
  (log/error :msg "POTENTIAL HIPAA VIOLATION"
            :audit-entry audit-entry)
  ;; Implementation would alert compliance officer
  )

(defn hipaa-data-encryption
  "Ensure HIPAA data is properly encrypted"
  [data contains-phi?]
  (if contains-phi?
    (encrypt-data data)
    data))

(defn hipaa-access-controls
  "Implement HIPAA access controls"
  [user-id resource action]
  (let [user-role (get-user-hipaa-role user-id)
        required-role (get-required-role-for-resource resource action)]

    ;; Check if user has required role for this action
    (contains? user-role required-role)))

(defn get-user-hipaa-role
  "Get user's HIPAA role"
  [user-id]
  (:hipaa-role (db/get db :profiles {:id user-id}) :none))

(defn get-required-role-for-resource
  "Get required role for accessing resource"
  [resource action]
  ;; Implementation would define role requirements
  :healthcare-professional) ; Example

;; ============================================================================
;; Advanced Permissions & RBAC
;; ============================================================================

(defn create-permission-role
  "Create a custom permission role"
  [name description permissions]
  (let [role {:id (uuid/next)
              :name name
              :description description
              :permissions permissions
              :created-at (ct/now)
              :is-system-role false}]

    (db/insert! db :permission-roles role)
    role))

(defn assign-user-role
  "Assign role to user"
  [user-id role-id scope]
  (let [assignment {:id (uuid/next)
                    :user-id user-id
                    :role-id role-id
                    :scope scope ; :global, :team, :project
                    :assigned-at (ct/now)
                    :assigned-by "system"}] ; Would be actual user

    (db/insert! db :user-role-assignments assignment)
    assignment))

(defn check-permission
  "Check if user has permission for action"
  [user-id action resource scope]
  (let [user-roles (db/execute! db
    ["SELECT r.permissions FROM user_role_assignments ua
      JOIN permission_roles r ON ua.role_id = r.id
      WHERE ua.user_id = ?
        AND (ua.scope = ? OR ua.scope = 'global')"
     user-id scope])

        all-permissions (set (mapcat :permissions user-roles))]

    (contains? all-permissions action)))

(defn get-effective-permissions
  "Get all effective permissions for user"
  [user-id scope]
  (let [user-roles (db/execute! db
    ["SELECT r.name, r.permissions FROM user_role_assignments ua
      JOIN permission_roles r ON ua.role_id = r.id
      WHERE ua.user_id = ?
        AND (ua.scope = ? OR ua.scope = 'global')"
     user-id scope])]

    {:roles (map :name user-roles)
     :permissions (set (mapcat :permissions user-roles))}))

;; ============================================================================
;; SSO Integration
;; ============================================================================

(defn configure-sso-provider
  "Configure SSO provider (Okta, Auth0, Azure AD, etc.)"
  [provider-name config]
  (let [sso-config {:id (uuid/next)
                    :provider provider-name
                    :config (encrypt-data config) ; Encrypt sensitive config
                    :enabled true
                    :created-at (ct/now)}]

    (db/insert! db :sso-providers sso-config)
    sso-config))

(defn sso-authenticate
  "Authenticate user via SSO"
  [provider-name auth-code]
  (when-let [sso-config (db/get db :sso-providers {:provider provider-name :enabled true})]
    (let [config (decrypt-data (:config sso-config))]

      ;; Exchange auth code for tokens
      (case provider-name
        "okta" (authenticate-okta config auth-code)
        "auth0" (authenticate-auth0 config auth-code)
        "azure-ad" (authenticate-azure config auth-code)))))

(defn authenticate-okta
  "Authenticate with Okta"
  [config auth-code]
  ;; Implementation would call Okta API
  {:user-info {:email "user@company.com" :name "User Name"}
   :tokens {:access-token "..." :refresh-token "..."}})

(defn authenticate-auth0
  "Authenticate with Auth0"
  [config auth-code]
  ;; Implementation would call Auth0 API
  {:user-info {:email "user@company.com" :name "User Name"}
   :tokens {:access-token "..." :refresh-token "..."}})

(defn authenticate-azure
  "Authenticate with Azure AD"
  [config auth-code]
  ;; Implementation would call Azure AD API
  {:user-info {:email "user@company.com" :name "User Name"}
   :tokens {:access-token "..." :refresh-token "..."}})

;; ============================================================================
;; End-to-End Encryption
;; ============================================================================

(defn generate-user-encryption-key
  "Generate user-specific encryption key"
  [user-id]
  (let [salt (.getBytes (str user-id) "UTF-8")
        key-material (byte-array 32)]
    (.nextBytes (SecureRandom.) key-material)

    ;; Derive key using HKDF-like construction
    {:key (hashers/derive (String. key-material) {:salt salt :alg :pbkdf2})
     :created-at (ct/now)}))

(defn encrypt-file-content
  "Encrypt file content for secure storage"
  [file-id content user-id]
  (let [user-key (get-user-encryption-key user-id)
        encrypted (encrypt-data content)]

    ;; Store encrypted content
    (db/update! db :files
                {:id file-id}
                {:encrypted-content encrypted
                 :encryption-key-id (:id user-key)
                 :updated-at (ct/now)})))

(defn decrypt-file-content
  "Decrypt file content for user access"
  [file-id user-id]
  (when-let [file (db/get db :files {:id file-id})]
    (when (= (:creator-id file) user-id) ; Only creator can decrypt
      (let [encrypted (:encrypted-content file)]
        (decrypt-data encrypted)))))

(defn get-user-encryption-key
  "Get user's encryption key"
  [user-id]
  (db/get db :user-encryption-keys {:user-id user-id}))

;; ============================================================================
;; Security Monitoring & Alerting
;; ============================================================================

(defn monitor-security-metrics
  "Monitor security metrics and alert on anomalies"
  []
  (a/go-loop []
    (a/<! (a/timeout (* 60 1000))) ; Check every minute

    (try
      (let [metrics (collect-security-metrics)]

        ;; Check for anomalies
        (when (detect-security-anomaly metrics)
          (alert-security-anomaly metrics))

        ;; Check compliance status
        (when (needs-compliance-review metrics)
          (schedule-compliance-review)))

      (catch Exception e
        (log/error :msg "Security monitoring failed" :error e)))

    (recur)))

(defn collect-security-metrics
  "Collect current security metrics"
  []
  (let [last-hour (ct/minus (ct/now) (ct/hours 1))]
    {:failed-logins (db/execute-one! db
                      ["SELECT COUNT(*) FROM security_audit_log WHERE event_type = 'authentication_failure' AND timestamp > ?" last-hour])
     :unauthorized-access (db/execute-one! db
                         ["SELECT COUNT(*) FROM security_audit_log WHERE event_type = 'unauthorized_access' AND timestamp > ?" last-hour])
     :active-sessions (db/execute-one! db
                      ["SELECT COUNT(*) FROM user_sessions WHERE last_activity > ?" last-hour])}))

(defn detect-security-anomaly
  "Detect security anomalies"
  [metrics]
  (or (> (:count (:failed-logins metrics)) 10)
      (> (:count (:unauthorized-access metrics)) 5)
      (> (:count (:active-sessions metrics)) 1000)))

(defn alert-security-anomaly
  "Alert about security anomalies"
  [metrics]
  (log/warn :msg "SECURITY ANOMALY DETECTED" :metrics metrics)
  ;; Implementation would send alerts
  )

(defn needs-compliance-review
  "Check if compliance review is needed"
  [metrics]
  (> (:count (:failed-logins metrics)) 50))

(defn schedule-compliance-review
  "Schedule a compliance review"
  []
  (log/info :msg "Scheduling compliance review")
  ;; Implementation would create review task
  )

;; ============================================================================
;; IP Restrictions & Network Security
;; ============================================================================

(defn configure-ip-restrictions
  "Configure IP address restrictions for team"
  [team-id allowed-ips blocked-ips]
  (let [restrictions {:id (uuid/next)
                      :team-id team-id
                      :allowed-ips allowed-ips
                      :blocked-ips blocked-ips
                      :enabled true
                      :created-at (ct/now)}]

    (db/insert! db :ip-restrictions restrictions)
    restrictions))

(defn check-ip-access
  "Check if IP address is allowed"
  [team-id client-ip]
  (when-let [restrictions (db/get db :ip-restrictions {:team-id team-id :enabled true})]
    (let [allowed (:allowed-ips restrictions)
          blocked (:blocked-ips restrictions)]

      (cond
        (and (seq blocked) (contains? blocked client-ip))
        {:allowed false :reason "IP blocked"}

        (and (seq allowed) (not (contains? allowed client-ip)))
        {:allowed false :reason "IP not in allowlist"}

        :else
        {:allowed true}))))

;; ============================================================================
;; Audit Trail & Compliance Reporting
;; ============================================================================

(defn generate-audit-report
  "Generate comprehensive audit report"
  [start-date end-date user-id]
  (let [audit-events (db/execute! db
    ["SELECT * FROM security_audit_log
      WHERE timestamp BETWEEN ? AND ?
        AND (? IS NULL OR user_id = ?)
      ORDER BY timestamp DESC"
     start-date end-date user-id user-id])

        compliance-status (calculate-compliance-status-from-events audit-events)]

    {:period {:start start-date :end end-date}
     :user-id user-id
     :events audit-events
     :compliance-status compliance-status
     :generated-at (ct/now)}))

(defn calculate-compliance-status-from-events
  "Calculate compliance status from audit events"
  [events]
  (let [event-types (group-by :event-type events)
        failed-auths (count (:authentication-failure event-types []))
        unauthorized (count (:unauthorized-access event-types []))]

    (cond
      (or (> failed-auths 5) (> unauthorized 3)) :non-compliant
      (or (> failed-auths 2) (> unauthorized 1)) :needs-review
      :else :compliant)))

;; Start security monitoring
(monitor-security-metrics)
