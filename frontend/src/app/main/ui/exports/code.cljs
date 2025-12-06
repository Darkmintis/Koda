;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) KODA INC

(ns app.main.ui.exports.code
  "The code generation dialog/modal for Koda AI"
  (:require-macros [app.main.style :as stl])
  (:require
   [app.common.data :as d]
   [app.common.data.macros :as dm]
   [app.main.data.exports.code :as cexp]
   [app.main.data.modal :as modal]
   [app.main.refs :as refs]
   [app.main.store :as st]
   [app.main.ui.ds.product.loader :refer [loader*]]
   [app.main.ui.ds.foundations.assets.icon :as i]
   [app.main.ui.icons :as deprecated-icon]
   [app.util.dom :as dom]
   [app.util.i18n :as i18n :refer [tr]]
   [beicon.v2.core :as rx]
   [rumext.v2 :as mf]))

;; Framework options with labels
(def framework-options
  [{:value :react-typescript :label "React + TypeScript" :icon "⚛️"}
   {:value :react :label "React + JavaScript" :icon "⚛️"}
   {:value :vue-typescript :label "Vue + TypeScript" :icon "💚"}
   {:value :vue :label "Vue + JavaScript" :icon "💚"}
   {:value :html-css-typescript :label "HTML/CSS + TypeScript" :icon "🌐"}
   {:value :html-css :label "HTML/CSS" :icon "🌐"}])

;; CSS framework options
(def css-options
  [{:value :tailwind :label "Tailwind CSS" :icon "🎨"}
   {:value :css-modules :label "CSS Modules" :icon "📦"}
   {:value :scss :label "SCSS/Sass" :icon "💅"}
   {:value :vanilla :label "Vanilla CSS" :icon "✨"}])

(defn- initialize-state
  "Initialize code generation dialog state"
  []
  {:status :configure ;; :configure | :generating | :complete | :error
   :options {:framework :react-typescript
             :css-framework :tailwind
             :generate-tokens true
             :generate-components true
             :include-routing true
             :include-interactions true
             :responsive true
             :accessibility true
             :dark-mode false}
   :result nil
   :error nil})

