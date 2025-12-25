;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) KODA INC

(ns app.rpc.commands.collaboration
  "Real-time collaboration for enterprise teams"
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
   [clojure.core.async :as a]
   [datoteka.fs :as fs])
  (:import
   [java.util.concurrent ConcurrentHashMap]))

(set! *warn-on-reflection* true)

;; ============================================================================
;; Collaboration State Management
;; ============================================================================

(defonce collaboration-state
  "Global state for active collaborations"
  (atom {}))

(defonce active-sessions
  "Active WebSocket sessions by file-id"
  (ConcurrentHashMap.))

(defonce change-history
  "CRDT-based change history for conflict resolution"
  (ConcurrentHashMap.))

;; ============================================================================
;; Schema Definitions
;; ============================================================================

(def ^:private schema:join-collaboration
  [:map {:title "join-collaboration"}
   [:file-id ::sm/uuid]
   [:session-id {:optional true} ::sm/string]])

(def ^:private schema:leave-collaboration
  [:map {:title "leave-collaboration"}
   [:session-id ::sm/string]])

(def ^:private schema:send-collaboration-event
  [:map {:title "send-collaboration-event"}
   [:session-id ::sm/string]
   [:event-type [:enum :cursor :selection :edit :comment :ping]]
   [:event-data ::sm/any]])

(def ^:private schema:create-design-review
  [:map {:title "create-design-review"}
   [:file-id ::sm/uuid]
   [:title ::sm/string]
   [:description {:optional true} ::sm/string]
   [:reviewers [:vector ::sm/uuid]]
   [:due-date {:optional true} ::sm/inst]])

(def ^:private schema:add-review-comment
  [:map {:title "add-review-comment"}
   [:review-id ::sm/uuid]
   [:element-id {:optional true} ::sm/string]
   [:comment ::sm/string]
   [:x {:optional true} ::sm/number]
   [:y {:optional true} ::sm/number]])

;; ============================================================================
;; CRDT Change Management
;; ============================================================================

(defn- create-crdt-operation
  "Create a CRDT operation for conflict-free replication"
  [operation-type data]
  {:id (uuid/next)
   :type operation-type
   :data data
   :timestamp (ct/now)
   :vector-clock {} ; Simplified - would use proper vector clock
   :causal-dependencies []})

(defn- apply-crdt-operation
  "Apply CRDT operation with conflict resolution"
  [file-state operation]
  (case (:type operation)
    :element-create
    (update file-state :objects assoc (:element-id (:data operation)) (:data operation))

    :element-update
    (update-in file-state [:objects (:element-id (:data operation))]
               merge (:changes (:data operation)))

    :element-delete
    (update file-state :objects dissoc (:element-id (:data operation)))

    :element-move
    (let [{:keys [element-id x y]} (:data operation)]
      (update-in file-state [:objects element-id] assoc :x x :y y))

    ;; Default: no change
    file-state))

(defn- resolve-conflicts
  "Resolve conflicts using CRDT rules (last-writer-wins, etc.)"
  [operations]
  ;; Simplified conflict resolution
  ;; In production, would use proper CRDT algorithms
  (sort-by :timestamp operations))

;; ============================================================================
;; Real-Time Event Broadcasting
;; ============================================================================

(defn- broadcast-to-session
  "Broadcast event to all participants in a session"
  [file-id event exclude-session]
  (when-let [sessions (.get active-sessions file-id)]
    (doseq [[session-id ws-channel] sessions]
      (when (not= session-id exclude-session)
        (try
          ;; Send event through WebSocket
          (a/go
            (a/>! ws-channel {:type :collaboration-event
                              :event event
                              :timestamp (ct/now)}))
          (catch Exception e
            (log/error :msg "Failed to broadcast to session"
                      :session-id session-id
                      :error e)))))))

(defn- broadcast-user-presence
  "Broadcast user presence updates"
  [file-id user action]
  (broadcast-to-session file-id
                       {:type :user-presence
                        :user user
                        :action action
                        :timestamp (ct/now)}
                       nil))

(defn- broadcast-cursor-update
  "Broadcast cursor position updates"
  [file-id session-id cursor-data]
  (broadcast-to-session file-id
                       {:type :cursor-update
                        :session-id session-id
                        :cursor cursor-data
                        :timestamp (ct/now)}
                       session-id))

