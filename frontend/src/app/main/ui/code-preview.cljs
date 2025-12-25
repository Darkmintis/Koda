;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) KODA INC

(ns app.main.ui.code-preview
  "Code quality preview with syntax highlighting for Koda"
  (:require-macros [app.main.style :as stl])
  (:require
   [app.common.data :as d]
   [app.common.data.macros :as dm]
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
;; Syntax Highlighter (Simple implementation)
;; ============================================================================

(defn- highlight-code
  "Basic syntax highlighting for code preview"
  [code language]
  (let [lines (str/split code #"\n")]
    (for [line lines]
      [:span {:class (stl/css :code-line)}
       (highlight-line line language)])))

(defn- highlight-line
  "Highlight syntax for a single line"
  [line language]
  (case language
    "javascript"
    (highlight-javascript line)

    "typescript"
    (highlight-typescript line)

    "html"
    (highlight-html line)

    "css"
    (highlight-css line)

    "dart"
    (highlight-dart line)

    ;; Default: no highlighting
    line))

(defn- highlight-javascript
  "Highlight JavaScript/TypeScript syntax"
  [line]
  (-> line
      ;; Keywords
      (str/replace #"(\bfunction\b|\bconst\b|\blet\b|\bvar\b|\bif\b|\belse\b|\bfor\b|\bwhile\b|\breturn\b|\bimport\b|\bexport\b|\bfrom\b|\bclass\b|\bextends\b)" "<span class=\"keyword\">$1</span>")
      ;; Strings
      (str/replace #"(\"[^\"]*\"|'[^']*')" "<span class=\"string\">$1</span>")
      ;; Comments
      (str/replace #"//.*" "<span class=\"comment\">$0</span>")
      ;; Functions
      (str/replace #"([a-zA-Z_$][a-zA-Z0-9_$]*)\s*\(" "<span class=\"function\">$1</span>(")
      ;; JSX tags
      (str/replace #"(&lt;/?[a-zA-Z][^&]*&gt;)" "<span class=\"jsx-tag\">$1</span>")
      (str/replace #"&lt;" "<")
      (str/replace #"&gt;" ">")))

(defn- highlight-typescript
  "Highlight TypeScript (extends JavaScript)"
  [line]
  (-> (highlight-javascript line)
      ;; Type annotations
      (str/replace #"(: [a-zA-Z\[\]{}|&<>]+)" "<span class=\"type\">$1</span>")
      ;; Interface/class declarations
      (str/replace #"(\binterface\b|\btype\b|\bclass\b)" "<span class=\"keyword\">$1</span>")))

(defn- highlight-html
  "Highlight HTML syntax"
  [line]
  (-> line
      ;; Tags
      (str/replace #"(&lt;/?[a-zA-Z][^&]*&gt;)" "<span class=\"html-tag\">$1</span>")
      ;; Attributes
      (str/replace #"([a-zA-Z-]+)=\"([^\"]*)\"" "<span class=\"html-attr\">$1</span>=\"<span class=\"html-value\">$2</span>\"")
      ;; Comments
      (str/replace #"(&lt;!--.*?--&gt;)" "<span class=\"comment\">$1</span>")
      (str/replace #"&lt;" "<")
      (str/replace #"&gt;" ">")))

(defn- highlight-css
  "Highlight CSS syntax"
  [line]
  (-> line
      ;; Selectors
      (str/replace #"^([^{]+)\{" "<span class=\"css-selector\">$1</span>{")
      ;; Properties
      (str/replace #"([a-z-]+):" "<span class=\"css-property\">$1</span>:")
      ;; Values
      (str/replace #": ([^;]+);" ": <span class=\"css-value\">$1</span>;")
      ;; Comments
      (str/replace #"(/\*.*?\*/)" "<span class=\"comment\">$1</span>")))

(defn- highlight-dart
  "Highlight Dart syntax"
  [line]
  (-> line
      ;; Keywords
      (str/replace #"(\bclass\b|\bvoid\b|\bString\b|\bint\b|\bdouble\b|\bbool\b|\bconst\b|\bfinal\b|\bvar\b|\bif\b|\belse\b|\bfor\b|\bwhile\b|\breturn\b|\bimport\b|\bexport\b)" "<span class=\"keyword\">$1</span>")
      ;; Strings
      (str/replace #"(\"[^\"]*\"|'[^']*')" "<span class=\"string\">$1</span>")
      ;; Comments
      (str/replace #"//.*" "<span class=\"comment\">$0</span>")
      ;; Classes and functions
      (str/replace #"(\b[A-Z][a-zA-Z]*\b)" "<span class=\"class\">$1</span>")))

;; ============================================================================
;; Code Quality Metrics
;; ============================================================================

(defn- calculate-code-quality
  "Calculate code quality metrics"
  [code language]
  (let [lines (str/split code #"\n")
        total-lines (count lines)
        non-empty-lines (count (filter #(not (str/blank? %)) lines))
        avg-line-length (if (> non-empty-lines 0)
                         (/ (reduce + (map count lines)) non-empty-lines)
                         0)
        has-proper-indentation (every? #(or (str/blank? %)
                                           (str/starts-with? % " ")
                                           (str/starts-with? % "\t")) lines)
        has-consistent-spacing true ; Simplified check
        has-meaningful-names true ; Would need AST analysis
        has-error-handling (str/includes? code "try") ; Basic check
        has-comments (str/includes? code "//" "/\\*") ; Basic check]

    {:total-lines total-lines
     :code-density (if (> total-lines 0) (/ non-empty-lines total-lines) 0)
     :avg-line-length avg-line-length
     :has-proper-indentation has-proper-indentation
     :has-consistent-spacing has-consistent-spacing
     :has-meaningful-names has-meaningful-names
     :has-error-handling has-error-handling
     :has-comments has-comments
     :overall-score (calculate-overall-score
                    {:indentation has-proper-indentation
                     :spacing has-consistent-spacing
                     :names has-meaningful-names
                     :error-handling has-error-handling
                     :comments has-comments})}))

(defn- calculate-overall-score
  "Calculate overall quality score (0-100)"
  [metrics]
  (let [weights {:indentation 0.2
                 :spacing 0.15
                 :names 0.25
                 :error-handling 0.2
                 :comments 0.2}
        score (reduce + (map #(* (weights %1) (if %2 1 0)) (keys weights) (vals weights)))]
    (Math/round (* score 100))))

;; ============================================================================
;; Code Preview Component
;; ============================================================================

(mf/defc code-preview*
  {::mf/props :obj
   ::mf/private true}
  [{:keys [code language on-copy on-close title]}]
  (let [quality-metrics (calculate-code-quality code language)
        {:keys [total-lines overall-score]} quality-metrics]

    [:div {:class (stl/css :code-preview-overlay)}
     [:div {:class (stl/css :code-preview-modal)}

      ;; Header
      [:div {:class (stl/css :preview-header)}
       [:div {:class (stl/css :header-left)}
        [:span {:class (stl/css :header-icon)} "📝"]
        [:h3 {:class (stl/css :header-title)} (or title "Code Preview")]
        [:div {:class (stl/css :quality-badge
                               :quality-good (> overall-score 80)
                               :quality-ok (> overall-score 60)
                               :quality-poor (<= overall-score 60))}
         (str "Quality: " overall-score "/100")]]
       [:div {:class (stl/css :header-right)}
        [:span {:class (stl/css :line-count)} (str total-lines " lines")]
        [:button {:class (stl/css :close-btn)
                  :on-click on-close}
         (icons/icon :close "close")]]]

      ;; Code display area
      [:div {:class (stl/css :code-display-area)}
       [:div {:class (stl/css :code-container)}
        [:pre {:class (stl/css :code-block)}
         [:code {:class (stl/css-case :code-content true
                                      :language language)}
          [highlight-code code language]]]]

       ;; Line numbers (optional)
       [:div {:class (stl/css :line-numbers)}
        (for [i (range 1 (inc (count (str/split code #"\n"))))]
          [:div {:key i :class (stl/css :line-number)} i])]]

      ;; Quality metrics panel
      [:div {:class (stl/css :quality-panel)}
       [:h4 {:class (stl/css :quality-title)} "Code Quality Analysis"]
       [:div {:class (stl/css :quality-metrics)}
        [:div {:class (stl/css :metric-item)}
         [:span {:class (stl/css :metric-label)} "Lines of Code"]
         [:span {:class (stl/css :metric-value)} total-lines]]

        [:div {:class (stl/css :metric-item)}
         [:span {:class (stl/css :metric-label)} "Code Density"]
         [:span {:class (stl/css :metric-value)}
          (str (Math/round (* (:code-density quality-metrics) 100)) "%")]]

        [:div {:class (stl/css :metric-item)}
         [:span {:class (stl/css :metric-label)} "Indentation"]
         [:span {:class (stl/css-case :metric-value
                                      :metric-good (:has-proper-indentation quality-metrics)
                                      :metric-bad (not (:has-proper-indentation quality-metrics)))}
          (if (:has-proper-indentation quality-metrics) "✓ Good" "✗ Needs improvement")]]

        [:div {:class (stl/css :metric-item)}
         [:span {:class (stl/css :metric-label)} "Error Handling"]
         [:span {:class (stl/css-case :metric-value
                                      :metric-good (:has-error-handling quality-metrics)
                                      :metric-bad (not (:has-error-handling quality-metrics)))}
          (if (:has-error-handling quality-metrics) "✓ Present" "○ Optional")]]]

      ;; Action buttons
      [:div {:class (stl/css :preview-actions)}
       [:button {:class (stl/css :copy-btn :primary)
                 :on-click #(on-copy code)}
        "📋 Copy Code"]

       [:button {:class (stl/css :download-btn)
                 :on-click #(download-code code language)}
        "📥 Download"]

       [:button {:class (stl/css :close-btn :secondary)
                 :on-click on-close}
        "Close"]]]]))

;; ============================================================================
;; Public API Functions
;; ============================================================================

(defn show-code-preview
  "Show code preview modal"
  [code language & {:keys [title on-copy on-close]}]
  (let [modal-props {:code code
                     :language language
                     :title title
                     :on-copy (or on-copy copy-to-clipboard)
                     :on-close (or on-close hide-code-preview)}]
    (st/emit! (modal/show {:type :code-preview
                           :props modal-props}))))

(defn hide-code-preview
  "Hide code preview modal"
  []
  (st/emit! (modal/hide)))

(defn copy-to-clipboard
  "Copy code to clipboard"
  [code]
  (-> (js/navigator.clipboard.writeText code)
      (.then #(js/console.log "Code copied to clipboard"))
      (.catch #(js/console.log "Failed to copy code:" %))))

(defn download-code
  "Download code as file"
  [code language]
  (let [extension (case language
                    "javascript" ".js"
                    "typescript" ".ts"
                    "html" ".html"
                    "css" ".css"
                    "dart" ".dart"
                    ".txt")
        filename (str "koda-generated-code" extension)
        blob (js/Blob. [code] {:type "text/plain"})
        url (js/URL.createObjectURL blob)
        a (js/document.createElement "a")]
    (set! (.-href a) url)
    (set! (.-download a) filename)
    (set! (.-style.visibility a) "hidden")
    (.appendChild js/document.body a)
    (.click a)
    (.removeChild js/document.body a)
    (js/URL.revokeObjectURL url)))

(defn get-code-quality
  "Get code quality metrics for code"
  [code language]
  (calculate-code-quality code language))

;; ============================================================================
;; Syntax Highlighting Styles (CSS-in-CLJS)
;; ============================================================================

(def ^:private syntax-styles
  ".code-preview-modal .code-content {
     font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
     font-size: 13px;
     line-height: 1.4;
   }

   .code-preview-modal .keyword {
     color: #0000ff;
     font-weight: bold;
   }

   .code-preview-modal .string {
     color: #008000;
   }

   .code-preview-modal .comment {
     color: #808080;
     font-style: italic;
   }

   .code-preview-modal .function {
     color: #795e26;
   }

   .code-preview-modal .jsx-tag {
     color: #800000;
   }

   .code-preview-modal .html-tag {
     color: #800000;
   }

   .code-preview-modal .html-attr {
     color: #ff0000;
   }

   .code-preview-modal .html-value {
     color: #0000ff;
   }

   .code-preview-modal .css-selector {
     color: #800000;
     font-weight: bold;
   }

   .code-preview-modal .css-property {
     color: #ff0000;
   }

   .code-preview-modal .css-value {
     color: #0000ff;
   }

   .code-preview-modal .class {
     color: #267f99;
   }

   .code-preview-modal .type {
     color: #267f99;
   }")

;; Inject styles on component mount
(defn- inject-syntax-styles
  "Inject syntax highlighting styles"
  []
  (when-not (dom/get-element-by-id "koda-syntax-styles")
    (let [style (dom/create-element "style")]
      (set! (.-id style) "koda-syntax-styles")
      (set! (.-innerHTML style) syntax-styles)
      (.appendChild (.-head js/document) style))))

;; ============================================================================
;; Component Registration
;; ============================================================================

;; Register the code preview component
(defmethod app.main.ui.render/component :code-preview
  [props]
  (inject-syntax-styles)
  (mf/element code-preview* props))
