(ns hyperphor.way.cluster-test
  (:use clojure.test)
  (:use hyperphor.way.cluster)
  (:require [clojure.string :as str]
            [hyperphor.multitool.core :as u]
            [hyperphor.multitool.math :as math]))

;;; TODO duh

(deftest vector-distance
  (testing "manhattan distance"
    (is (= 4 (manhattan-distance [1 2 3] [3 2 1]) ))
    (is (= 0 (manhattan-distance [] [])))
    ;; (manhattan-distance [2 3] [5]) should error I suppose
    )
  (testing "euclidean distance"
    (is (= 8.0 (euclidean-squared-distance [1 2 3] [3 2 1]) ))
    (is (= 0 (euclidean-squared-distance [] [])))
    ;; (euclidean-squared-distance [2 3] [5]) should error I suppose
    ))

(defn random-data
  [n m vrange]
  (for [i (range n)
        j (range m)]
    {:row i :col j :val (rand-int vrange)}))

(comment 
  (def d0 (random-data 5 5 10))
(cluster-data d0 :row :col :val))


