;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) KODA INC

(ns app.main.data.collaboration
  "Real-time collaboration data management"
  (:require
   [app.common.data :as d]
   [app.common.data.macros :as dm]
   [app.common.schema :as sm]
   [app.main.data.event :as ev]
   [app.main.data.modal :as modal]
   [app.main.repo :as rp]
   [app.main.store :as st]
   [app.util.sse :as sse]
   [beicon.v2.core :as rx]
   [potok.v2.core :as ptk]))

;; ============================================================================
;; Collaboration State
;; ============================================================================

(def initial-state
  {:session nil
   :active-users []
   :cursors {}
   :selections {}
   :pending-changes []
   :connection-status :disconnected
   :design-reviews []
   :comments []})

;; ============================================================================
;; Events
;; ============================================================================

;; Collaboration session events
(defn join-collaboration
  [file-id]
  (ptk/reify ::join-collaboration
    ptk/WatchEvent
    (watch [_ state _]
      (let [params {:file-id file-id}]
        (rx/merge
         (rx/of (ev/event {::ev/name "collaboration-join-started"
                          :file-id file-id}))
         (->> (rp/cmd! :join-collaboration params)
              (rx/map (fn [response]
                        (join-collaboration-success response)))
              (rx/catch (fn [error]
                          (join-collaboration-error error))))))))

(defn join-collaboration-success
  [{:keys [session-id file-id active-users] :as response}]
  (ptk/reify ::join-collaboration-success
    ptk/UpdateEvent
    (update [_ state]
      (assoc-in state [:collaboration]
                (merge initial-state
                       {:session {:id session-id :file-id file-id}
                        :active-users active-users
                        :connection-status :connected})))))

(defn join-collaboration-error
  [error]
  (ptk/reify ::join-collaboration-error
    ptk/UpdateEvent
    (update [_ state]
      (assoc-in state [:collaboration :connection-status] :error))))

(defn leave-collaboration
  []
  (ptk/reify ::leave-collaboration
    ptk/WatchEvent
    (watch [_ state _]
      (let [session-id (get-in state [:collaboration :session :id])]
        (when session-id
          (->> (rp/cmd! :leave-collaboration {:session-id session-id})
               (rx/map (fn [_] (leave-collaboration-success)))
               (rx/catch (fn [error] (leave-collaboration-error error)))))))

    ptk/UpdateEvent
    (update [_ state]
      (assoc-in state [:collaboration :connection-status] :disconnecting))))

(defn leave-collaboration-success
  []
  (ptk/reify ::leave-collaboration-success
    ptk/UpdateEvent
    (update [_ state]
      (assoc state :collaboration initial-state))))

(defn leave-collaboration-error
  [error]
  (ptk/reify ::leave-collaboration-error
    ptk/UpdateEvent
    (update [_ state]
      (assoc-in state [:collaboration :connection-status] :error))))

;; ============================================================================
;; Real-time Events
;; ============================================================================

(defn send-cursor-update
  [x y]
  (ptk/reify ::send-cursor-update
    ptk/WatchEvent
    (watch [_ state _]
      (let [session-id (get-in state [:collaboration :session :id])]
        (when (and session-id (= (get-in state [:collaboration :connection-status]) :connected))
          (rp/cmd! :send-collaboration-event
                   {:session-id session-id
                    :event-type :cursor
                    :event-data {:x x :y y}}))))))

(defn send-selection-update
  [selection]
  (ptk/reify ::send-selection-update
    ptk/WatchEvent
    (watch [_ state _]
      (let [session-id (get-in state [:collaboration :session :id])]
        (when (and session-id (= (get-in state [:collaboration :connection-status]) :connected))
          (rp/cmd! :send-collaboration-event
                   {:session-id session-id
                    :event-type :selection
                    :event-data selection}))))))

(defn send-edit-update
  [element-id changes]
  (ptk/reify ::send-edit-update
    ptk/WatchEvent
    (watch [_ state _]
      (let [session-id (get-in state [:collaboration :session :id])]
        (when (and session-id (= (get-in state [:collaboration :connection-status]) :connected))
          (rp/cmd! :send-collaboration-event
                   {:session-id session-id
                    :event-type :edit
                    :event-data {:element-id element-id
                                :changes changes}}))))))

;; ============================================================================
;; Design Review Events
;; ============================================================================

(defn create-design-review
  [file-id title description reviewers due-date]
  (ptk/reify ::create-design-review
    ptk/WatchEvent
    (watch [_ state _]
      (let [params {:file-id file-id
                    :title title
                    :description description
                    :reviewers reviewers
                    :due-date due-date}]
        (->> (rp/cmd! :create-design-review params)
             (rx/map (fn [response]
                       (create-design-review-success response)))
             (rx/catch (fn [error]
                         (create-design-review-error error)))))))

(defn create-design-review-success
  [review]
  (ptk/reify ::create-design-review-success
    ptk/UpdateEvent
    (update [_ state]
      (update-in state [:collaboration :design-reviews] (fnil conj []) review))))

(defn create-design-review-error
  [error]
  (ptk/reify ::create-design-review-error
    ev/Event
    (-data [_] {:error error})))

(defn add-review-comment
  [review-id element-id comment x y]
  (ptk/reify ::add-review-comment
    ptk/WatchEvent
    (watch [_ state _]
      (let [params {:review-id review-id
                    :element-id element-id
                    :comment comment
                    :x x
                    :y y}]
        (->> (rp/cmd! :add-review-comment params)
             (rx/map (fn [response]
                       (add-review-comment-success response)))
             (rx/catch (fn [error]
                         (add-review-comment-error error)))))))

(defn add-review-comment-success
  [comment]
  (ptk/reify ::add-review-comment-success
    ptk/UpdateEvent
    (update [_ state]
      (update-in state [:collaboration :comments] (fnil conj []) comment))))

(defn add-review-comment-error
  [error]
  (ptk/reify ::add-review-comment-error
    ev/Event
    (-data [_] {:error error})))

;; ============================================================================
;; Real-time Event Handlers
;; ============================================================================

(defn handle-collaboration-event
  [event]
  (case (:type event)
    :user-presence (handle-user-presence event)
    :cursor-update (handle-cursor-update event)
    :selection-update (handle-selection-update event)
    :edit-operation (handle-edit-operation event)
    :review-comment (handle-review-comment event)
    identity))

(defn handle-user-presence
  [{:keys [user action]}]
  (ptk/reify ::handle-user-presence
    ptk/UpdateEvent
    (update [_ state]
      (case action
        :joined (update-in state [:collaboration :active-users] (fnil conj []) user)
        :left (update-in state [:collaboration :active-users]
                        (fn [users] (remove #(= (:id %) (:id user)) users)))
        state))))

(defn handle-cursor-update
  [{:keys [session-id cursor]}]
  (ptk/reify ::handle-cursor-update
    ptk/UpdateEvent
    (update [_ state]
      (assoc-in state [:collaboration :cursors session-id] cursor))))

(defn handle-selection-update
  [{:keys [session-id selection]}]
  (ptk/reify ::handle-selection-update
    ptk/UpdateEvent
    (update [_ state]
      (assoc-in state [:collaboration :selections session-id] selection))))

(defn handle-edit-operation
  [{:keys [operation]}]
  (ptk/reify ::handle-edit-operation
    ptk/UpdateEvent
    (update [_ state]
      ;; Apply the edit operation to the local state
      ;; This would integrate with the existing workspace data management
      (update-in state [:collaboration :pending-changes] (fnil conj []) operation))))

(defn handle-review-comment
  [{:keys [comment]}]
  (ptk/reify ::handle-review-comment
    ptk/UpdateEvent
    (update [_ state]
      (update-in state [:collaboration :comments] (fnil conj []) comment))))

;; ============================================================================
;; WebSocket Connection Management
;; ============================================================================

(defn initialize-websocket-connection
  "Initialize WebSocket connection for real-time collaboration"
  [file-id]
  (ptk/reify ::initialize-websocket
    ptk/EffectEvent
    (effect [_ state _]
      ;; Initialize WebSocket connection
      ;; This would connect to the collaboration WebSocket endpoint
      (let [ws-url (str "wss://" (.-host js/location) "/ws/collaboration/" file-id)]
        (try
          (let [ws (js/WebSocket. ws-url)]
            (set! (.-onopen ws) (fn [_]
                                  (st/emit! (join-collaboration file-id))))
            (set! (.-onmessage ws) (fn [event]
                                     (let [data (js/JSON.parse (.-data event))]
                                       (st/emit! (handle-collaboration-event data)))))
            (set! (.-onclose ws) (fn [_]
                                   (st/emit! (leave-collaboration))))
            (set! (.-onerror ws) (fn [error]
                                   (js/console.error "WebSocket error:" error))))
          (catch js/Error e
            (js/console.error "Failed to initialize WebSocket:" e)))))))

;; ============================================================================
;; Utility Functions
;; ============================================================================

(defn get-collaboration-state
  "Get current collaboration state from app state"
  [state]
  (get state :collaboration initial-state))

(defn is-collaborating?
  "Check if user is currently in a collaboration session"
  [state]
  (= (get-in state [:collaboration :connection-status]) :connected))

(defn get-active-users
  "Get list of active collaborators"
  [state]
  (get-in state [:collaboration :active-users] []))

(defn get-user-cursors
  "Get cursor positions of all collaborators"
  [state]
  (get-in state [:collaboration :cursors] {}))

(defn get-user-selections
  "Get selections of all collaborators"
  [state]
  (get-in state [:collaboration :selections] {}))

(defn get-design-reviews
  "Get design reviews for current file"
  [state]
  (get-in state [:collaboration :design-reviews] []))

(defn get-review-comments
  "Get comments for design reviews"
  [state]
  (get-in state [:collaboration :comments] []))
