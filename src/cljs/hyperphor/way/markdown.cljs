(ns hyperphor.way.markdown
  (:require [nextjournal.markdown :as md]
            [nextjournal.markdown.transform :as mdt]
            ))

;;; Client-side Markdown rendering

(defn render
  [s]
  (-> s
      md/parse
      mdt/->hiccup
      ))

