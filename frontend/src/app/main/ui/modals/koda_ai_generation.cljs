;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) KODA INC

(ns app.main.ui.modals.koda-ai-generation
  (:require-macros [app.main.style :as stl])
  (:require
   [app.main.data.event :as ev]
   [app.main.data.modal :as modal]
   [app.main.data.workspace :as dw]
   [app.main.refs :as refs]
   [app.main.store :as st]
   [app.main.ui.components.dropdown :refer [dropdown]]
   [app.main.ui.ds.buttons.button :refer [button*]]
   [app.main.ui.ds.foundations.assets.icon :as i]
   [app.main.ui.ds.layout.modal :refer [modal-container*]]
   [app.util.dom :as dom]
   [app.util.i18n :as i18n :refer [tr]]
   [beicon.v2.core :as rx]
   [cuerdas.core :as str]
   [rumext.v2 :as mf]))

;; ============================================================================
;; Framework Selection Component
;; ============================================================================

(mf/defc framework-selector*
  {::mf/props :obj
   ::mf/private true}
  [{:keys [selected-framework on-framework-change]}]
  (let [frameworks [{:id "react" :name "React" :icon "⚛️"}
                    {:id "vue" :name "Vue" :icon "💚"}
                    {:id "angular" :name "Angular" :icon "🅰️"}
                    {:id "svelte" :name "Svelte" :icon "🧡"}
                    {:id "html-css" :name "HTML+CSS" :icon "🌐"}
                    {:id "react-native" :name "React Native" :icon "📱"}
                    {:id "flutter" :name "Flutter" :icon "🦋"}]]

    [:div {:class (stl/css :framework-selector)}
     [:h4 {:class (stl/css :selector-title)} "Choose Framework"]
     [:div {:class (stl/css :framework-grid)}
      (for [framework frameworks]
        [:button {:key (:id framework)
                  :class (stl/css-case :framework-btn true
                                       :selected (= selected-framework (:id framework)))
                  :on-click #(on-framework-change (:id framework))}
         [:span {:class (stl/css :framework-icon)} (:icon framework)]
         [:span {:class (stl/css :framework-name)} (:name framework)]])]]))

;; ============================================================================
;; Generation Options Component
;; ============================================================================

(mf/defc generation-options*
  {::mf/props :obj
   ::mf/private true}
  [{:keys [options on-option-change]}]
  [:div {:class (stl/css :generation-options)}
   [:h4 {:class (stl/css :options-title)} "Generation Options"]

   [:div {:class (stl/css :option-group)}
    [:label {:class (stl/css :option-label)}
     [:input {:type "checkbox"
              :checked (:include-styles options)
              :on-change #(on-option-change :include-styles (not (:include-styles options)))}]
     [:span {:class (stl/css :option-text)} "Include optimized CSS/SCSS"]]

    [:label {:class (stl/css :option-label)}
     [:input {:type "checkbox"
              :checked (:responsive options)
              :on-change #(on-option-change :responsive (not (:responsive options)))}]
     [:span {:class (stl/css :option-text)} "Make responsive"]]

    [:label {:class (stl/css :option-label)}
     [:input {:type "checkbox"
              :checked (:accessibility options)
              :on-change #(on-option-change :accessibility (not (:accessibility options)))}]
     [:span {:class (stl/css :option-text)} "WCAG 2.1 AA accessibility"]]

    [:label {:class (stl/css :option-label)}
     [:input {:type "checkbox"
              :checked (:typescript options)
              :on-change #(on-option-change :typescript (not (:typescript options)))}]
     [:span {:class (stl/css :option-text)} "TypeScript support"]]]

   [:div {:class (stl/css :option-group)}
    [:label {:class (stl/css :option-label)}
     "Project Structure:"
     [:select {:class (stl/css :structure-select)
               :value (:structure options)
               :on-change #(on-option-change :structure (dom/get-target-val %))}
      [:option {:value "flat"} "Flat (single file)"]
      [:option {:value "modular"} "Modular (component files)"]
      [:option {:value "full"} "Full Project (with routing)"]]]]])

;; ============================================================================
;; Generation Preview Component
;; ============================================================================

(mf/defc generation-preview*
  {::mf/props :obj
   ::mf/private true}
  [{:keys [framework options file]}]
  (let [estimated-files (calculate-estimated-files framework options)
        estimated-size (calculate-estimated-size framework options)]

    [:div {:class (stl/css :generation-preview)}
     [:h4 {:class (stl/css :preview-title)} "Generation Preview"]

     [:div {:class (stl/css :preview-stats)}
      [:div {:class (stl/css :stat-item)}
       [:span {:class (stl/css :stat-icon)} "📁"]
       [:span {:class (stl/css :stat-label)} "Files:"]
       [:span {:class (stl/css :stat-value)} estimated-files]]

      [:div {:class (stl/css :stat-item)}
       [:span {:class (stl/css :stat-icon)} "📦"]
       [:span {:class (stl/css :stat-label)} "Size:"]
       [:span {:class (stl/css :stat-value)} (str "~" estimated-size "KB")]]

      [:div {:class (stl/css :stat-item)}
       [:span {:class (stl/css :stat-icon)} "⚡"]
       [:span {:class (stl/css :stat-label)} "Quality:"]
       [:span {:class (stl/css :stat-value :high-quality)} "A+ Grade"]]]

     [:div {:class (stl/css :preview-features)}
      [:h5 {:class (stl/css :features-title)} "Included Features:"]
      [:ul {:class (stl/css :features-list)}
       (when (:include-styles options)
         [:li "🎨 Optimized CSS with BEM methodology"])
       (when (:responsive options)
         [:li "📱 Mobile-first responsive design"])
       (when (:accessibility options)
         [:li "♿ WCAG 2.1 AA compliance"])
       (when (:typescript options)
         [:li "🔷 Full TypeScript support"])
       [:li "🚀 Production-ready code"]
       [:li "🧪 Comprehensive testing"]
       [:li "📚 Complete documentation"]]]]))

;; ============================================================================
;; Main Koda AI Generation Modal
;; ============================================================================

(mf/defc koda-ai-generation-modal*
  {::mf/props :obj
   ::mf/private true}
  [{:keys [file]}]
  (let [selected-framework* (mf/use-state "react")
        generation-options* (mf/use-state {:include-styles true
                                          :responsive true
                                          :accessibility true
                                          :typescript false
                                          :structure "modular"})
        is-generating* (mf/use-state false)
        progress* (mf/use-state 0)

        generate-code
        (mf/use-fn
         (mf/deps selected-framework* generation-options* is-generating*)
         (fn []
           (when (not @is-generating*)
             (reset! is-generating* true)
             (reset! progress* 0)

             ;; Simulate generation progress
             (let [progress-interval (js/setInterval
                                      (fn []
                                        (swap! progress* #(+ % (rand-int 15)))
                                        (when (>= @progress* 100)
                                          (js/clearInterval progress-interval)
                                          (reset! is-generating* false)
                                          ;; In real implementation, this would trigger actual code generation
                                          (st/emit! (modal/show {:type :koda-ai-result
                                                                :file file
                                                                :framework @selected-framework*
                                                                :options @generation-options*}))))
                                      500)])))

        update-option
        (mf/use-fn
         (fn [key value]
           (swap! generation-options* assoc key value)))

        select-framework
        (mf/use-fn
         (fn [framework]
           (reset! selected-framework* framework)))

        close-modal
        (mf/use-fn
         (fn []
           (st/emit! (modal/hide))))]

    [:> modal-container* {}
     [:div {:class (stl/css :koda-ai-modal)}
      [:div {:class (stl/css :modal-header)}
       [:div {:class (stl/css :modal-title-section)}
        [:span {:class (stl/css :modal-icon)} "🚀"]
        [:div {:class (stl/css :modal-title-block)}
         [:h2 {:class (stl/css :modal-title)} "Generate Code with Koda AI"]
         [:p {:class (stl/css :modal-subtitle)}
          "Transform your designs into production-ready applications"]]]

       [:button {:class (stl/css :modal-close-btn)
                 :on-click close-modal
                 :title "Close"}
        "×"]]

      [:div {:class (stl/css :modal-content)}
       ;; Framework Selection
       [:& framework-selector*
        {:selected-framework @selected-framework*
         :on-framework-change select-framework}]

       ;; Generation Options
       [:& generation-options*
        {:options @generation-options*
         :on-option-change update-option}]

       ;; Preview
       [:& generation-preview*
        {:framework @selected-framework*
         :options @generation-options*
         :file file}]]

      [:div {:class (stl/css :modal-footer)}
       (if @is-generating*
         ;; Generation Progress
         [:div {:class (stl/css :generation-progress)}
          [:div {:class (stl/css :progress-bar)}
           [:div {:class (stl/css :progress-fill)
                  :style {:width (str @progress* "%")}}]]
          [:div {:class (stl/css :progress-text)}
           (str "Generating code... " @progress* "%")]]

         ;; Action Buttons
         [:div {:class (stl/css :modal-actions)}
          [:button {:class (stl/css :cancel-btn)
                    :on-click close-modal}
           "Cancel"]

          [:> button* {:variant "primary"
                       :size "large"
                       :on-click generate-code
                       :disabled @is-generating*}
           [:span {:class (stl/css :generate-icon)} "⚡"]
           "Generate Production Code"]])]]]))

;; ============================================================================
;; Utility Functions
;; ============================================================================

(defn calculate-estimated-files [framework options]
  (case (:structure options)
    "flat" 1
    "modular" (case framework
                "react" 8
                "vue" 6
                "angular" 12
                "svelte" 4
                "html-css" 3
                "react-native" 10
                "flutter" 8
                5)
    "full" (case framework
             "react" 15
             "vue" 12
             "angular" 20
             "svelte" 10
             "html-css" 8
             "react-native" 18
             "flutter" 15
             10)))

(defn calculate-estimated-size [framework options]
  (let [base-size (case framework
                    "react" 150
                    "vue" 120
                    "angular" 200
                    "svelte" 80
                    "html-css" 50
                    "react-native" 180
                    "flutter" 160
                    100)
        style-multiplier (if (:include-styles options) 1.3 1.0)
        responsive-multiplier (if (:responsive options) 1.2 1.0)
        accessibility-multiplier (if (:accessibility options) 1.1 1.0)]

    (Math/round (* base-size style-multiplier responsive-multiplier accessibility-multiplier))))

;; ============================================================================
;; Modal Registration
;; ============================================================================

;; Register the modal with the modal system
(defmethod app.main.ui.render/component :koda-ai-generation
  [props]
  (mf/element koda-ai-generation-modal* props))
