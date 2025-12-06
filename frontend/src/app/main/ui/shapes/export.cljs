;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) KALEIDOS INC

(ns app.main.ui.shapes.export
  "Components that generates koda specific svg nodes with
  exportation data. This xml nodes serves mainly to enable
  importation."
  (:require
   [app.common.data :as d]
   [app.common.data.macros :as dm]
   [app.common.geom.shapes :as gsh]
   [app.common.json :as json]
   [app.common.svg :as csvg]
   [app.main.ui.context :as muc]
   [app.util.object :as obj]
   [cuerdas.core :as str]
   [rumext.v2 :as mf]))

(def ^:private internal-counter (atom 0))

(def include-metadata-ctx
  (mf/create-context false))

(mf/defc render-xml
  [{{:keys [tag attrs content] :as node} :xml}]

  (cond
    (map? node)
    (let [props (-> (csvg/attrs->props attrs)
                    (json/->js :key-fn name))]
      [:> (d/name tag) props
       (for [child content]
         [:& render-xml {:xml child :key (swap! internal-counter inc)}])])

    (string? node)
    node

    :else
    nil))

(defn bool->str [val]
  (when (some? val) (str val)))

(defn touched->str [val]
  (str/join " " (map str val)))

(defn add-factory [shape]
  (fn add!
    ([props attr]
     (add! props attr str))

    ([props attr trfn]
     (let [val (get shape attr)
           val (if (keyword? val) (d/name val) val)
           ns-attr (-> (str "koda:" (-> attr d/name))
                       (str/strip-suffix "?"))]
       (cond-> props
         (some? val)
         (obj/set! ns-attr (trfn val)))))))

(defn add-data
  "Adds as metadata properties that we cannot deduce from the exported SVG"
  [props shape]
  (let [add! (add-factory shape)
        frame? (= :frame (:type shape))
        group? (= :group (:type shape))
        rect?  (= :rect (:type shape))
        image? (= :image (:type shape))
        text?  (= :text (:type shape))
        path?  (= :path (:type shape))
        mask?  (and group? (:masked-group shape))
        bool?  (= :bool (:type shape))
        center (gsh/shape->center shape)]
    (-> props
        (add! :name)
        (add! :blocked)
        (add! :hidden)
        (add! :type)
        (add! :stroke-style)
        (add! :stroke-alignment)
        (add! :hide-fill-on-export)
        (add! :transform)
        (add! :transform-inverse)
        (add! :flip-x)
        (add! :flip-y)
        (add! :proportion)
        (add! :proportion-lock)
        (add! :rotation)
        (obj/set! "koda:center-x" (-> center :x str))
        (obj/set! "koda:center-y" (-> center :y str))

        ;; Constraints
        (add! :constraints-h)
        (add! :constraints-v)
        (add! :fixed-scroll)

        (cond-> frame?
          (-> (add! :show-content)
              (add! :hide-in-viewer)))

        (cond-> (and frame? (:use-for-thumbnail shape))
          (add! :use-for-thumbnail))

        (cond-> (and (or rect? image? frame?) (some? (:r1 shape)))
          (-> (add! :r1)
              (add! :r2)
              (add! :r3)
              (add! :r4)))

        (cond-> path?
          (-> (add! :stroke-cap-start)
              (add! :stroke-cap-end)))

        (cond-> text?
          (-> (add! :x)
              (add! :y)
              (add! :width)
              (add! :height)
              (add! :grow-type)
              (add! :content json/encode)
              (add! :position-data json/encode)))

        (cond-> mask?
          (obj/set! "koda:masked-group" "true"))

        (cond-> bool?
          (add! :bool-type)))))

(defn add-library-refs [props shape]
  (let [add! (add-factory shape)]
    (-> props
        (add! :fill-color-ref-id)
        (add! :fill-color-ref-file)
        (add! :stroke-color-ref-id)
        (add! :stroke-color-ref-file)
        (add! :typography-ref-id)
        (add! :typography-ref-file)
        (add! :component-file)
        (add! :component-id)
        (add! :component-root)
        (add! :main-instance)
        (add! :shape-ref)
        (add! :touched touched->str))))

(defn prefix-keys [m]
  (letfn [(prefix-entry [[k v]]
            [(str "koda:" (d/name k)) v])]
    (into {} (map prefix-entry) m)))

(defn- export-grid-data [{:keys [grids]}]
  (when (d/not-empty? grids)
    (mf/html
     [:> "koda:grids" #js {}
      (for [{:keys [type display params]} grids]
        (let [props (->> (dissoc params :color)
                         (prefix-keys)
                         (clj->js))]
          [:> "koda:grid"
           (-> props
               (obj/set! "koda:color" (get-in params [:color :color]))
               (obj/set! "koda:opacity" (get-in params [:color :opacity]))
               (obj/set! "koda:type" (d/name type))
               (cond-> (some? display)
                 (obj/set! "koda:display" (str display))))]))])))

