(ns hyperphor.way.violin
  (:require [hyperphor.way.vega :as v]
            [hyperphor.way.cutils :as cu]
            [hyperphor.way.web-utils :as wu]
            [hyperphor.multitool.core :as u]
            [clojure.set :as set]
            )
  )

(defn map-bidirectional
  [map]
  (merge map (set/map-invert map)))

(def remap
  (map-bidirectional
   {:x :y
    :x2 :y2
    :xc :yc
    :width :height
    :bottom :left
    :vertical :horizontal
    "width" "height"
    }))

(defn rotate-spec
  [spec]
  (u/walk-map-entries
   (fn [[k v]] 
     [(get remap k k)
      (get remap v v)])
   spec))

;;; Patch mechanism. This isn't really adequate, its OK for maps but not vectors. 

;;; Changem from multitool version in how vectors are handled (TODO maybe update multiool)
(defn merge-recursive
  "Recursively merge two arbitrariy nested map structures. Terminal seqs are concatentated, terminal sets are merged."
  [m1 m2]
  (u/merge-recursive-with
   (fn [v1 v2]
     (cond (nil? v1) v2
           (nil? v2) v1
           (and (set? v1) (set? v2))
           (set/union v1 v2)
           (and (vector? v1) (vector? v2))
           (mapv merge-recursive v1 v2)
           (and (sequential? v1) (sequential? v2))
           (concat v1 v2)
           :else v2)                    ;Thought about returning a pair in this case but that seems a bit off.
     )
   m1 m2))

(defn spec
  [data dim feature {:keys [vertical? patches] :as options}]
  (let [dim (name dim)]
    ((if vertical? rotate-spec identity)
     (merge-recursive 
      {:description "A violin plot example showing distributions for pengiun body mass.",
       :$schema "https://vega.github.io/schema/vega/v5.json",
       :width 700,
       :signals
       [{:name "blobWidth", :value 200, :bind {:input :range, :min 100, :max 1000}} ;controls fatness of violins  
        {:name "height", :value 750 :bind {:input :range, :min 100, :max 2000}}
        {:name "points", :value true, :bind {:input "checkbox"}}
        {:name "boxes" :value true :bind {:input "checkbox"}}
        {:name "violins" :value true :bind  {:input "checkbox"}}
        {:name "jitter" :value 50 :bind {:input :range, :min 0, :max 200}}
        {:name "trim", :value true, :bind {:input "checkbox"}}
        {:name "bandwidth", :value 0, :bind {:input "range", :min 0, :max 1.0E-4, :step 1.0E-6}}],
       :data
       [{:name "source", :values data}
        {:name "density",
         :source "source",
         :transform
         [{:type "kde",                   ; Kernel Density Estimation, see https://vega.github.io/vega/docs/transforms/kde/
           :field feature,
           :groupby [dim],
           :bandwidth {:signal "bandwidth"},
           :resolve "shared"
           :extent {:signal "trim ? null : [0.0003, 0.0005]"}}]}
        {:name "stats",
         :source "source",
         :transform
         [{:type "aggregate",
           :groupby [dim],
           :fields [feature feature feature feature feature],
           :ops ["min" "q1" "median" "q3" "max"],
           :as ["min" "q1" "median" "q3" "max"]}]}]

       :config {:axisBand {:bandPosition 1, :tickExtra true, :tickOffset 0}},
       :axes
       [{:orient :bottom, :scale "xscale", :zindex 1, :title (cu/humanize feature)} ;TODO want metacluster in this
        {:orient :left, :scale "layout", :title (cu/humanize dim) :tickCount 5, :zindex 1}],

       :scales
       [{:name "layout",
         :type "band",
         :range "height",
         :domain {:data "source", :field dim},
         :paddingOuter 0.5}
        {:name "xscale",
         :range "width",
         :round true,
         :domain {:data "source", :field feature},
         :nice true}
        {:name "hscale",
         :type "linear",
         :range [0 {:signal "blobWidth"}],
         :domain {:data "density", :field "density"}}
        {:name "color", :type "ordinal", :domain {:data "source", :field dim}, :range "category"}],
       :padding 5,
       :marks
       [{:type "group",
         :from {:facet {:data "density", :name "violin", :groupby dim}},
         :encode
         {:update
          {:yc {:scale "layout", :field dim, :band 0.5},
           :height {:signal "blobWidth"},
           :width {:signal "width"}}},
         :data
         [{:name "summary",
           :source "stats",
           :transform [{:type "filter", :expr (wu/js-format "datum.%s === parent.%s" dim dim)}]}],
         :marks
         [

          ;; Violins
          {:type "area",
           :from {:data "violin"},
           :encode
           {:enter
            {:fill {:scale "color", :field {:parent dim}}
             :orient {:value :vertical}
             ;; :tooltip {:signal "datum"}
             },
            :update
            {:x {:scale "xscale", :field "value"},
             :yc {:signal "blobWidth / 2"},
             :height {:scale "hscale", :field "density"}
             :opacity {:signal "violins ? 1 : 0"}}}}

          ;; Points
          {:type "symbol",
           :from {:data "source"},
           :encode
           {:enter {:fill "black", :y {:value 0}},
            :update
            {:stroke {:value "#000000"},
             :fill {:value "#000000"},
             :size {:value 25},
             :z {:value 1000000},
             :yc {:signal "blobWidth / 2 + jitter*(random() - 0.5)"}, ;should scale with fatness
             :strokeWidth {:value 1},
             :opacity {:signal "points ? 0.3 : 0"},
             :shape {:value "circle"},
             :x {:scale "xscale", :field feature}}}}

          ;; Box outline

          {:type "rect",
           :from {:data "summary"},
           :encode
           {:enter {:stroke {:value "black"},
                    :height {:value 2}
                    :cornerRadius {:value 4}
                    },
            :update
            {:x {:scale "xscale", :field "q1"},
             :x2 {:scale "xscale", :field "q3"},
             :height {:signal "blobWidth / 5"}
             :yc {:signal "blobWidth / 2"}
             :opacity {:signal "boxes ? 1 : 0"}
             :fill {:signal (str "violins ? '' :  scale('color', datum." dim ")")} 
             }
            }}



          ;; Midpoint
          {:type "rect",
           :from {:data "summary"},
           :encode
           {:enter {:fill {:value "black"},
                    :width {:value 2},
                    :height {:value 8}},
            :update {:x {:scale "xscale", :field "median"},
                     :yc {:signal "blobWidth / 2"}
                     :opacity {:signal "boxes ? 1 : 0"}}}}

          ;; Whisker 
          {:type "rect",                 
           :from {:data "summary"},
           :encode
           {:enter {:fill {:value "black"},
                    :height {:value 2}},
            :update {:x {:scale "xscale", :field "min"},
                     :x2 {:scale "xscale", :field "max"}
                     :yc {:signal "blobWidth / 2"}
                     :height {:value 2}
                     :opacity {:signal "boxes ? 1 : 0"}
                     }}}

          ]}],
       } patches))))

;;; Options:
;;;  vertical?
;;;  patches  - this gets merged recursively into the spec. Breaks abstraction but makes arbitrary customization possible
(defn violin
  [data row-field feature-field options]
  (when (and data row-field feature-field)
    [v/vega-view (spec data row-field feature-field options) []]))