(mf/defc framework-selector*
  {::mf/props :obj
   ::mf/private true}
  [{:keys [selected on-change]}]
  [:div {:class (stl/css :framework-selector)}
   [:label {:class (stl/css :selector-label)} "Framework"]
   [:div {:class (stl/css :framework-options)}
    (for [{:keys [value label icon]} framework-options]
      [:button {:key (name value)
                :class (stl/css-case :framework-option true
                                     :selected (= selected value))
                :on-click #(on-change :framework value)}
       [:span {:class (stl/css :option-icon)} icon]
       [:span {:class (stl/css :option-label)} label]])]])

(mf/defc css-selector*
  {::mf/props :obj
   ::mf/private true}
  [{:keys [selected on-change]}]
  [:div {:class (stl/css :css-selector)}
   [:label {:class (stl/css :selector-label)} "CSS Framework"]
   [:div {:class (stl/css :css-options)}
    (for [{:keys [value label icon]} css-options]
      [:button {:key (name value)
                :class (stl/css-case :css-option true
                                     :selected (= selected value))
                :on-click #(on-change :css-framework value)}
       [:span {:class (stl/css :option-icon)} icon]
       [:span {:class (stl/css :option-label)} label]])]])

(mf/defc toggle-option*
  {::mf/props :obj
   ::mf/private true}
  [{:keys [id label checked on-change description]}]
  [:div {:class (stl/css :toggle-option)}
   [:label {:class (stl/css :toggle-label)
            :for id}
    [:input {:type "checkbox"
             :id id
             :checked checked
             :on-change #(on-change id (not checked))}]
    [:span {:class (stl/css-case :toggle-switch true
                                 :checked checked)}]
    [:span {:class (stl/css :toggle-text)} label]]
   (when description
     [:span {:class (stl/css :toggle-description)} description])])

(mf/defc code-generation-dialog
  {::mf/register modal/components
   ::mf/register-as :code-generation
   ::mf/props :obj}
  [{:keys [file-id page-id]}]
  (let [state*    (mf/use-state initialize-state)
        state     (deref state*)
        status    (:status state)
        options   (:options state)

        on-option-change
        (mf/use-fn
         (fn [key value]
           (swap! state* assoc-in [:options key] value)))

        on-toggle-change
        (mf/use-fn
         (fn [id checked]
           (swap! state* assoc-in [:options (keyword id)] checked)))

        on-cancel
        (mf/use-fn
         (fn [event]
           (dom/prevent-default event)
           (st/emit! (modal/hide))))

        on-generate
        (mf/use-fn
         (mf/deps file-id page-id options)
         (fn [event]
           (dom/prevent-default event)
           (swap! state* assoc :status :generating)
           ;; Trigger code generation with callbacks
           (st/emit! (cexp/generate-code 
                      {:file-id file-id
                       :page-id page-id
                       :options options
                       :on-success (fn [result]
                                     (swap! state* assoc 
                                            :status :complete
                                            :result result))
                       :on-error (fn [error]
                                   (swap! state* assoc 
                                          :status :error
                                          :error (or (:message error) 
                                                     "Code generation failed")))}))))

        on-download
        (mf/use-fn
         (mf/deps state)
         (fn [event]
           (dom/prevent-default event)
           (when-let [result (:result state)]
             (st/emit! (cexp/download-generated-code 
                        {:download-url (:download-url result)
                         :filename (or (:filename result) "koda-generated-code.zip")})))))]

    [:div {:class (stl/css :modal-overlay)}
     [:div {:class (stl/css :modal-container :code-generation-modal)}
      
      ;; Header
      [:div {:class (stl/css :modal-header)}
       [:div {:class (stl/css :header-content)}
        [:span {:class (stl/css :header-icon)} "⚡"]
        [:h2 {:class (stl/css :modal-title)} "Generate Code"]]
       [:button {:class (stl/css :modal-close-btn)
                 :on-click on-cancel} deprecated-icon/close]]

      ;; Content
      (case status
        :configure
        [:*
         [:div {:class (stl/css :modal-content :code-gen-content)}
          [:p {:class (stl/css :modal-msg)}
           "Generate production-ready code from your design. Select your preferred framework and options below."]

          ;; Framework selection
          [:> framework-selector* {:selected (:framework options)
                                   :on-change on-option-change}]

          ;; CSS Framework selection
          [:> css-selector* {:selected (:css-framework options)
                             :on-change on-option-change}]

          ;; Additional options
          [:div {:class (stl/css :options-section)}
           [:label {:class (stl/css :section-label)} "Options"]
           [:div {:class (stl/css :options-grid)}
            [:> toggle-option* {:id "generate-tokens"
                                :label "Design Tokens"
                                :checked (:generate-tokens options)
                                :on-change on-toggle-change
                                :description "Colors, spacing, typography"}]
            [:> toggle-option* {:id "generate-components"
                                :label "Components"
                                :checked (:generate-components options)
                                :on-change on-toggle-change
                                :description "Reusable UI components"}]
            [:> toggle-option* {:id "include-routing"
                                :label "Routing"
                                :checked (:include-routing options)
                                :on-change on-toggle-change
                                :description "Navigation from prototype flows"}]
            [:> toggle-option* {:id "include-interactions"
                                :label "Interactions"
                                :checked (:include-interactions options)
                                :on-change on-toggle-change
                                :description "Event handlers & animations"}]
            [:> toggle-option* {:id "responsive"
                                :label "Responsive"
                                :checked (:responsive options)
                                :on-change on-toggle-change
                                :description "Mobile-first breakpoints"}]
            [:> toggle-option* {:id "accessibility"
                                :label "Accessibility"
                                :checked (:accessibility options)
                                :on-change on-toggle-change
                                :description "ARIA labels & semantic HTML"}]
            [:> toggle-option* {:id "dark-mode"
                                :label "Dark Mode"
                                :checked (:dark-mode options)
                                :on-change on-toggle-change
                                :description "Dark theme support"}]]]]

         [:div {:class (stl/css :modal-footer)}
          [:button {:class (stl/css :btn-secondary)
                    :on-click on-cancel}
           "Cancel"]
          [:button {:class (stl/css :btn-primary)
                    :on-click on-generate}
           [:span {:class (stl/css :btn-icon)} "⚡"]
           "Generate Code"]]]

        :generating
        [:div {:class (stl/css :modal-content :generating-content)}
         [:div {:class (stl/css :generating-animation)}
          [:> loader* {:width 48 :title "Generating code..."}]]
         [:h3 {:class (stl/css :generating-title)} "Generating your code..."]
         [:p {:class (stl/css :generating-msg)}
          "Koda AI is analyzing your design and generating production-ready code. This may take a moment."]]

        :complete
        [:*
         [:div {:class (stl/css :modal-content :complete-content)}
          [:div {:class (stl/css :success-icon)} "✅"]
          [:h3 {:class (stl/css :complete-title)} "Code Generated Successfully!"]
          [:p {:class (stl/css :complete-msg)}
           "Your code is ready to download. The package includes components, styles, and routing based on your design."]
          [:div {:class (stl/css :result-summary)}
           [:div {:class (stl/css :summary-item)}
            [:span "Components"] [:strong (get-in state [:result :component-count] 0)]]
           [:div {:class (stl/css :summary-item)}
            [:span "Pages"] [:strong (get-in state [:result :page-count] 0)]]
           [:div {:class (stl/css :summary-item)}
            [:span "Routes"] [:strong (get-in state [:result :route-count] 0)]]]]
         [:div {:class (stl/css :modal-footer)}
          [:button {:class (stl/css :btn-secondary)
                    :on-click on-cancel}
           "Close"]
          [:button {:class (stl/css :btn-primary :download-btn)
                    :on-click on-download}
           [:span {:class (stl/css :btn-icon)} "📥"]
           "Download Code"]]]

        :error
        [:*
         [:div {:class (stl/css :modal-content :error-content)}
          [:div {:class (stl/css :error-icon)} "❌"]
          [:h3 {:class (stl/css :error-title)} "Generation Failed"]
          [:p {:class (stl/css :error-msg)}
           (or (:error state) "An error occurred while generating code. Please try again.")]]
         [:div {:class (stl/css :modal-footer)}
          [:button {:class (stl/css :btn-secondary)
                    :on-click on-cancel}
           "Close"]
          [:button {:class (stl/css :btn-primary)
                    :on-click #(swap! state* assoc :status :configure :error nil)}
           "Try Again"]]])]]))