(defn- broadcast-selection-update
  "Broadcast selection updates"
  [file-id session-id selection-data]
  (broadcast-to-session file-id
                       {:type :selection-update
                        :session-id session-id
                        :selection selection-data
                        :timestamp (ct/now)}
                       session-id))

;; ============================================================================
;; Collaboration Session Management
;; ============================================================================

(defn- create-collaboration-session
  "Create a new collaboration session for a file"
  [file-id user]
  (let [session-id (str (uuid/next))
        session {:id session-id
                 :file-id file-id
                 :user user
                 :joined-at (ct/now)
                 :last-activity (ct/now)
                 :cursor-position nil
                 :selection nil}]
    session))

(defn- join-collaboration-session
  "Add user to collaboration session"
  [file-id user ws-channel]
  (let [session (create-collaboration-session file-id user)]
    ;; Add to active sessions
    (.putIfAbsent active-sessions file-id (ConcurrentHashMap.))
    (when-let [file-sessions (.get active-sessions file-id)]
      (.put file-sessions (:id session) ws-channel))

    ;; Broadcast presence
    (broadcast-user-presence file-id user :joined)

    session))

(defn- leave-collaboration-session
  "Remove user from collaboration session"
  [file-id session-id]
  (when-let [file-sessions (.get active-sessions file-id)]
    (when-let [removed-session (.remove file-sessions session-id)]
      ;; Check if file has no more sessions
      (when (.isEmpty file-sessions)
        (.remove active-sessions file-id))

      ;; Broadcast departure
      (broadcast-user-presence file-id {:session-id session-id} :left))))

;; ============================================================================
;; Design Review System
;; ============================================================================

(defn- create-design-review
  "Create a new design review"
  [db file-id creator title description reviewers due-date]
  (let [review-id (uuid/next)
        review {:id review-id
                :file-id file-id
                :creator-id (:id creator)
                :title title
                :description description
                :reviewers (map :id reviewers)
                :status :open
                :created-at (ct/now)
                :updated-at (ct/now)
                :due-date due-date
                :comments []}]

    ;; Store in database
    (db/insert! db :design-review review)

    ;; Notify reviewers
    (doseq [reviewer reviewers]
      ;; Send notification (would integrate with notification system)
      (log/info :msg "Design review created"
               :review-id review-id
               :reviewer-id (:id reviewer)))

    review))

(defn- add-review-comment
  "Add a comment to a design review"
  [db review-id user element-id comment x y]
  (let [comment-data {:id (uuid/next)
                      :review-id review-id
                      :user-id (:id user)
                      :element-id element-id
                      :comment comment
                      :x x
                      :y y
                      :created-at (ct/now)
                      :resolved false}]

    ;; Add to review
    (db/execute-one! db
      ["UPDATE design_review
        SET comments = comments || ?
        WHERE id = ?"
       (t/encode comment-data)
       review-id])

    ;; Broadcast to collaborators
    (when-let [review (db/get db :design-review {:id review-id})]
      (broadcast-to-session (:file-id review)
                           {:type :review-comment
                            :comment comment-data}
                           nil))

    comment-data))

;; ============================================================================
;; Public API Commands
;; ============================================================================

(sv/defmethod ::join-collaboration
  "Join a real-time collaboration session"
  {::doc/added "2.1"
   ::sm/params schema:join-collaboration}
  [{:keys [::db/pool] :as cfg}
   {:keys [::rpc/profile-id file-id session-id] :as params}]
  (files/check-read-permissions! pool profile-id file-id)

  (let [user (db/get pool :profile {:id profile-id})
        ;; In real implementation, ws-channel would come from WebSocket upgrade
        ws-channel (a/chan) ; Placeholder
        session (join-collaboration-session file-id user ws-channel)]

    {:session-id (:id session)
     :file-id file-id
     :active-users (count (.get active-sessions file-id))}))

(sv/defmethod ::leave-collaboration
  "Leave a collaboration session"
  {::doc/added "2.1"
   ::sm/params schema:leave-collaboration}
  [cfg {:keys [session-id] :as params}]
  ;; Find file-id from session
  (doseq [[file-id sessions] active-sessions]
    (when (.containsKey sessions session-id)
      (leave-collaboration-session file-id session-id)
      (return {:status :left})))

  {:status :not-found})

