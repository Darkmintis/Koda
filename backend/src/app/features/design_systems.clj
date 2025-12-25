;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) KODA INC

(ns app.features.design-systems
  "Enterprise design systems management"
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
;; Design System Data Structures
;; ============================================================================

(defrecord DesignSystem [id name description owner-id team-id themes tokens components libraries metadata])
(defrecord DesignToken [id name type value category references computed?])
(defrecord TokenTheme [id name description tokens overrides])
(defrecord ComponentLibrary [id name version components dependencies])

;; ============================================================================
;; Design System Management
;; ============================================================================

(defn create-design-system
  "Create a new design system"
  [db owner-id team-id name description]
  (let [ds-id (uuid/next)
        design-system (->DesignSystem
                       ds-id
                       name
                       description
                       owner-id
                       team-id
                       [] ; themes
                       {} ; tokens
                       [] ; components
                       [] ; libraries
                       {:created-at (ct/now)
                        :updated-at (ct/now)
                        :version "1.0.0"})]

    ;; Store in database
    (db/insert! db :design-system design-system)

    ;; Create default theme
    (create-theme db ds-id "Default" "Default theme" {})

    design-system))

(defn update-design-system
  "Update design system metadata"
  [db ds-id updates]
  (db/update! db :design-system
              {:id ds-id}
              (merge updates {:updated-at (ct/now)})))

(defn delete-design-system
  "Delete a design system"
  [db ds-id]
  ;; Soft delete - mark as deleted
  (db/update! db :design-system
              {:id ds-id}
              {:deleted-at (ct/now)
               :updated-at (ct/now)}))

;; ============================================================================
;; Token Management
;; ============================================================================

(defn create-token
  "Create a new design token"
  [db ds-id name type value category & {:keys [references computed?]}]
  (let [token-id (uuid/next)
        token (->DesignToken
               token-id
               name
               type
               value
               category
               (or references [])
               (or computed? false))]

    ;; Add to design system
    (db/execute-one! db
      ["UPDATE design_system
        SET tokens = jsonb_set(tokens, ?, ?)
        WHERE id = ?"
       (str "{" (name->json-key name) "}")
       (t/encode token)
       ds-id])

    token))

(defn update-token
  "Update a design token"
  [db ds-id token-name updates]
  (let [json-path (str "{" (name->json-key token-name) "}")]
    (db/execute-one! db
      ["UPDATE design_system
        SET tokens = jsonb_set(tokens, ?, jsonb_set(tokens->?, ?, true))
        WHERE id = ?"
       json-path
       json-path
       (t/encode updates)
       ds-id])))

(defn compute-token-value
  "Compute dynamic token value based on references"
  [token all-tokens]
  (if (:computed? token)
    ;; Evaluate expression (simplified - would use safe evaluator)
    (let [expression (:value token)]
      ;; Replace token references with actual values
      (reduce (fn [expr [ref-name ref-token]]
                (str/replace expr
                            (str "$" ref-name)
                            (str (:value ref-token))))
              expression
              all-tokens))
    (:value token)))

(defn validate-token-dependencies
  "Validate token dependency graph for circular references"
  [tokens]
  (let [token-map (into {} (map (juxt :name identity) tokens))
        visited (atom #{})
        recursion-stack (atom #{})]

    (letfn [(has-circular-ref? [token-name]
              (when (contains? @recursion-stack token-name)
                true) ; Circular reference detected
              (when (not (contains? @visited token-name))
                (swap! visited conj token-name)
                (swap! recursion-stack conj token-name)

                (let [token (get token-map token-name)]
                  (if (and token (:computed? token))
                    (some has-circular-ref? (:references token))
                    false))))]

      (some has-circular-ref? (keys token-map)))))

;; ============================================================================
;; Theme Management
;; ============================================================================

(defn create-theme
  "Create a new theme for a design system"
  [db ds-id name description token-overrides]
  (let [theme-id (uuid/next)
        theme (->TokenTheme
               theme-id
               name
               description
               {} ; base tokens
               token-overrides)]

    ;; Add theme to design system
    (db/execute-one! db
      ["UPDATE design_system
        SET themes = array_append(themes, ?)
        WHERE id = ?"
       (t/encode theme)
       ds-id])

    theme))

(defn update-theme
  "Update theme token overrides"
  [db ds-id theme-id token-overrides]
  (db/execute-one! db
    ["UPDATE design_system
      SET themes = (
        SELECT array_agg(
          CASE WHEN (value->>'id') = ?
               THEN jsonb_set(value, '{overrides}', ?)
               ELSE value END
        )
        FROM unnest(themes) AS value
      )
      WHERE id = ?"
     (str theme-id)
     (t/encode token-overrides)
     ds-id]))

(defn apply-theme
  "Apply theme to get final token values"
  [base-tokens theme]
  (merge base-tokens (:overrides theme)))

;; ============================================================================
;; Component Library Management
;; ============================================================================

(defn create-component-library
  "Create a new component library"
  [db ds-id name version components]
  (let [lib-id (uuid/next)
        library (->ComponentLibrary
                 lib-id
                 name
                 version
                 components
                 [] ; dependencies
                 {:created-at (ct/now)
                  :updated-at (ct/now)})]

    ;; Add to design system
    (db/execute-one! db
      ["UPDATE design_system
        SET libraries = array_append(libraries, ?)
        WHERE id = ?"
       (t/encode library)
       ds-id])

    library))

(defn publish-library
  "Publish component library to NPM"
  [db lib-id registry token]
  ;; Implementation would publish to NPM registry
  (log/info :msg "Publishing library to NPM"
           :lib-id lib-id
           :registry registry))

(defn import-library
  "Import component library from NPM"
  [db ds-id package-name version]
  ;; Implementation would fetch from NPM and import
  (log/info :msg "Importing library from NPM"
           :package package-name
           :version version))

;; ============================================================================
;; Design System Analytics
;; ============================================================================

(defn get-usage-analytics
  "Get design system usage analytics"
  [db ds-id start-date end-date]
  (let [usage-data (db/execute-one! db
    ["SELECT
       COUNT(DISTINCT f.id) as files_using,
       COUNT(DISTINCT t.id) as tokens_used,
       COUNT(DISTINCT c.id) as components_used,
       AVG(extract(epoch from (f.updated_at - f.created_at))) as avg_session_time
     FROM design_system ds
     LEFT JOIN files f ON f.design_system_id = ds.id
     LEFT JOIN tokens t ON t.design_system_id = ds.id
     LEFT JOIN components c ON c.design_system_id = ds.id
     WHERE ds.id = ?
       AND f.updated_at BETWEEN ? AND ?"
     ds-id start-date end-date])]

    {:files-using (:files_using usage-data 0)
     :tokens-used (:tokens_used usage-data 0)
     :components-used (:components_used usage-data 0)
     :avg-session-time (:avg_session_time usage-data 0)
     :adoption-rate (calculate-adoption-rate usage-data)}))

(defn calculate-adoption-rate
  "Calculate team adoption rate"
  [usage-data]
  ;; Simplified calculation
  (let [total-files (:files_using usage-data 0)
        active-files (:tokens_used usage-data 0)] ; Approximation
    (if (> total-files 0)
      (/ active-files total-files)
      0)))

(defn get-token-usage-stats
  "Get detailed token usage statistics"
  [db ds-id]
  (db/execute! db
    ["SELECT
       t.category,
       t.type,
       COUNT(*) as count,
       AVG(LENGTH(t.value::text)) as avg_value_length
     FROM design_system ds
     JOIN tokens t ON t.design_system_id = ds.id
     WHERE ds.id = ?
     GROUP BY t.category, t.type"
     ds-id]))

(defn get-compliance-report
  "Generate design system compliance report"
  [db ds-id team-id]
  (let [violations (db/execute! db
    ["SELECT
       f.name as file_name,
       v.rule_name,
       v.severity,
       v.description
     FROM design_system ds
     JOIN files f ON f.design_system_id = ds.id
     JOIN violations v ON v.file_id = f.id
     WHERE ds.id = ? AND f.team_id = ?"
     ds-id team-id])]

    {:total-violations (count violations)
     :by-severity (group-by :severity violations)
     :compliance-score (calculate-compliance-score violations)}))

(defn calculate-compliance-score
  "Calculate compliance score (0-100)"
  [violations]
  (let [total-files 100 ; Would be actual count
        violations-by-severity (group-by :severity violations)
        critical-count (count (:critical violations-by-severity []))
        high-count (count (:high violations-by-severity []))]

    ;; Scoring algorithm
    (max 0 (- 100 (* 10 critical-count) (* 5 high-count)))))

;; ============================================================================
;; Multi-Brand Support
;; ============================================================================

(defn create-brand
  "Create a new brand configuration"
  [db ds-id brand-name brand-config]
  (db/execute-one! db
    ["UPDATE design_system
      SET brands = jsonb_set(COALESCE(brands, '{}'), ?, ?)
      WHERE id = ?"
     (str "{" brand-name "}")
     (t/encode brand-config)
     ds-id]))

(defn apply-brand-overrides
  "Apply brand-specific overrides to tokens"
  [base-tokens brand-overrides]
  (merge base-tokens brand-overrides))

(defn get-brand-specific-tokens
  "Get tokens for a specific brand"
  [db ds-id brand-name]
  (let [ds (db/get db :design-system {:id ds-id})
        brand-overrides (get-in ds [:brands brand-name] {})]
    (apply-brand-overrides (:tokens ds) brand-overrides)))

;; ============================================================================
;; Utility Functions
;; ============================================================================

(defn name->json-key
  "Convert token name to JSON key"
  [name]
  (str/replace name #"[^a-zA-Z0-9_]" "_"))

(defn validate-design-system
  "Validate design system integrity"
  [db ds-id]
  (let [ds (db/get db :design-system {:id ds-id})]
    (cond
      (nil? ds) {:valid false :error "Design system not found"}
      (:deleted-at ds) {:valid false :error "Design system deleted"}
      :else {:valid true :warnings (check-warnings ds)})))

(defn check-warnings
  "Check for design system warnings"
  [ds]
  (let [warnings []]
    (when (> (count (:tokens ds)) 1000)
      (conj warnings "Large number of tokens may impact performance"))
    (when (> (count (:components ds)) 500)
      (conj warnings "Large number of components may impact performance"))
    warnings))

(defn export-design-system
  "Export design system to JSON"
  [db ds-id]
  (let [ds (db/get db :design-system {:id ds-id})]
    (when ds
      (t/encode (dissoc ds :internal-fields)))))

(defn import-design-system
  "Import design system from JSON"
  [db owner-id team-id import-data]
  (let [ds-id (uuid/next)
        design-system (merge import-data
                            {:id ds-id
                             :owner-id owner-id
                             :team-id team-id
                             :imported-at (ct/now)})]
    (db/insert! db :design-system design-system)
    design-system))
