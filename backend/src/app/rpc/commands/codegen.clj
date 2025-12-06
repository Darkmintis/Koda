;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) KODA INC

(ns app.rpc.commands.codegen
  "Code generation API for Koda AI - generates frontend code from designs"
  (:require
   [app.common.data :as d]
   [app.common.exceptions :as ex]
   [app.common.files.helpers :as cfh]
   [app.common.logging :as l]
   [app.common.schema :as sm]
   [app.common.time :as ct]
   [app.common.transit :as t]
   [app.common.uri :as u]
   [app.config :as cf]
   [app.db :as db]
   [app.http.sse :as sse]
   [app.loggers.audit :as-alias audit]
   [app.rpc :as-alias rpc]
   [app.rpc.commands.files :as files]
   [app.rpc.doc :as-alias doc]
   [app.storage :as sto]
   [app.storage.tmp :as tmp]
   [app.util.services :as sv]
   [clojure.java.io :as io]
   [clojure.java.shell :refer [sh]]
   [datoteka.fs :as fs])
  (:import
   [java.io ByteArrayOutputStream]
   [java.util.zip ZipOutputStream ZipEntry]))

(set! *warn-on-reflection* true)

;; --- Schema definitions

(def ^:private schema:code-generation-options
  [:map {:title "CodeGenerationOptions"}
   [:framework {:optional true} 
    [:enum :react :react-typescript :vue :vue-typescript :html-css :html-css-typescript]]
   [:css-framework {:optional true} 
    [:enum :tailwind :css-modules :scss :vanilla]]
   [:generate-tokens {:optional true} ::sm/boolean]
   [:generate-components {:optional true} ::sm/boolean]
   [:include-routing {:optional true} ::sm/boolean]
   [:include-interactions {:optional true} ::sm/boolean]
   [:responsive {:optional true} ::sm/boolean]
   [:accessibility {:optional true} ::sm/boolean]
   [:dark-mode {:optional true} ::sm/boolean]])

(def ^:private schema:generate-code
  [:map {:title "generate-code"}
   [:file-id ::sm/uuid]
   [:page-id {:optional true} ::sm/uuid]
   [:options {:optional true} schema:code-generation-options]])

;; --- Helper functions

(defn- get-file-data
  "Get file data with all objects for code generation"
  [pool file-id]
  (let [file (db/get pool :file {:id file-id})]
    (when-not file
      (ex/raise :type :not-found
                :code :file-not-found
                :hint "file not found"))
    file))

(defn- get-page-data
  "Get page data from file"
  [pool file-id page-id]
  (let [file (get-file-data pool file-id)
        data (:data file)]
    (if page-id
      (get-in data [:pages-index page-id])
      ;; Return first page if no page-id specified
      (let [first-page-id (first (:pages data))]
        (get-in data [:pages-index first-page-id])))))

(defn- extract-design-structure
  "Extract design structure for code generation"
  [page-data]
  (let [objects (:objects page-data)
        root-frame-id (:id (first (cfh/get-immediate-children objects (java.util.UUID/fromString "00000000-0000-0000-0000-000000000000"))))]
    {:name (:name page-data)
     :id (:id page-data)
     :objects objects
     :root-frame-id root-frame-id}))