(mf/defc export-flows
  [{:keys [flows]}]
  [:> "koda:flows" #js {}
   (for [{:keys [id name starting-frame]} (vals flows)]
     [:> "koda:flow" #js {:id id
                            :key id
                            :name name
                            :starting-frame starting-frame}])])

(mf/defc export-guides
  [{:keys [guides]}]
  [:> "koda:guides" #js {}
   (for [{:keys [position frame-id axis]} (vals guides)]
     [:> "koda:guide" #js {:position position
                             :frame-id frame-id
                             :axis (d/name axis)}])])

(mf/defc export-page
  {::mf/props :obj}
  [{:keys [page]}]
  (let [id     (get page :id)
        grids  (get page :grids)
        flows  (get page :flows)
        guides (get page :guides)]
    [:> "koda:page" #js {:id id}
     (when (d/not-empty? grids)
       (let [parse-grid (fn [[type params]] {:type type :params params})
             grids (mapv parse-grid grids)]
         [:& export-grid-data {:grids grids}]))

     (when (d/not-empty? flows)
       [:& export-flows {:flows flows}])

     (when (d/not-empty? guides)
       [:& export-guides {:guides guides}])]))

(defn- export-shadow-data [{:keys [shadow]}]
  (mf/html
   (for [{:keys [style hidden color offset-x offset-y blur spread]} shadow]
     [:> "koda:shadow"
      #js {:koda:shadow-type (d/name style)
           :key (swap! internal-counter inc)
           :koda:hidden (str hidden)
           :koda:color (str (:color color))
           :koda:opacity (str (:opacity color))
           :koda:offset-x (str offset-x)
           :koda:offset-y (str offset-y)
           :koda:blur (str blur)
           :koda:spread (str spread)}])))

(defn- export-blur-data [{:keys [blur]}]
  (when-let [{:keys [type hidden value]} blur]
    (mf/html
     [:> "koda:blur"
      #js {:koda:blur-type (d/name type)
           :koda:hidden    (str hidden)
           :koda:value     (str value)}])))

(defn export-exports-data [{:keys [exports]}]
  (mf/html
   (for [{:keys [scale suffix type]} exports]
     [:> "koda:export"
      #js {:koda:type   (d/name type)
           :key (swap! internal-counter inc)
           :koda:suffix suffix
           :koda:scale  (str scale)}])))

(defn str->style
  [style-str]
  (if (string? style-str)
    (->> (str/split style-str ";")
         (map str/trim)
         (map #(str/split % ":"))
         (group-by first)
         (map (fn [[key val]]
                (vector (keyword key) (second (first val)))))
         (into {}))
    style-str))

(defn style->str
  [style]
  (->> style
       (map (fn [[key val]] (str (d/name key) ":" val)))
       (str/join "; ")))

(defn- export-svg-data [shape]
  (mf/html
   [:*
    (when (contains? shape :svg-attrs)
      (let [svg-transform (get shape :svg-transform)
            svg-attrs     (->> shape :svg-attrs keys (mapv (comp d/name str/kebab)) (str/join ","))
            svg-defs      (->> shape :svg-defs keys (mapv d/name) (str/join ","))]
        [:> "koda:svg-import"
         #js {:koda:svg-attrs          (when-not (empty? svg-attrs) svg-attrs)
              ;; Style and filter are special properties so we need to save it otherwise will be indistingishible from
              ;; standard properties
              :koda:svg-style          (when (contains? (:svg-attrs shape) :style) (style->str (get-in shape [:svg-attrs :style])))
              :koda:svg-filter         (when (contains? (:svg-attrs shape) :filter) (get-in shape [:svg-attrs :filter]))
              :koda:svg-defs           (when-not (empty? svg-defs) svg-defs)
              :koda:svg-transform      (when svg-transform (str svg-transform))
              :koda:svg-viewbox-x      (get-in shape [:svg-viewbox :x])
              :koda:svg-viewbox-y      (get-in shape [:svg-viewbox :y])
              :koda:svg-viewbox-width  (get-in shape [:svg-viewbox :width])
              :koda:svg-viewbox-height (get-in shape [:svg-viewbox :height])}
         (for [[def-id def-xml] (:svg-defs shape)]
           [:> "koda:svg-def" #js {:def-id def-id
                                     :key (swap! internal-counter inc)}
            [:& render-xml {:xml def-xml}]])]))

    (when (= (:type shape) :svg-raw)
      (let [shape (-> shape (d/update-in-when [:content :attrs :style] str->style))
            props
            (-> (obj/create)
                (obj/set! "koda:x" (:x shape))
                (obj/set! "koda:y" (:y shape))
                (obj/set! "koda:width" (:width shape))
                (obj/set! "koda:height" (:height shape))
                (obj/set! "koda:tag" (-> (get-in shape [:content :tag]) d/name))
                (obj/merge! (-> (get-in shape [:content :attrs])
                                (clj->js))))]
        [:> "koda:svg-content" props
         (for [leaf (->> shape :content :content (filter string?))]
           [:> "koda:svg-child" {:key (swap! internal-counter inc)} leaf])]))]))


