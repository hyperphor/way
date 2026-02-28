(ns hyperphor.way.cheatmap
  (:require [hyperphor.way.vega :as v]
            [hyperphor.way.cluster :as cluster]
            [hyperphor.multitool.core :as u]
            [hyperphor.multitool.math :as um]
            [hyperphor.way.cutils :as cu]
            )
  )

;;; TODO tooltips
;;; TODO row/col annotations w colors and scales, see examples
;;; TODO autoscale dend "height" so it remains legible for large graphs
;;; TODO more parameterization
;;;   option to have tree on right side
;;;   axis labels

(def default-options
  {:color-scheme "magma"
   :dend-width 50                       ;TODO should be independent for x and y, also would be niec to be adaptive to hmap size
   :cell-size 22                        ;TODO x and y independence
   :cell-gap 1
   :aggregate-fn :mean
   :tooltip? true
   :cluster-rows? true
   :cluster-cols? true
   })

;;; Generates the spec for a single tree 
(defn tree
  [cluster-data left?]
  {:type "group"
   :data 
   [{:name "links"
     :source cluster-data
     :transform
     [{:type "treelinks"}
      {:type "linkpath"
       :orient (if left? "horizontal" "vertical")
       :shape "orthogonal"}]}]
   :marks [{:type "path"
            :from {:data "links"}
            :encode {:enter
                     {:path {:field "path"}
                      :stroke {:value "#666"}}}}
           ;; For debugging purposes
           #_
           {:type "text"
            :from {:data cluster-data}
            :encode {:enter
                     {:x {:field "x"}
                      :y {:field "y"}
                      :angle {:value 45}
                      :text {:field "id"}}}}
           ]
   })

;;; Generates TWO data specs (for full tree, and filtered to leaves)
(defn tree-data-spec
  [name clusters left?]
  [{:name name
    :values clusters
    :transform
    [{:type "stratify" :key "id" :parentKey "parent"}
     {:type "tree"
      :separation false                 ;necessary to keep tree and hm aligned
      :method "cluster"
      :size [{:signal (if left? "hm_height" "hm_width")} ;;  
             {:signal "dend_width"}]
      :as (if left?
            ["y" "x" "depth" "children"]
            ["x" "y" "depth" "children"])}]}
   {:name (str name "-leaf")
    :source name
    :transform [{:type "filter" :expr "datum.children == 0"}]}]
  )


;;; todo in Multitool
(defn concatv
  "Conj a value to the front (left) of vector. Not performant"
  [& args]
  (vec (apply concat args)))


(defn spec
  [data h-field v-field value-field h-clusters v-clusters
   {:keys [color-scheme dend-width cell-size cell-gap tooltip? patches] :as options}]
  (let [hsize (count (distinct (map h-field data))) ;wanted to this in vega but circularities are interfering
        vsize (count (distinct (map v-field data)))
        base 
        {:description "A clustered heatmap with side-dendrograms"
         :$schema "https://vega.github.io/schema/vega/v5.json"
         :layout {:align "each"
                  :columns 2}
         :data (concatv
                [{:name "hm"
                  :values data}]
                (when h-clusters (tree-data-spec "htree" h-clusters true))
                (when v-clusters (tree-data-spec "vtree" v-clusters false)))
         :scales
         ;; Note: min is because sorting apparently requires an aggregation? And there's no pickone
         [{:name "sx" :type "band"
           :domain (if v-clusters
                     {:data "vtree-leaf" :field "id" :sort {:field "x" :op "min"}}
                     {:data "hm" :field v-field :sort true})
           :range {:step cell-size} } 
          {:name "sy" :type "band"
           :domain (if h-clusters
                     {:data "htree-leaf" :field "id" :sort {:field "y" :op "min"}}
                     {:data "hm" :field h-field :sort true}
                     )
           :range {:step cell-size}} 
          {:name "color"
           :type "symlog"
           :range {:scheme color-scheme}
           :domain {:data "hm" :field value-field}
           }]
         :signals
         [{:name "hm_width" :value (* cell-size vsize)}
          {:name "hm_height" :value (* cell-size hsize)}
          {:name "dend_width" :value dend-width} ;TODO :bind doesn't work, maybe these shouldn't be signals
          ]
         :padding 5
         :marks
         [
          ;; Upper-left Empty quadrant
          {:type :group                       
           :encode {:enter {:width (if h-clusters {:signal "dend_width"} {:value 0})
                            :height (if v-clusters {:signal "dend_width"} {:value 0})
                            :strokeWidth {:value 0}}}}

          ;; column tree
          (if v-clusters
            (tree "vtree" false)
            {:type "group"
             :encoding {:width {:signal "hm_width"} 
                        :height {:value 0}
                        :strokeWidth {:value 0}}
             })

          ;; row tree
          (if h-clusters
            (tree "htree" true)
            {:type "group"
             :encoding {:width {:value 0}
                        :height {:signal "hm_height"} 
                        :strokeWidth {:value 0}}
             })

          ;; actual heatmap
          {:type "group"
           :name "heatmap"
           :style "cell"
           :encode {:update {:width {:signal "hm_width"}
                             :height {:signal "hm_height"}
                             :fill {:value "#ccc"}}} ;TODO parameter, also see cell-gap
           :axes
           [{:orient :right :scale :sy :domain false :title (cu/humanize h-field)} 
            {:orient :bottom :scale :sx :labelAngle 90 :labelAlign "left" :labelBaseline :middle :domain false :title (cu/humanize v-field)}]

           :legends
           [{:fill :color
             :type :gradient
             :title (cu/humanize value-field)
             :titleOrient "bottom"
             :gradientLength {:signal "hm_height / 2"} ;TODO not always right
             }]

           :marks
           [{:type "rect"
             :from {:data "hm"}
             :encode
             {:enter
              {:y {:field h-field :scale "sy"}
               :x {:field v-field :scale "sx"}
               :width {:value (- cell-size cell-gap)} :height {:value (- cell-size cell-gap)}
               :fill {:field value-field :scale "color"}
               :tooltip {:signal (when tooltip?
                                   (u/expand-template
                                    "{ '{{h-field}}': datum['{{h-field}}'], '{{v-field}}': datum['{{v-field}}'], '{{value-field}}': datum['{{value-field}}']}"
                                    {:h-field (name h-field) :v-field (name v-field) :value-field (name value-field)}
                                    ))}}}}
            ]
           }
          ]}]
    (v/patch base patches)
    ))

(defn aggregate
  [data dim-cols agg-col agg-fn]
  (let [agg-fn (case agg-fn
                 :sum #(apply + %)
                 :count count
                 :mean um/mean)]
    (->> data
         (filter #(every? % (cons agg-col dim-cols)))
         (group-by (apply juxt dim-cols))
         (map (fn [[dims rows]]
                (assoc (zipmap dim-cols dims)
                       agg-col
                       (agg-fn (keep agg-col rows))
                       )))
         )))





;;; This is the top-level call. Takes data and three field designators, does clustering on both dimensions
;;; and outputs a heatmap with dendrograms
(defn heatmap
  [data row-field col-field value-field options]
  (let [options (merge default-options options)
        {:keys [aggregate-fn cluster-rows? cluster-cols?]} options]
    (when (and data row-field col-field value-field)
      (let [data (aggregate data [row-field col-field] value-field aggregate-fn)
            cluster-l (when cluster-rows?
                        (cluster/cluster-data data row-field col-field value-field ))
            cluster-u (when cluster-cols?
                        (cluster/cluster-data data col-field row-field value-field ))
            ]
        [v/vega-view (spec data row-field col-field value-field cluster-l cluster-u options) []])
      )))


