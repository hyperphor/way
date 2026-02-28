(ns hyperphor.way.download
  (:require [clojure.string :as str]
            )
  )

;;; ⦿⦾⦿ download tsv ⦿⦾⦿⦾⦿⦾⦿⦾⦿⦾⦿⦾⦿⦾⦿⦾⦿⦾⦿⦾⦿⦾⦿⦾⦿⦾⦿⦾⦿⦾⦿⦾⦿⦾⦿⦾⦿⦾⦿⦾⦿⦾⦿⦾⦿⦾⦿⦾⦿⦾⦿⦾⦿⦾⦿⦾⦿⦾⦿

;;; This lets you "download" directly from client data, no server call required

;;; Rows is a seq of maps
(defn data->tsv
  [rows]
  (let [cols (keys (first rows))]
    (str/join
     "\n" 
     (map (partial str/join \tab)
          (cons (map name cols)
                (map (fn [row] (map (fn [col] (get row col)) cols))
                     rows))))))

(defn download-data-as-tsv [data export-name]
  (let [data-blob (js/Blob. (clj->js [(data->tsv data)]) #js {:type "application/tsv"})
        link (.createElement js/document "a")]
    (set! (.-href link) (.createObjectURL js/URL data-blob))
    (.setAttribute link "download" export-name)
    (.appendChild (.-body js/document) link)
    (.click link)
    (.removeChild (.-body js/document) link)
    nil))

(defn button
  [data-sub filename]
  (when-not (empty? data-sub) ;TODO disable is better
    [:button.btn.btn-outline-primary
     {:on-click #(do
                   (.preventDefault %)
                   (download-data-as-tsv data-sub filename))}
     "Download"]))
