;;; Note: not real tests yet
;;; Backend for feed test

(ns hyperphor.way.data-test
  (:use clojure.test)
  (:require 
            [hyperphor.way.handler :as handler]
            [hyperphor.way.server :as server]
            [hyperphor.way.config :as config]
            [hyperphor.way.data :as data]
            [compojure.core :as compojure]
            [hyperphor.multitool.core :as u]
            [clj-http.client :as client2]))
            

(defn server
  [port]
  (config/read-config "test/test-config.edn")
  (server/start port (handler/app (compojure/routes) (compojure/routes))))

(defmethod data/data :test
  [{:keys [from to] :as params}]
  (let [from (u/coerce-numeric from)    ;TODO should be done by machinery
        to (u/coerce-numeric to)]
    (vec (range from to))))

(defn poke1
  []
  (-> "http://localhost:1112/api/data"
       (client2/get {:query-params {:data-id "test" :from 1 :to 10}
                     :as :json})
       :body))