(defn- export-fills-data [{:keys [fills]}]
  (when-let [fills     (seq fills)]
    (let [render-id (mf/use-ctx muc/render-id)]
      (mf/html
       [:> "koda:fills" #js {}
        (for [[index fill] (d/enumerate fills)]
          (let [fill-image-id (dm/str "fill-image-" render-id "-" index)]
            [:> "koda:fill"
             #js {:koda:fill-color          (cond
                                                (some? (:fill-color-gradient fill))
                                                (str/format "url(#%s)" (str "fill-color-gradient-" render-id "-" index))

                                                :else
                                                (d/name (:fill-color fill)))
                  :key                        (swap! internal-counter inc)

                  :koda:fill-image-id       (when (:fill-image fill) fill-image-id)
                  :koda:fill-color-ref-file (d/name (:fill-color-ref-file fill))
                  :koda:fill-color-ref-id   (d/name (:fill-color-ref-id fill))
                  :koda:fill-opacity        (d/name (:fill-opacity fill))}]))]))))

(defn- export-strokes-data [{:keys [strokes]}]
  (when-let [strokes (seq strokes)]
    (let [render-id (mf/use-ctx muc/render-id)]
      (mf/html
       [:> "koda:strokes" #js {}
        (for [[index stroke] (d/enumerate strokes)]
          (let [stroke-image-id (dm/str "stroke-image-" render-id "-" index)]
            [:> "koda:stroke"
             #js {:koda:stroke-color          (cond
                                                  (some? (:stroke-color-gradient stroke))
                                                  (str/format "url(#%s)" (str "stroke-color-gradient-" render-id "-" index))

                                                  :else
                                                  (d/name (:stroke-color stroke)))
                  :key                          (swap! internal-counter inc)
                  :koda:stroke-image-id       (when (:stroke-image stroke) stroke-image-id)
                  :koda:stroke-color-ref-file (d/name (:stroke-color-ref-file stroke))
                  :koda:stroke-color-ref-id   (d/name (:stroke-color-ref-id stroke))
                  :koda:stroke-opacity        (d/name (:stroke-opacity stroke))
                  :koda:stroke-style          (d/name (:stroke-style stroke))
                  :koda:stroke-width          (d/name (:stroke-width stroke))
                  :koda:stroke-alignment      (d/name (:stroke-alignment stroke))
                  :koda:stroke-cap-start      (d/name (:stroke-cap-start stroke))
                  :koda:stroke-cap-end        (d/name (:stroke-cap-end stroke))}]))]))))

(defn- export-interactions-data [{:keys [interactions]}]
  (when-let [interactions (seq interactions)]
    (mf/html
     [:> "koda:interactions" #js {}
      (for [interaction interactions]
        [:> "koda:interaction"
         #js {:koda:event-type (d/name (:event-type interaction))
              :koda:action-type (d/name (:action-type interaction))
              :koda:delay ((d/nilf str) (:delay interaction))
              :koda:destination ((d/nilf str) (:destination interaction))
              :koda:overlay-pos-type ((d/nilf d/name) (:overlay-pos-type interaction))
              :koda:overlay-position-x ((d/nilf get-in) interaction [:overlay-position :x])
              :koda:overlay-position-y ((d/nilf get-in) interaction [:overlay-position :y])
              :koda:url (:url interaction)
              :key (swap! internal-counter inc)
              :koda:close-click-outside ((d/nilf str) (:close-click-outside interaction))
              :koda:background-overlay ((d/nilf str) (:background-overlay interaction))
              :koda:preserve-scroll ((d/nilf str) (:preserve-scroll interaction))}])])))


