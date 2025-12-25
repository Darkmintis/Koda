;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) KODA INC

(ns app.main.ui.preview-panel
  "Real-time code preview panel for Koda"
  (:require-macros [app.main.style :as stl])
  (:require
   [app.common.data :as d]
   [app.common.data.macros :as dm]
   [app.main.data.workspace :as dw]
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
;; Preview Panel State
;; ============================================================================

(def ^:private preview-panel-ref (atom nil))

(def ^:private initial-state
  {:visible false
   :framework "react"
   :selected-file nil
   :generated-code ""
   :is-generating false
   :last-update nil
   :error nil})

(def ^:private state* (atom initial-state))

;; ============================================================================
;; Preview Panel Component
;; ============================================================================

(mf/defc preview-panel*
  {::mf/props :obj
   ::mf/private true}
  []
  (let [state (deref state*)
        {:keys [visible framework selected-file generated-code is-generating error]} @state*

        ;; Framework options
        frameworks ["react" "vue" "html-css" "react-native" "flutter"]

        ;; Resize hook for panel sizing
        {:keys [size on-resize-start]} (r/use-resize-hook
                                        :code-preview-panel
                                        {:width 400
                                         :min-width 300
                                         :max-width 800})]

    (when visible
      [:div {:class (stl/css :preview-panel-overlay)}
       [:div {:class (stl/css :preview-panel)
              :style {:width (:width size)}
              :ref on-resize-start}

        ;; Header
        [:div {:class (stl/css :preview-header)}
         [:div {:class (stl/css :header-left)}
          [:span {:class (stl/css :header-icon)} "⚡"]
          [:h3 {:class (stl/css :header-title)} "Code Preview"]]

         [:div {:class (stl/css :header-right)}
          ;; Framework selector
          [:select {:class (stl/css :framework-select)
                    :value framework
                    :on-change #(change-framework (dom/get-target-val %))}
           (for [fw frameworks]
             [:option {:key fw :value fw} fw])]

          ;; Close button
          [:button {:class (stl/css :close-btn)
                    :on-click close-preview-panel}
           (icons/icon :close "close")]]]

        ;; Content
        [:div {:class (stl/css :preview-content)}
         (cond
           is-generating
           [:div {:class (stl/css :loading-state)}
            [:div {:class (stl/css :loading-spinner)}]
            [:p {:class (stl/css :loading-text)} "Generating code..."]]

           error
           [:div {:class (stl/css :error-state)}
            [:div {:class (stl/css :error-icon)} "❌"]
            [:p {:class (stl/css :error-message)} error]
            [:button {:class (stl/css :retry-btn)
                      :on-click retry-generation}
             "Retry"]]

           :else
           [:*
            ;; File selector
            [:div {:class (stl/css :file-selector)}
             [:select {:class (stl/css :file-select)
                       :value (or selected-file "")
                       :on-change #(select-file (dom/get-target-val %))}
              [:option {:value ""} "Select file..."]
              ;; Dynamic file options will be populated from generated code
              ]]

            ;; Code display
            [:div {:class (stl/css :code-display)}
             [:pre {:class (stl/css :code-block)}
              [:code {:class (stl/css-case :code-content true
                                           :language (get-language-class framework))}
               generated-code]]]

            ;; Action buttons
            [:div {:class (stl/css :action-buttons)}
             [:button {:class (stl/css :copy-btn)
                       :on-click copy-current-code}
              "📋 Copy Code"]

             [:button {:class (stl/css :download-btn)
                       :on-click download-project}
              "📥 Download Project"]]])]])))

;; ============================================================================
;; Public API Functions
;; ============================================================================

(defn show-preview-panel
  "Show the real-time code preview panel"
  []
  (swap! state* assoc :visible true)
  (generate-preview-code))

(defn hide-preview-panel
  "Hide the preview panel"
  []
  (swap! state* assoc :visible false))

(defn update-preview
  "Update preview with new design changes"
  [changes]
  (when (:visible @state*)
    (swap! state* assoc :is-generating true :error nil)
    ;; Trigger code regeneration
    (generate-preview-code)))

(defn copy-element-code
  "Copy code for selected element"
  [element-id]
  ;; Implementation for copying specific element code
  (js/console.log "Copying code for element:" element-id))

;; ============================================================================
;; Private Helper Functions
;; ============================================================================

(defn- change-framework
  [new-framework]
  (swap! state* assoc :framework new-framework)
  (generate-preview-code))

(defn- close-preview-panel
  []
  (hide-preview-panel))

(defn- select-file
  [file-path]
  (swap! state* assoc :selected-file file-path)
  (update-code-display file-path))

(defn- copy-current-code
  []
  (let [code (:generated-code @state*)]
    (when (seq code)
      (-> (js/navigator.clipboard.writeText code)
          (.then #(js/console.log "Code copied to clipboard"))
          (.catch #(js/console.log "Failed to copy code:" %))))))

(defn- download-project
  []
  ;; Implementation for downloading full project
  (js/console.log "Downloading project..."))

(defn- retry-generation
  []
  (generate-preview-code))

(defn- generate-preview-code
  "Generate preview code using the data-driven generator"
  []
  (when (:visible @state*)
    (swap! state* assoc :is-generating true :error nil)

    ;; Get current design from workspace
    (let [file-data (deref refs/file-data)
          current-page-id (deref refs/current-page-id)]

      (when (and file-data current-page-id)
        ;; Convert file data to KodaDesign format
        (let [design (convert-file-data-to-design file-data current-page-id)]
          ;; Call the preview generation (would integrate with RealtimePreviewManager)
          (generate-code-for-design design (:framework @state*)))))))

(defn- convert-file-data-to-design
  "Convert Penpot file data to KodaDesign format"
  [file-data page-id]
  ;; Implementation to convert Penpot's internal format to our design format
  (let [page (get-in file-data [:pages-index page-id])]
    {:id (:id page)
     :name (:name page)
     :version "1.0.0"
     :pages [page]
     :components []
     :tokens {:colors {} :typography {} :spacing {} :shadows {} :borders {} :breakpoints {}}
     :metadata {:created-at (js/Date.) :updated-at (js/Date.) :exported-at (js/Date.) :koda-version "1.0.0"}}))

(defn- generate-code-for-design
  "Generate code for the given design (placeholder for actual implementation)"
  [design framework]
  ;; This would call the actual DataDrivenGenerator
  ;; For now, simulate generation
  (js/setTimeout
   (fn []
     (let [sample-code (generate-sample-code framework)]
       (swap! state* assoc
              :generated-code sample-code
              :is-generating false
              :last-update (js/Date.)
              :error nil)))
   1000))

(defn- generate-sample-code
  "Generate sample code for preview (placeholder)"
  [framework]
  (case framework
    "react" "import React from 'react';

function App() {
  return (
    <div className=\"app\">
      <h1>Hello from Koda!</h1>
      <button onClick={() => alert('Clicked!')}>
        Click me
      </button>
    </div>
  );
}

export default App;"

    "vue" "<template>
  <div class=\"app\">
    <h1>Hello from Koda!</h1>
    <button @click=\"handleClick\">
      Click me
    </button>
  </div>
</template>

<script>
export default {
  name: 'App',
  methods: {
    handleClick() {
      alert('Clicked!');
    }
  }
}
</script>

<style scoped>
.app {
  text-align: center;
  margin-top: 50px;
}
</style>"

    "html-css" "<!DOCTYPE html>
<html lang=\"en\">
<head>
    <meta charset=\"UTF-8\">
    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">
    <title>Koda Generated App</title>
    <style>
        .app {
            text-align: center;
            margin-top: 50px;
        }
        button {
            padding: 10px 20px;
            background-color: #007AFF;
            color: white;
            border: none;
            border-radius: 5px;
            cursor: pointer;
        }
    </style>
</head>
<body>
    <div class=\"app\">
        <h1>Hello from Koda!</h1>
        <button onclick=\"alert('Clicked!')\">Click me</button>
    </div>
</body>
</html>"

    "react-native" "import React from 'react';
import { View, Text, TouchableOpacity, StyleSheet } from 'react-native';

function App() {
  const handlePress = () => {
    alert('Button pressed!');
  };

  return (
    <View style={styles.container}>
      <Text style={styles.title}>Hello from Koda!</Text>
      <TouchableOpacity style={styles.button} onPress={handlePress}>
        <Text style={styles.buttonText}>Click me</Text>
      </TouchableOpacity>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: '#ffffff',
  },
  title: {
    fontSize: 24,
    marginBottom: 20,
  },
  button: {
    backgroundColor: '#007AFF',
    paddingHorizontal: 20,
    paddingVertical: 10,
    borderRadius: 5,
  },
  buttonText: {
    color: 'white',
    fontSize: 16,
  },
});

export default App;"

    "flutter" "import 'package:flutter/material.dart';

void main() {
  runApp(MyApp());
}

class MyApp extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        body: Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Text(
                'Hello from Koda!',
                style: TextStyle(fontSize: 24),
              ),
              SizedBox(height: 20),
              ElevatedButton(
                onPressed: () {
                  // Handle button press
                },
                child: Text('Click me'),
              ),
            ],
          ),
        ),
      ),
    );
  }
}"

    ;; Default fallback
    "// Code generation not implemented for this framework yet"))

(defn- update-code-display
  "Update the displayed code for selected file"
  [file-path]
  ;; Implementation for showing different files
  (swap! state* assoc :selected-file file-path))

(defn- get-language-class
  "Get CSS class for syntax highlighting"
  [framework]
  (case framework
    "react" "language-javascript"
    "vue" "language-html"
    "html-css" "language-html"
    "react-native" "language-javascript"
    "flutter" "language-dart"
    "language-text"))

;; ============================================================================
;; Event Handlers
;; ============================================================================

;; Register the preview panel component
(defmethod app.main.ui.render/component :preview-panel
  [props]
  (mf/element preview-panel* props))
