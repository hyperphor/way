(ns hyperphor.way.api
  (:require [re-frame.core :as rf]
            [ajax.core :as ajax]
            ))

;;; API utils
;;; Note: transit decoding happens magically

(def api-base "/api")

(def standard-ajax-options
  {:error-handler #(rf/dispatch [:error %1])
   :format :transit
   :response-format :transit
   })

(rf/reg-event-fx
 :error
 (fn [_ [_ message]]
   {:dispatch [:flash (if message
                        {:class "alert-danger" :message message}
                        {:show? false})]
    }))

(defn ajax-get
  [uri options]
  (ajax/GET uri
            (merge standard-ajax-options options)))

(defn ajax-post
  [uri options]
  (ajax/POST uri
             (merge standard-ajax-options options)))

(defn ajax-put
  [uri options]
  (ajax/PUT uri
            (merge standard-ajax-options options)))

(defn api-get
  [uri options]
  (ajax-get (str "/api" uri) options))

(defn api-post
  [uri options]
  (ajax-post (str "/api" uri) options))

(defn api-put
  [uri options]
  (ajax-put (str "/api" uri) options))