(defn- export-layout-container-data
  [{:keys [layout
           layout-flex-dir
           layout-gap
           layout-gap-type
           layout-wrap-type
           layout-padding-type
           layout-padding
           layout-justify-items
           layout-justify-content
           layout-align-items
           layout-align-content
           layout-grid-dir
           layout-grid-rows
           layout-grid-columns
           layout-grid-cells]}]

  (when layout
    (mf/html
     [:> "koda:layout"
      #js {:koda:layout (d/name layout)
           :koda:layout-flex-dir (d/name layout-flex-dir)
           :koda:layout-gap-type (d/name layout-gap-type)
           :koda:layout-gap-row (:row-gap layout-gap)
           :koda:layout-gap-column (:column-gap layout-gap)
           :koda:layout-wrap-type (d/name layout-wrap-type)
           :koda:layout-padding-type (d/name layout-padding-type)
           :koda:layout-padding-p1 (:p1 layout-padding)
           :koda:layout-padding-p2 (:p2 layout-padding)
           :koda:layout-padding-p3 (:p3 layout-padding)
           :koda:layout-padding-p4 (:p4 layout-padding)
           :koda:layout-justify-items (d/name layout-justify-items)
           :koda:layout-justify-content (d/name layout-justify-content)
           :koda:layout-align-items (d/name layout-align-items)
           :koda:layout-align-content (d/name layout-align-content)
           :koda:layout-grid-dir (d/name layout-grid-dir)}

      [:> "koda:grid-rows" #js {}
       (for [[idx {:keys [type value]}] (d/enumerate layout-grid-rows)]
         [:> "koda:grid-track"
          #js {:koda:index idx
               :key (swap! internal-counter inc)
               :koda:type (d/name type)
               :koda:value value}])]

      [:> "koda:grid-columns" #js {}
       (for [[idx {:keys [type value]}] (d/enumerate layout-grid-columns)]
         [:> "koda:grid-track"
          #js {:koda:index idx
               :key (swap! internal-counter inc)
               :koda:type (d/name type)
               :koda:value value}])]

      [:> "koda:grid-cells" #js {}
       (for [[_ {:keys [id
                        area-name
                        row
                        row-span
                        column
                        column-span
                        position
                        align-self
                        justify-self
                        shapes]}] layout-grid-cells]
         [:> "koda:grid-cell"
          #js {:koda:id id
               :key (swap! internal-counter inc)
               :koda:area-name area-name
               :koda:row row
               :koda:row-span row-span
               :koda:column column
               :koda:column-span column-span
               :koda:position (d/name position)
               :koda:align-self (d/name align-self)
               :koda:justify-self (d/name justify-self)
               :koda:shapes (str/join " " shapes)}])]])))

(defn- export-layout-item-data
  [{:keys [layout-item-margin
           layout-item-margin-type
           layout-item-h-sizing
           layout-item-v-sizing
           layout-item-max-h
           layout-item-min-h
           layout-item-max-w
           layout-item-min-w
           layout-item-align-self
           layout-item-absolute
           layout-item-z-index]}]

  (when (or layout-item-margin
            layout-item-margin-type
            layout-item-h-sizing
            layout-item-v-sizing
            layout-item-max-h
            layout-item-min-h
            layout-item-max-w
            layout-item-min-w
            layout-item-align-self
            layout-item-absolute
            layout-item-z-index)
    (mf/html
     [:> "koda:layout-item"
      #js {:koda:layout-item-margin-m1 (:m1 layout-item-margin)
           :koda:layout-item-margin-m2 (:m2 layout-item-margin)
           :koda:layout-item-margin-m3 (:m3 layout-item-margin)
           :koda:layout-item-margin-m4 (:m4 layout-item-margin)
           :koda:layout-item-margin-type (d/name layout-item-margin-type)
           :koda:layout-item-h-sizing (d/name layout-item-h-sizing)
           :koda:layout-item-v-sizing (d/name layout-item-v-sizing)
           :koda:layout-item-max-h layout-item-max-h
           :koda:layout-item-min-h layout-item-min-h
           :koda:layout-item-max-w layout-item-max-w
           :koda:layout-item-min-w layout-item-min-w
           :koda:layout-item-align-self (d/name layout-item-align-self)
           :koda:layout-item-absolute layout-item-absolute
           :koda:layout-item-z-index layout-item-z-index}])))


(mf/defc export-data
  [{:keys [shape]}]
  (let [props (-> (obj/create) (add-data shape) (add-library-refs shape))]
    [:> "koda:shape" props
     (export-shadow-data           shape)
     (export-blur-data             shape)
     (export-exports-data          shape)
     (export-svg-data              shape)
     (export-interactions-data     shape)
     (export-fills-data            shape)
     (export-strokes-data          shape)
     (export-grid-data             shape)
     (export-layout-container-data shape)
     (export-layout-item-data      shape)]))