(sv/defmethod ::send-collaboration-event
  "Send a collaboration event (cursor, selection, etc.)"
  {::doc/added "2.1"
   ::sm/params schema:send-collaboration-event}
  [{:keys [::db/pool] :as cfg}
   {:keys [::rpc/profile-id session-id event-type event-data] :as params}]

  ;; Find file-id from session
  (let [file-id (some #(when (.containsKey (.get active-sessions %) session-id) %)
                      (keys active-sessions))]

    (when file-id
      (files/check-read-permissions! pool profile-id file-id)

      ;; Handle different event types
      (case event-type
        :cursor (broadcast-cursor-update file-id session-id event-data)
        :selection (broadcast-selection-update file-id session-id event-data)
        :edit (handle-edit-event file-id session-id event-data)
        :comment (handle-comment-event file-id session-id event-data)
        :ping (handle-ping-event file-id session-id))

      {:status :broadcasted})))

(sv/defmethod ::create-design-review
  "Create a new design review"
  {::doc/added "2.1"
   ::sm/params schema:create-design-review}
  [{:keys [::db/pool] :as cfg}
   {:keys [::rpc/profile-id file-id title description reviewers due-date] :as params}]
  (files/check-read-permissions! pool profile-id file-id)

  (let [creator (db/get pool :profile {:id profile-id})
        reviewer-profiles (map #(db/get pool :profile {:id %}) reviewers)
        review (create-design-review pool file-id creator title description reviewer-profiles due-date)]

    {:review review}))

(sv/defmethod ::add-review-comment
  "Add a comment to a design review"
  {::doc/added "2.1"
   ::sm/params schema:add-review-comment}
  [{:keys [::db/pool] :as cfg}
   {:keys [::rpc/profile-id review-id element-id comment x y] :as params}]

  ;; Verify user can comment on this review
  (let [review (db/get pool :design-review {:id review-id})
        user (db/get pool :profile {:id profile-id})]

    (when (or (= (:creator-id review) profile-id)
              (some #(= % profile-id) (:reviewers review)))
      (let [comment-data (add-review-comment pool review-id user element-id comment x y)]
        {:comment comment-data})
      (ex/raise :type :not-authorized
                :code :cannot-comment-on-review))))

;; ============================================================================
;; Event Handlers
;; ============================================================================

(defn- handle-edit-event
  "Handle design edit events with CRDT"
  [file-id session-id event-data]
  (let [operation (create-crdt-operation :element-update event-data)]
    ;; Store operation for conflict resolution
    (.putIfAbsent change-history file-id (ConcurrentHashMap.))
    (when-let [file-history (.get change-history file-id)]
      (.put file-history (:id operation) operation))

    ;; Broadcast to other collaborators
    (broadcast-to-session file-id
                         {:type :edit-operation
                          :operation operation}
                         session-id)))

(defn- handle-comment-event
  "Handle comment events"
  [file-id session-id event-data]
  (broadcast-to-session file-id
                       {:type :comment
                        :session-id session-id
                        :data event-data}
                       session-id))

(defn- handle-ping-event
  "Handle ping events for presence"
  [file-id session-id]
  ;; Update last activity
  (when-let [file-sessions (.get active-sessions file-id)]
    (when-let [session (.get file-sessions session-id)]
      ;; Update last activity timestamp
      )))

;; ============================================================================
;; Utility Functions
;; ============================================================================

(defn get-active-collaborators
  "Get list of active collaborators for a file"
  [file-id]
  (when-let [sessions (.get active-sessions file-id)]
    (mapv (fn [[session-id _]]
            {:session-id session-id
             :last-activity (ct/now)}) ; Would track actual activity
          sessions)))

(defn get-collaboration-stats
  "Get collaboration statistics"
  []
  {:active-files (count active-sessions)
   :total-sessions (reduce + (map count (vals active-sessions)))
   :active-change-history (count change-history)})

(defn cleanup-inactive-sessions
  "Clean up inactive collaboration sessions"
  []
  (let [timeout-threshold (ct/minus (ct/now) (ct/duration {:minutes 30}))]
    (doseq [[file-id sessions] active-sessions]
      (doseq [[session-id _] sessions]
        ;; Check if session is inactive and remove if needed
        ;; Implementation would check last activity timestamp
        ))))
