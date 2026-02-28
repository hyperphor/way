(ns hyperphor.way.cutils
  (:require [clojure.string :as str]
            [clojure.string :as str]
            [hyperphor.multitool.core :as u]
            )
  )

;;; Cljc web utils

;;; Generalize?
;;; CLJC
(defn humanize
  [term]
  (when term
    (-> term
        name
        (str/replace "_" " "))))

;;; See https://material.io/resources/icons/
(defn icon
  [icon tooltip handler & {:keys [class disabled?] :or {class "md-dark"}}]
  [:i.icon.material-icons.vcenter
   {:class (str/join " " (list class (if disabled? "md-inactive" "")))
    :on-click handler
    :data-toggle "tooltip"
    :title tooltip}
   icon])

#?(:clj
   (defn style-arg
     [m]
     (str/join (map (fn [[p v]] (format "%s: %s;" (name p) v)) m))))

;;; cljs doesn need this at all
