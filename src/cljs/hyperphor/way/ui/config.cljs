(ns hyperphor.way.ui.config
  (:require [hyperphor.way.api :as api]))

;;; TODO not sure why this isn't in react db – guess it doesn't change. Now includeds :user
(def the-config (atom nil))

(defn init
  [cont]
  (api/api-get "/config" {:handler #(do (reset! the-config %)
                                        (cont %))}))

(defn config
  [& atts]
  (assert @the-config "Config referenced before set")
  (get-in @the-config atts))

