(ns hyperphor.way.vega
  (:require
   [hyperphor.multitool.core :as u] 
   [clojure.walk :as walk]
   [hyperphor.way.ui.config :as config]
   [reagent.core :as reagent]
   ["react-vega" :as rv]))

;;; TODO some convenient way to call vl → vega compiler

;;; ⟐⚇⟐ views ⚇⟐⚇⟐⚇⟐⚇⟐⚇⟐⚇⟐⚇⟐⚇⟐⚇⟐⚇⟐⚇⟐⚇⟐⚇⟐⚇⟐⚇⟐⚇⟐⚇⟐⚇⟐⚇⟐⚇⟐⚇⟐⚇

(def vega-lite-adapter (reagent/adapt-react-class rv/VegaLite))

;;; TODO data is being overused, omitting it should not hide the thing
(defn vega-lite-view
  [spec data & {:keys [listeners]}]
  (when data
    [vega-lite-adapter {:data (clj->js data) ;TODO this has been removed in react-vaga v8. See https://github.com/vega/react-vega?tab=readme-ov-file#3-subscribe-to-signal-events
                        :spec (clj->js spec)
                        :signalListeners (clj->js listeners) ;TODO this has also been removed in react-vaga v8.
                        :actions (boolean (config/config :dev-mode))}]))

(def vega-adapter (reagent/adapt-react-class rv/Vega))

;;; To implement click handling, you need something like this in :signals
;;;     {:name "click"
;;;      :on [{:events "symbol:click" :update "datum"}]}

;;; listeners: eg: {"click" (fn [_ v] (rf/dispatch [:vega-click v]))}
;;; TODO :actions should be a param 
(defn vega-view
  [spec data & {:keys [listeners]}]
  (when data
    [vega-adapter {:data (clj->js data)
                   :spec (clj->js spec)
                   :signalListeners (clj->js listeners)
                   :actions (boolean (config/config :dev-mode))
                   }]))

;;; ⟐⚇⟐ spec manipulators ⚇⟐⚇⟐⚇⟐⚇⟐⚇⟐⚇⟐⚇⟐⚇⟐⚇⟐⚇⟐⚇⟐⚇⟐⚇⟐⚇⟐⚇⟐⚇⟐⚇⟐⚇⟐⚇⟐⚇⟐⚇⟐⚇

;;; Use this in specs when field name might have dots or other problematic characters.
(defn bracketize
  [s]
  (when s (str "[" s "]")))

(defn- patch-matches?
  [id thing]
  (and (map? thing)
       (every? (fn [[k v]] (= v (k thing))) id)))

;;; Terrible kludge but the alternative is exposing every single Vega option explicitly?
;;; Each patch is [<identifier> <modifier]
;;; identifier is a map, matches maps that have equal fields
;;; modifier is a map to be merged (recursively) with original
;;; → multitool I guess,
;;; Extension: check that each patch matches exactly once.
(defn patch
  [spec patches]
  (reduce (fn [spec [id mods]]
            (walk/postwalk
             #(if (patch-matches? id %)
                (u/merge-recursive % mods)
                %)
             spec))
          spec
          patches))


;;; ⟐⚇⟐ options ⚇⟐⚇⟐⚇⟐⚇⟐⚇⟐⚇⟐⚇⟐⚇⟐⚇⟐⚇⟐⚇⟐⚇⟐⚇⟐⚇⟐⚇⟐⚇⟐⚇⟐⚇⟐⚇⟐⚇⟐⚇⟐⚇

;;; TODO put these in groups, but :optgroups ar broken
(def color-schemes
  [
   "blues"
   "tealblues"
   "teals"
   "greens"
   "browns"
   "greys"
   "purples"
   "warmgreys"
   "reds"
   "oranges"

   "turbo"
   "viridis"
   "inferno"
   "magma"
   "plasma"
   "cividis"
   "bluegreen"
   "bluepurple"
   "goldgreen"
   "goldorange"
   "goldred"
   "greenblue"
   "orangered"
   "purplebluegreen"
   "purpleblue"
   "purplered"
   "redpurple"
   "yellowgreenblue"
   "yellowgreen"
   "yelloworangebrown"
   "yelloworangered"
   "darkblue"
   "darkgold"
   "darkgreen"
   "darkmulti"
   "darkred"
   "lightgreyred"
   "lightgreyteal"
   "lightmulti"
   "lightorange"
   "lighttealblue"

   ;; diverging
   "blueorange"
   "brownbluegreen"
   "purplegreen"
   "pinkyellowgreen"
   "purpleorange"
   "redblue"
   "redgrey"
   "redyellowblue"
   "redyellowgreen"
   "spectral"])




