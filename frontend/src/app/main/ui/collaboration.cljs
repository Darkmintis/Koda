;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) KODA INC

(ns app.main.ui.collaboration
  "Real-time collaboration UI components"
  (:require-macros [app.main.style :as stl])
  (:require
   [app.common.data :as d]
   [app.common.data.macros :as dm]
   [app.main.data.collaboration :as dc]
   [app.main.refs :as refs]
   [app.main.store :as st]
   [app.main.ui.ds.buttons.icon-button :refer [icon-button*]]
   [app.main.ui.ds.foundations.assets.icon :as i]
   [app.main.ui.hooks.resize :as r]
   [app.main.ui.icons :as icons]
   [app.util.dom :as dom]
   [app.util.i18n :as i18n :refer [tr]]
   [beicon.v2.core :as rx]
   [cuerdas.core :as str]
   [rumext.v2 :as mf]))

;; ============================================================================
;; User Presence Indicators
;; ============================================================================

(mf/defc user-presence-indicator*
  {::mf/props :obj
   ::mf/private true}
  [{:keys [users]}]
  (when (seq users)
    [:div {:class (stl/css :user-presence)}
     [:div {:class (stl/css :presence-header)}
      [:span {:class (stl/css :presence-icon)} "👥"]
      [:span {:class (stl/css :presence-count)} (count users)]]

     [:div {:class (stl/css :presence-list)}
      (for [user users]
        [:div {:key (:id user)
               :class (stl/css :presence-user)}
         [:div {:class (stl/css :user-avatar)}
          (or (:avatar user) "👤")]
         [:span {:class (stl/css :user-name)}
          (or (:name user) (:email user))]])]]))

;; ============================================================================
;; Collaborative Cursors
;; ============================================================================

(mf/defc collaborative-cursors*
  {::mf/props :obj
   ::mf/private true}
  [{:keys [cursors zoom]}]
  (when (seq cursors)
    [:div {:class (stl/css :collaborative-cursors)}
     (for [[session-id cursor] cursors]
       (when cursor
         (let [x (* (:x cursor) zoom)
               y (* (:y cursor) zoom)]
           [:div {:key session-id
                  :class (stl/css :cursor-indicator)
                  :style {:left (str x "px")
                          :top (str y "px")}}
            [:div {:class (stl/css :cursor-pointer)} "👆"]
            [:div {:class (stl/css :cursor-label)}
             (:user-name cursor)]])))]))

;; ============================================================================
;; Collaborative Selections
;; ============================================================================

(mf/defc collaborative-selections*
  {::mf/props :obj
   ::mf/private true}
  [{:keys [selections zoom]}]
  (when (seq selections)
    [:div {:class (stl/css :collaborative-selections)}
     (for [[session-id selection] selections]
       (when selection
         [:div {:key session-id
                :class (stl/css :selection-overlay)
                :style {:left (str (* (:x selection) zoom) "px")
                        :top (str (* (:y selection) zoom) "px")
                        :width (str (* (:width selection) zoom) "px")
                        :height (str (* (:height selection) zoom) "px")}}
          [:div {:class (stl/css :selection-border)}]]))]))

;; ============================================================================
;; Design Review Comments
;; ============================================================================

(mf/defc review-comments*
  {::mf/props :obj
   ::mf/private true}
  [{:keys [comments zoom on-add-comment]}]
  [:div {:class (stl/css :review-comments)}
   (for [comment comments]
     [:div {:key (:id comment)
            :class (stl/css :comment-pin)
            :style {:left (str (* (:x comment) zoom) "px")
                    :top (str (* (:y comment) zoom) "px")}}
      [:div {:class (stl/css :comment-bubble)}
       [:div {:class (stl/css :comment-header)}
        [:span {:class (stl/css :comment-author)}
         (:author comment)]
        [:span {:class (stl/css :comment-time)}
         (format-time (:created-at comment))]]
       [:div {:class (stl/css :comment-content)}
        (:comment comment)]
       (when (:replies comment)
         [:div {:class (stl/css :comment-replies)}
          (str (count (:replies comment)) " replies")])]])])

;; ============================================================================
;; Comment Input Component
;; ============================================================================

(mf/defc comment-input*
  {::mf/props :obj
   ::mf/private true}
  [{:keys [x y on-submit on-cancel]}]
  (let [comment-text* (mf/use-state "")
        submitting* (mf/use-state false)]

    [:div {:class (stl/css :comment-input-overlay)
           :style {:left (str x "px")
                   :top (str y "px")}}
     [:div {:class (stl/css :comment-input-card)}
      [:textarea {:class (stl/css :comment-textarea)
                  :placeholder "Add a comment..."
                  :value @comment-text*
                  :on-change #(reset! comment-text* (dom/get-target-val %))
                  :auto-focus true}]
      [:div {:class (stl/css :comment-actions)}
       [:button {:class (stl/css :comment-cancel)
                 :on-click on-cancel}
        "Cancel"]
       [:button {:class (stl/css :comment-submit)
                 :disabled (or (str/blank? @comment-text*) @submitting*)
                 :on-click (fn []
                             (when (and (not (str/blank? @comment-text*))
                                       (not @submitting*))
                               (reset! submitting* true)
                               (on-submit @comment-text*)))}
        (if @submitting* "Posting..." "Comment")]]]])))

;; ============================================================================
;; Design Review Panel
;; ============================================================================

