;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) KODA INC

(ns app.main.ui.workspace.koda-chat
  (:require-macros [app.main.style :as stl])
  (:require
   [app.main.data.event :as ev]
   [app.main.data.modal :as modal]
   [app.main.data.workspace :as dw]
   [app.main.refs :as refs]
   [app.main.store :as st]
   [app.main.ui.context :as ctx]
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
;; Chat Message Component
;; ============================================================================

(mf/defc chat-message*
  {::mf/props :obj
   ::mf/private true}
  [{:keys [message is-user?]}]
  [:div {:class (stl/css-case :chat-message true
                              :user-message is-user?
                              :ai-message (not is-user?))}
   [:div {:class (stl/css :message-avatar)}
    (if is-user?
      [:span "👤"]
      [:span "🤖"])]

   [:div {:class (stl/css :message-content)}
    [:div {:class (stl/css :message-text)}
     (:content message)]

    (when (:designs message)
      [:div {:class (stl/css :message-designs)}
       [:div {:class (stl/css :designs-header)}
        "Generated Designs:"]
       (for [design (:designs message)]
         [:div {:key (:id design)
                :class (stl/css :design-preview)}
          [:div {:class (stl/css :design-name)}
           (:name design)]
          [:div {:class (stl/css :design-description)}
           (:description design)]
          [:button {:class (stl/css :import-design-btn)
                    :on-click #(st/emit! (ev/event ::ev/import-koda-chat-design design))}
           "Import to Canvas"]])])

    [:div {:class (stl/css :message-time)}
     (format-time (:timestamp message))]]])

;; ============================================================================
;; Model Selection Component
;; ============================================================================

(mf/defc model-selector*
  {::mf/props :obj
   ::mf/private true}
  [{:keys [selected-model on-model-change models]}]
  [:div {:class (stl/css :model-selector)}
   [:select {:class (stl/css :model-select)
             :value (or selected-model "openai-gpt-4")
             :on-change #(on-model-change (dom/get-target-val %))}
    (for [model models]
      [:option {:key (:id model)
                :value (:id model)}
       (str (:name model) " (" (:provider model) ")")])]

   [:div {:class (stl/css :model-info)}
    (when selected-model
      (let [model (first (filter #(= (:id %) selected-model) models))]
        (when model
          [:div {:class (stl/css :model-details)}
           [:div {:class (stl/css :model-context)}
            (str "Context: " (:contextLength model) " tokens")]
           [:div {:class (stl/css :model-pricing)}
            (if (:pricing model)
              (str "$" (:pricing model).input "/1K input, $" (:pricing model).output "/1K output")
              "Free (local model)")]])))]]

;; ============================================================================
;; Chat Input Component
;; ============================================================================

(mf/defc chat-input*
  {::mf/props :obj
   ::mf/private true}
  [{:keys [on-send-message disabled?]}]
  (let [message* (mf/use-state "")
        sending* (mf/use-state false)

        send-message
        (mf/use-fn
         (fn []
           (when (and (not (str/blank? @message*))
                     (not @sending*)
                     (not disabled?))
             (reset! sending* true)
             (on-send-message @message*)
             (reset! message* "")
             (reset! sending* false))))

        handle-key-press
        (mf/use-fn
         (mf/deps message* sending* disabled?)
         (fn [event]
           (when (and (= (.-key event) "Enter")
                     (not (.-shiftKey event)))
             (dom/prevent-default event)
             (send-message))))]

    [:div {:class (stl/css :chat-input-container)}
     [:textarea {:class (stl/css :chat-input)
                 :placeholder "Describe your design idea... (e.g., 'Create a modern dashboard with charts and user stats')"
                 :value @message*
                 :disabled (or @sending* disabled?)
                 :on-change #(reset! message* (dom/get-target-val %))
                 :on-key-press handle-key-press
                 :rows 3}]

     [:div {:class (stl/css :chat-input-actions)}
      [:div {:class (stl/css :input-hints)}
       [:span {:class (stl/css :hint-text)}
        "💡 Try: 'Design a fitness app with workout tracking'"]]

      [:button {:class (stl/css-case :send-button true
                                     :disabled (or (str/blank? @message*) @sending* disabled?))
                :on-click send-message
                :disabled (or (str/blank? @message*) @sending* disabled?)}
       (if @sending?
         "Generating..."
         "Generate Design")]]]))

;; ============================================================================
;; Main Koda Chat Component
;; ============================================================================

(mf/defc koda-chat*
  {::mf/props :obj
   ::mf/private true}
  [{:keys [layout file]}]
  (let [chat-visible? (contains? layout :koda-chat)
        messages* (mf/use-state [])
        selected-model* (mf/use-state "openai-gpt-4")
        is-connected* (mf/use-state false)
        available-models* (mf/use-state [])

        ;; Load available models on mount
        (mf/use-effect
         (fn []
           ;; Fetch available models from backend
           (rx/run! (rx/of nil)
                   (rx/map (fn [_]
                             ;; Mock data - in real implementation, fetch from backend
                             [{:id "openai-gpt-4"
                               :name "GPT-4"
                               :provider "openai"
                               :contextLength 8192
                               :pricing {:input 0.03 :output 0.06}}
                              {:id "anthropic-claude-3"
                               :name "Claude 3 Opus"
                               :provider "anthropic"
                               :contextLength 200000
                               :pricing {:input 0.015 :output 0.075}}
                              {:id "ollama-llama2"
                               :name "Llama 2 (Local)"
                               :provider "ollama"
                               :contextLength 4096
                               :pricing nil}]))
                   (rx/subs (fn [models]
                              (reset! available-models* models)))))

        send-message
        (mf/use-fn
         (mf/deps messages* selected-model*)
         (fn [message]
           (let [user-message {:id (str "msg-" (random-uuid))
                              :content message
                              :timestamp (js/Date.)
                              :type "user"}]
             (swap! messages* conj user-message)

             ;; Simulate AI response (in real implementation, connect to Koda Chat backend)
             (js/setTimeout
              (fn []
                (let [ai-response {:id (str "msg-" (random-uuid))
                                  :content "I'm analyzing your design requirements and generating professional designs. This will take a moment..."
                                  :timestamp (js/Date.)
                                  :type "ai"}]
                  (swap! messages* conj ai-response)

                  ;; Simulate design generation
                  (js/setTimeout
                   (fn []
                     (let [designs-response {:id (str "msg-" (random-uuid))
                                            :content "I've created professional designs for your request!"
                                            :designs [{:id "design-1"
                                                      :name "Modern Dashboard"
                                                      :description "Clean dashboard with charts and metrics"}
                                                     {:id "design-2"
                                                      :name "Mobile Layout"
                                                      :description "Responsive mobile-first design"}]
                                            :timestamp (js/Date.)
                                            :type "ai"}]
                       (swap! messages* conj designs-response)))
                   2000)))
              1000))))

        close-chat
        (mf/use-fn
         (fn []
           (st/emit! (dw/remove-layout-flag :koda-chat))))

        select-model
        (mf/use-fn
         (fn [model-id]
           (reset! selected-model* model-id)))]

    (when chat-visible?
      [:div {:class (stl/css :koda-chat-overlay)}
       [:div {:class (stl/css :koda-chat-panel)}
        ;; Header
        [:div {:class (stl/css :chat-header)}
         [:div {:class (stl/css :chat-title)}
          [:span {:class (stl/css :chat-icon)} "🤖"]
          [:span {:class (stl/css :chat-text)} "Koda Chat"]
          [:span {:class (stl/css :chat-subtitle)} "AI Design Assistant"]]

         [:button {:class (stl/css :chat-close-btn)
                   :on-click close-chat
                   :title "Close Chat"}
          "×"]]

        ;; Model Selector
        [:div {:class (stl/css :chat-model-section)}
         [:& model-selector*
          {:selected-model @selected-model*
           :on-model-change select-model
           :models @available-models*}]]

        ;; Messages
        [:div {:class (stl/css :chat-messages)}
         (if (empty? @messages*)
           ;; Welcome message
           [:div {:class (stl/css :chat-welcome)}
            [:div {:class (stl/css :welcome-icon)} "🎨"]
            [:h3 {:class (stl/css :welcome-title)} "Welcome to Koda Chat!"]
            [:p {:class (stl/css :welcome-text)}
             "Describe your design ideas and I'll generate professional UI designs for you."]
            [:div {:class (stl/css :welcome-examples)}
             [:p {:class (stl/css :examples-title)} "Try these examples:"]
             [:div {:class (stl/css :example-buttons)}
              [:button {:class (stl/css :example-btn)
                        :on-click #(send-message "Create a modern SaaS dashboard")}
               "📊 SaaS Dashboard"]
              [:button {:class (stl/css :example-btn)
                        :on-click #(send-message "Design a mobile fitness app")}
               "💪 Fitness App"]
              [:button {:class (stl/css :example-btn)
                        :on-click #(send-message "Build an e-commerce product page")}
               "🛒 E-commerce"]]]]

           ;; Message list
           (for [message @messages*]
             [:& chat-message*
              {:key (:id message)
               :message message
               :is-user? (= (:type message) "user")}]))
         [:div {:ref #(when % (.scrollIntoView %))}]] ; Auto-scroll to bottom

        ;; Input
        [:& chat-input*
         {:on-send-message send-message
          :disabled? (not @is-connected*)}]]])))

;; ============================================================================
;; Utility Functions
;; ============================================================================

(defn format-time [timestamp]
  (when timestamp
    (let [date (if (instance? js/Date timestamp)
                 timestamp
                 (js/Date. timestamp))
          now (js/Date.)
          diff-ms (- (.getTime now) (.getTime date))
          diff-minutes (/ diff-ms 60000)]

      (cond
        (< diff-minutes 1) "just now"
        (< diff-minutes 60) (str (Math/floor diff-minutes) "m ago")
        (< diff-minutes 1440) (str (Math/floor (/ diff-minutes 60)) "h ago")
        :else (.toLocaleDateString date)))))

;; ============================================================================
;; Integration with Workspace
;; ============================================================================

;; Add Koda Chat to workspace layout
(defmethod app.main.ui.render/component :koda-chat
  [props]
  (mf/element koda-chat* props))