(defn- extract-design-tokens
  "Extract design tokens (colors, typography, spacing) from design"
  [objects]
  (let [colors (atom #{})
        fonts (atom #{})
        spacings (atom #{})]
    ;; Walk through all objects and extract design tokens
    (doseq [[_ obj] objects]
      ;; Extract fill colors
      (when-let [fills (:fills obj)]
        (doseq [fill fills]
          (when (:fill-color fill)
            (swap! colors conj {:hex (:fill-color fill)
                                :opacity (:fill-opacity fill 1)}))))
      ;; Extract stroke colors
      (when-let [strokes (:strokes obj)]
        (doseq [stroke strokes]
          (when (:stroke-color stroke)
            (swap! colors conj {:hex (:stroke-color stroke)
                                :opacity (:stroke-opacity stroke 1)}))))
      ;; Extract typography
      (when (= :text (:type obj))
        (when-let [content (:content obj)]
          (doseq [paragraph (:children content)]
            (doseq [text-node (:children paragraph)]
              (when-let [font-id (:font-id text-node)]
                (swap! fonts conj {:font-id font-id
                                   :font-family (:font-family text-node)
                                   :font-size (:font-size text-node)
                                   :font-weight (:font-weight text-node)})))))))
    {:colors @colors
     :fonts @fonts
     :spacings @spacings}))

(defn- shape->component
  "Convert a shape to a component definition"
  [shape depth]
  (let [base {:id (:id shape)
              :name (:name shape)
              :type (:type shape)
              :x (:x shape)
              :y (:y shape)
              :width (:width shape)
              :height (:height shape)
              :depth depth}]
    (cond-> base
      (:fills shape) (assoc :fills (:fills shape))
      (:strokes shape) (assoc :strokes (:strokes shape))
      (:rx shape) (assoc :border-radius (:rx shape))
      (:opacity shape) (assoc :opacity (:opacity shape))
      (= :text (:type shape)) (assoc :content (:content shape))
      (:constraints-h shape) (assoc :constraints-h (:constraints-h shape))
      (:constraints-v shape) (assoc :constraints-v (:constraints-v shape))
      (:layout shape) (assoc :layout (:layout shape))
      (:layout-flex-dir shape) (assoc :flex-direction (:layout-flex-dir shape))
      (:layout-gap shape) (assoc :gap (:layout-gap shape))
      (:layout-padding shape) (assoc :padding (:layout-padding shape)))))

(defn- build-component-tree
  "Build hierarchical component tree from flat objects map"
  [objects parent-id depth]
  (let [children (cfh/get-immediate-children objects parent-id)]
    (mapv (fn [child]
            (let [component (shape->component child depth)]
              (if (seq (cfh/get-immediate-children objects (:id child)))
                (assoc component :children (build-component-tree objects (:id child) (inc depth)))
                component)))
          children)))

(defn- generate-tailwind-classes
  "Generate Tailwind CSS classes for a shape"
  [shape]
  (let [classes []
        ;; Width and height
        classes (cond-> classes
                  (:width shape) (conj (str "w-[" (int (:width shape)) "px]"))
                  (:height shape) (conj (str "h-[" (int (:height shape)) "px]")))
        ;; Border radius
        classes (cond-> classes
                  (:border-radius shape) (conj (str "rounded-[" (int (:border-radius shape)) "px]")))
        ;; Flexbox
        classes (cond-> classes
                  (:layout shape) (conj "flex")
                  (= :column (:flex-direction shape)) (conj "flex-col")
                  (= :row (:flex-direction shape)) (conj "flex-row"))
        ;; Gap
        classes (cond-> classes
                  (:gap shape) (conj (str "gap-[" (:gap shape) "px]")))]
    (clojure.string/join " " classes)))

(defn- generate-react-component
  "Generate React component code from component definition"
  [component options indent-level]
  (let [indent (apply str (repeat indent-level "  "))
        classes (generate-tailwind-classes component)
        tag-name (case (:type component)
                   :text "p"
                   :frame "div"
                   :rect "div"
                   :circle "div"
                   :image "img"
                   "div")
        content (when (= :text (:type component))
                  (-> component :content :children first :children first :text))
        children-code (when (:children component)
                        (mapv #(generate-react-component % options (inc indent-level)) 
                              (:children component)))]
    (str indent "<" tag-name 
         (when (seq classes) (str " className=\"" classes "\""))
         ">\n"
         (when content (str indent "  " content "\n"))
         (when children-code (clojure.string/join "" children-code))
         indent "</" tag-name ">\n")))

(defn- generate-design-tokens-file
  "Generate design tokens TypeScript file"
  [tokens options]
  (let [colors (:colors tokens)
        color-entries (map-indexed 
                       (fn [idx {:keys [hex opacity]}]
                         (str "  '" (or hex "color") "-" idx "': '" hex "',"))
                       colors)]
    (str "// Design tokens generated by Koda AI\n"
         "// Do not edit manually - regenerate from your design\n\n"
         "export const colors = {\n"
         (clojure.string/join "\n" color-entries)
         "\n} as const;\n\n"
         "export const spacing = {\n"
         "  'xs': '4px',\n"
         "  'sm': '8px',\n"
         "  'md': '16px',\n"
         "  'lg': '24px',\n"
         "  'xl': '32px',\n"
         "  '2xl': '48px',\n"
         "} as const;\n\n"
         "export type ColorToken = keyof typeof colors;\n"
         "export type SpacingToken = keyof typeof spacing;\n")))

(defn- generate-component-file
  "Generate a React component file"
  [component options]
  (let [component-name (-> (:name component)
                           (clojure.string/replace #"[^a-zA-Z0-9]" "")
                           (clojure.string/capitalize))
        tsx? (or (= :react-typescript (:framework options))
                 (= :vue-typescript (:framework options))
                 (= :html-css-typescript (:framework options)))]
    (str "// Generated by Koda AI\n"
         "import React from 'react';\n\n"
         (if tsx?
           (str "interface " component-name "Props {\n"
                "  className?: string;\n"
                "}\n\n"
                "export function " component-name "({ className }: " component-name "Props) {\n")
           (str "export function " component-name "({ className }) {\n"))
         "  return (\n"
         (generate-react-component component options 2)
         "  );\n"
         "}\n")))

(defn- generate-page-file
  "Generate a page component file"
  [page-name components options]
  (let [page-component-name (-> page-name
                                (clojure.string/replace #"[^a-zA-Z0-9]" "")
                                (clojure.string/capitalize)
                                (str "Page"))
        tsx? (or (= :react-typescript (:framework options))
                 (= :vue-typescript (:framework options)))]
    (str "// Generated by Koda AI\n"
         "// Page: " page-name "\n"
         "import React from 'react';\n\n"
         (if tsx?
           (str "export function " page-component-name "(): JSX.Element {\n")
           (str "export function " page-component-name "() {\n"))
         "  return (\n"
         "    <div className=\"min-h-screen\">\n"
         (clojure.string/join "" 
                              (map #(generate-react-component % options 3) components))
         "    </div>\n"
         "  );\n"
         "}\n")))

(defn- create-zip-archive
  "Create a ZIP archive from file map"
  [files]
  (let [baos (ByteArrayOutputStream.)]
    (with-open [zos (ZipOutputStream. baos)]
      (doseq [[path content] files]
        (.putNextEntry zos (ZipEntry. path))
        (.write zos (.getBytes ^String content "UTF-8"))
        (.closeEntry zos)))
    (.toByteArray baos)))

(defn- generate-code-files
  "Generate all code files for the design"
  [design-data options]
  (let [page-name (:name design-data)
        objects (:objects design-data)
        root-id (java.util.UUID/fromString "00000000-0000-0000-0000-000000000000")
        
        ;; Build component tree
        components (build-component-tree objects root-id 0)
        
        ;; Extract design tokens
        tokens (extract-design-tokens objects)
        
        ;; Generate files
        files {"src/tokens/index.ts" (generate-design-tokens-file tokens options)
               (str "src/pages/" (clojure.string/replace page-name #"[^a-zA-Z0-9]" "") ".tsx")
               (generate-page-file page-name components options)
               "package.json" (str "{\n"
                                   "  \"name\": \"koda-generated\",\n"
                                   "  \"version\": \"1.0.0\",\n"
                                   "  \"private\": true,\n"
                                   "  \"scripts\": {\n"
                                   "    \"dev\": \"vite\",\n"
                                   "    \"build\": \"vite build\"\n"
                                   "  },\n"
                                   "  \"dependencies\": {\n"
                                   "    \"react\": \"^18.2.0\",\n"
                                   "    \"react-dom\": \"^18.2.0\"\n"
                                   "  },\n"
                                   "  \"devDependencies\": {\n"
                                   "    \"@types/react\": \"^18.2.0\",\n"
                                   "    \"@vitejs/plugin-react\": \"^4.2.0\",\n"
                                   "    \"tailwindcss\": \"^3.4.0\",\n"
                                   "    \"typescript\": \"^5.3.0\",\n"
                                   "    \"vite\": \"^5.0.0\"\n"
                                   "  }\n"
                                   "}\n")
               "README.md" (str "# Generated by Koda AI\n\n"
                                "This project was generated from your Koda design.\n\n"
                                "## Getting Started\n\n"
                                "```bash\n"
                                "npm install\n"
                                "npm run dev\n"
                                "```\n\n"
                                "## Project Structure\n\n"
                                "- `src/tokens/` - Design tokens (colors, spacing, typography)\n"
                                "- `src/pages/` - Page components\n"
                                "- `src/components/` - Reusable UI components\n")}]
    
    ;; Generate individual component files
    (reduce (fn [acc component]
              (let [comp-name (-> (:name component)
                                  (clojure.string/replace #"[^a-zA-Z0-9]" "")
                                  (clojure.string/capitalize))]
                (assoc acc 
                       (str "src/components/" comp-name ".tsx")
                       (generate-component-file component options))))
            files
            (filter #(#{:frame} (:type %)) components))))

;; --- Public API

(defn- do-generate-code
  [{:keys [::db/pool ::sto/storage] :as cfg} 
   {:keys [file-id page-id options] :as params}]
  (let [;; Default options
        default-options {:framework :react-typescript
                         :css-framework :tailwind
                         :generate-tokens true
                         :generate-components true
                         :include-routing true
                         :include-interactions true
                         :responsive true
                         :accessibility true
                         :dark-mode false}
        merged-options (merge default-options options)
        
        ;; Get design data
        page-data (get-page-data pool file-id page-id)
        design-data (extract-design-structure page-data)
        
        ;; Generate code files
        files (generate-code-files design-data merged-options)
        
        ;; Create ZIP archive
        zip-bytes (create-zip-archive files)
        
        ;; Store in temporary storage
        output-file (tmp/tempfile* :suffix ".zip")
        _ (io/copy zip-bytes output-file)
        
        object (sto/put-object! storage
                                {::sto/content (sto/content output-file)
                                 ::sto/touched-at (ct/in-future {:minutes 60})
                                 :content-type "application/zip"
                                 :bucket "tempfile"})]
    
    ;; Clean up temp file
    (fs/delete output-file)
    
    ;; Return download URL
    {:download-url (str (cf/get :public-uri) "/assets/by-id/" (:id object))
     :filename "koda-generated-code.zip"
     :component-count (count (filter #(#{:frame} (:type %)) 
                                     (vals (:objects design-data))))
     :page-count 1
     :route-count 0}))

(sv/defmethod ::generate-code
  "Generate frontend code from a Koda design file."
  {::doc/added "2.0"
   ::sm/params schema:generate-code}
  [{:keys [::db/pool] :as cfg} {:keys [::rpc/profile-id file-id] :as params}]
  (files/check-read-permissions! pool profile-id file-id)
  (sse/response (partial do-generate-code cfg params)))

;; --- Command: download-generated-code

(def ^:private schema:download-generated-code
  [:map {:title "download-generated-code"}
   [:generation-id ::sm/uuid]])

(sv/defmethod ::download-generated-code
  "Download previously generated code."
  {::doc/added "2.0"
   ::sm/params schema:download-generated-code}
  [{:keys [::sto/storage] :as cfg} {:keys [::rpc/profile-id generation-id] :as params}]
  ;; Return the download URL for the generation
  {:download-url (str (cf/get :public-uri) "/assets/by-id/" generation-id)
   :filename "koda-generated-code.zip"})
