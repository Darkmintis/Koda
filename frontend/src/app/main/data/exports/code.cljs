;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) KODA INC

(ns app.main.data.exports.code
  "Code generation API and events for Koda AI"
  (:require
   [app.common.data :as d]
   [app.common.schema :as sm]
   [app.main.data.event :as ev]
   [app.main.data.modal :as modal]
   [app.main.repo :as rp]
   [app.util.sse :as sse]
   [beicon.v2.core :as rx]
   [potok.v2.core :as ptk]))

;; Valid framework options
(def valid-frameworks
  #{:react :react-typescript :vue :vue-typescript :html-css :html-css-typescript})

;; Valid CSS framework options
(def valid-css-frameworks
  #{:tailwind :css-modules :scss :vanilla})

(def ^:private schema:code-generation-options
  [:map {:title "CodeGenerationOptions"}
   [:framework {:optional true} [:enum :react :react-typescript :vue :vue-typescript :html-css :html-css-typescript]]
   [:css-framework {:optional true} [:enum :tailwind :css-modules :scss :vanilla]]
   [:generate-tokens {:optional true} ::sm/boolean]
   [:generate-components {:optional true} ::sm/boolean]
   [:include-routing {:optional true} ::sm/boolean]
   [:include-interactions {:optional true} ::sm/boolean]
   [:responsive {:optional true} ::sm/boolean]
   [:accessibility {:optional true} ::sm/boolean]
   [:dark-mode {:optional true} ::sm/boolean]])

(defn show-code-generation-dialog
  "Show the code generation dialog for the current file/page"
  [{:keys [file-id page-id origin] :as params}]
  (ptk/reify ::show-code-generation-dialog
    ptk/WatchEvent
    (watch [_ state _]
      (rx/merge
       (rx/of (ev/event {::ev/name "open-code-generation-dialog"
                         ::ev/origin (or origin "workspace")
                         :file-id file-id
                         :page-id page-id}))
       (rx/of (modal/show {:type :code-generation
                           :file-id file-id
                           :page-id page-id}))))))

(defn generate-code
  "Generate code from design with the specified options"
  [{:keys [file-id page-id options] :as params}]
  (ptk/reify ::generate-code
    ptk/WatchEvent
    (watch [_ state _]
      (let [default-options {:framework :react-typescript
                             :css-framework :tailwind
                             :generate-tokens true
                             :generate-components true
                             :include-routing true
                             :include-interactions true
                             :responsive true
                             :accessibility true
                             :dark-mode false}
            merged-options (merge default-options options)]
        (rx/merge
         (rx/of (ev/event {::ev/name "generate-code"
                           ::ev/origin "code-generation-dialog"
                           :file-id file-id
                           :page-id page-id
                           :framework (:framework merged-options)
                           :css-framework (:css-framework merged-options)}))
         ;; Call the backend API for code generation
         (->> (rp/cmd! :generate-code {:file-id file-id
                                       :page-id page-id
                                       :options merged-options})
              (rx/map (fn [result]
                        {:type :code-generation-complete
                         :result result}))
              (rx/catch (fn [cause]
                          (let [error (ex-data cause)]
                            (rx/of {:type :code-generation-error
                                    :error error}))))))))))

(defn download-generated-code
  "Download the generated code as a zip file"
  [{:keys [generation-id filename] :as params}]
  (ptk/reify ::download-generated-code
    ptk/WatchEvent
    (watch [_ _ _]
      (->> (rp/cmd! :download-generated-code {:generation-id generation-id})
           (rx/map (fn [uri]
                     {:type :download-ready
                      :uri uri
                      :filename (or filename "koda-generated-code.zip")}))
           (rx/catch (fn [cause]
                       (let [error (ex-data cause)]
                         (rx/of {:type :download-error
                                 :error error}))))))))

(defn export-design-json
  "Export the current design as JSON for code generation"
  [{:keys [file-id page-id] :as params}]
  (ptk/reify ::export-design-json
    ptk/WatchEvent
    (watch [_ state _]
      (let [file (get-in state [:workspace-file])
            page (get-in state [:workspace-data :pages-index page-id])]
        (rx/of {:type :design-json-ready
                :data {:file file
                       :page page
                       :objects (get page :objects)}})))))