(mf/defc design-review-panel*
  {::mf/props :obj
   ::mf/private true}
  [{:keys [reviews on-create-review]}]
  [:div {:class (stl/css :design-review-panel)}
   [:div {:class (stl/css :review-header)}
    [:h3 {:class (stl/css :review-title)} "Design Reviews"]
    [:button {:class (stl/css :create-review-btn)
              :on-click on-create-review}
     "+ New Review"]]

   [:div {:class (stl/css :review-list)}
    (if (seq reviews)
      (for [review reviews]
        [:div {:key (:id review)
               :class (stl/css :review-item)}
         [:div {:class (stl/css :review-info)}
          [:h4 {:class (stl/css :review-name)} (:title review)]
          [:p {:class (stl/css :review-description)} (:description review)]
          [:div {:class (stl/css :review-meta)}
           [:span {:class (stl/css :review-status
                                   :status-open (= (:status review) :open)
                                   :status-closed (= (:status review) :closed))}
            (str/capital (:status review))]
           [:span {:class (stl/css :review-comments)}
            (str (count (:comments review)) " comments")]]]])
      [:div {:class (stl/css :no-reviews)}
       [:p "No design reviews yet"]
       [:p {:class (stl/css :no-reviews-subtitle)}
        "Create a review to get feedback on your designs"]])]])

;; ============================================================================
;; Main Collaboration Overlay
;; ============================================================================

(mf/defc collaboration-overlay*
  {::mf/props :obj
   ::mf/private true}
  [{:keys [state zoom]}]
  (let [collaboration-state (dc/get-collaboration-state state)
        {:keys [active-users cursors selections comments]} collaboration-state]

    (when (dc/is-collaborating? state)
      [:div {:class (stl/css :collaboration-overlay)}
       ;; User presence indicator
       [:> user-presence-indicator* {:users active-users}]

       ;; Collaborative cursors
       [:> collaborative-cursors* {:cursors cursors :zoom zoom}]

       ;; Collaborative selections
       [:> collaborative-selections* {:selections selections :zoom zoom}]

       ;; Review comments
       [:> review-comments* {:comments comments :zoom zoom}]])))

;; ============================================================================
;; Collaboration Toolbar
;; ============================================================================

(mf/defc collaboration-toolbar*
  {::mf/props :obj
   ::mf/private true}
  [{:keys [state on-toggle-reviews on-toggle-comments]}]
  (let [is-collaborating (dc/is-collaborating? state)
        active-users (dc/get-active-users state)]

    [:div {:class (stl/css :collaboration-toolbar)}
     (if is-collaborating
       [:div {:class (stl/css :collaboration-active)}
        [:div {:class (stl/css :collaboration-status)}
         [:span {:class (stl/css :status-dot :status-active)}]
         [:span {:class (stl/css :status-text)}
          (str "Live collaboration with " (count active-users) " users")]]

        [:div {:class (stl/css :collaboration-actions)}
         [:button {:class (stl/css :toolbar-btn)
                   :on-click on-toggle-reviews}
          "📝 Reviews"]
         [:button {:class (stl/css :toolbar-btn)
                   :on-click on-toggle-comments}
          "💬 Comments"]
         [:button {:class (stl/css :toolbar-btn)
                   :on-click #(st/emit! (dc/leave-collaboration))}
          "Leave Session"]]]

       [:div {:class (stl/css :collaboration-inactive)}
        [:button {:class (stl/css :start-collaboration-btn)
                  :on-click #(st/emit! (dc/join-collaboration (deref refs/current-file-id)))}
         "👥 Start Collaboration"]])]))

;; ============================================================================
;; Utility Functions
;; ============================================================================

(defn format-time
  "Format timestamp for display"
  [timestamp]
  (when timestamp
    (let [date (js/Date. timestamp)
          now (js/Date.)
          diff-ms (- (.getTime now) (.getTime date))
          diff-minutes (/ diff-ms 60000)]

      (cond
        (< diff-minutes 1) "just now"
        (< diff-minutes 60) (str (Math/floor diff-minutes) "m ago")
        (< diff-minutes 1440) (str (Math/floor (/ diff-minutes 60)) "h ago")
        :else (.toLocaleDateString date)))))

(defn calculate-cursor-position
  "Calculate cursor position relative to viewport"
  [event viewport]
  (let [rect (.getBoundingClientRect viewport)
        x (- (.-clientX event) (.-left rect))
        y (- (.-clientY event) (.-top rect))]
    {:x x :y y}))

;; ============================================================================
;; Event Handlers
;; ============================================================================

(defn handle-mouse-move
  "Handle mouse movement for cursor sharing"
  [event]
  (when (dc/is-collaborating? @st/state)
    (let [viewport (dom/get-element "viewport")
          position (calculate-cursor-position event viewport)]
      (st/emit! (dc/send-cursor-update (:x position) (:y position))))))

(defn handle-selection-change
  "Handle selection changes for sharing"
  [selection]
  (when (dc/is-collaborating? @st/state)
    (st/emit! (dc/send-selection-update selection))))

(defn handle-element-edit
  "Handle element edits for collaboration"
  [element-id changes]
  (when (dc/is-collaborating? @st/state)
    (st/emit! (dc/send-edit-update element-id changes))))

;; ============================================================================
;; Integration with Main UI
;; ============================================================================

;; Add collaboration overlay to workspace
(defmethod app.main.ui.render/component :collaboration-overlay
  [props]
  (mf/element collaboration-overlay* props))

;; Add collaboration toolbar to workspace
(defmethod app.main.ui.render/component :collaboration-toolbar
  [props]
  (mf/element collaboration-toolbar* props))
