(ns hyperphor.way.cluster
  (:require [hyperphor.multitool.core :as u]
            [hyperphor.multitool.math :as mu]))

;;; Not particularly performant, but OK for the sizes of heatmaps that you can reasonably display.
;;; See ANN (approximate nearest neighbor search) https://github.com/jbellis/jvector?tab=readme-ov-file

;;; https://bioinformatics.ccr.cancer.gov/docs/btep-coding-club/CC2023/complex_heatmap_enhanced_volcano/
;;; https://en.wikipedia.org/wiki/Ward%27s_method

;;; TODO package in a Vega transform, see https://vega.github.io/vega/docs/api/extensibility/#transform

;;; Not presently used, TODO should be an option, it actually seems better in some cases
(defn manhattan-distance
  [v1 v2]
  (reduce + (map (comp abs -) v1 v2)))

(defn euclidean-squared-distance
  [v1 v2]
  (reduce + (map (fn [e1 e2] (Math/pow (- e1 e2) 2)) v1 v2)))

(def vector-mean (mu/tensorize (fn [a b] (/ (+ a b) 2))))
  
;;; Naive and inefficient algo
;;; maps: data as seq of maps
;;; idcol: dimension to be clustered
;;; → multitool

;;; For clarity, expressed as rows by columns (but actually works on mapseqs so any field can be "row" or "column")
;;; maps: mapseq, each entry represents a cell in the heatmap
;;; row-dim: the field containing the leaf clusters (rows)
;;; col-dim: the other dimension (columns)
;;; value-field: field containing values to be clustered
;;;   Note: I suppose there is no reason why value field couldn't be a function or a set of fields...
(defn cluster
  [maps row-dim col-dim value-field]
  (let [indexed (u/map-values
                 value-field
                 (u/index-by (juxt row-dim col-dim) maps )) ;produces essentially a matrix, what clustering usually starts with
        rows (distinct (map row-dim maps))
        cols (distinct (map col-dim maps))
        vectors (zipmap rows (map (fn [row]
                                    (vec (map (fn [col]
                                                (get indexed [row col]))
                                              cols)))
                                  rows))
        ;; Initialize to the complete graph of inter-row distances
        distances (into {}
                        (u/forf [row1 rows row2 rows]
                                (when (u/<* row1 row2)
                                  [[row1 row2] (euclidean-squared-distance (get vectors row1) (get vectors row2))])))
        ]
    (loop [vectors vectors              ;TODO make transient, but some issues
           distances distances
           tree []]
      (if (= 1 (count vectors))
        tree
        (let [[[row1 row2] _] (u/min-by second distances)
              cluster-id (str row1 "-" row2) ;TODO These IDS are annoyingly large, good for debugging though
              vector (vector-mean (get vectors row1) (get vectors row2))]
          (recur (-> vectors            ;replace merged (clustered) vectors with new one (mean).
                     (dissoc row1 row2)
                     (assoc cluster-id vector))
                 (->> distances         ;remove 
                      (u/dissoc-if (fn [[[xrow1 xrow2] _]]
                                     (or (= row1 xrow1) (= row1 xrow2)
                                         (= row2 xrow1) (= row2 xrow2))))
                      (merge (into {}
                                   (for [[id v] (dissoc vectors row1 row2)] ;TODO doing this twice
                                     [[id cluster-id] (euclidean-squared-distance v vector)]))))

                 (conj tree [cluster-id row1 row2])
                 ))))))

;;; Returns a mapseq of {:id :parent}, suitable for passing to vega tree.
(defn cluster-data
  [maps row-dim col-dim value-field]
  (let [clusters (cluster maps row-dim col-dim value-field)
        invert (merge (u/index-by second clusters) (u/index-by #(nth % 2)  clusters))
        root (last clusters)]
    (if (empty? clusters)               ;special case where there is a single row
      {:id (get (first maps) row-dim)}
      (cons {:id (first root)}
            (map (fn [[c [p _]]]
                   {:id c :parent p})
                 invert)))))

