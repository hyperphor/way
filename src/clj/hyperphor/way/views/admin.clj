(ns hyperphor.way.views.admin
  (:require [environ.core :as env]
            [hyperphor.way.views.html :as html]
            [hyperphor.way.config :as config]
            [hiccup.util :as hu]
            [hyperphor.multitool.core :as u]
            [clojure.set :as set]
            [hyperphor.multitool.nlp :as nlp]
            )
  )

;;; TODO git commit information, etc. Can't run git on server, somehow need to sneak it into an uberjar. Hm you could just
;;; link a resource to  ./.git/logs/HEAD

;;; Redact secrets. NOTE: this is not 100% reliable of course
(def redactable #{"token" "api" "key" "cred" "creds" "credential" "credentials" "password" "secret"})

(defn redact?
  [k]
  (not (empty? (set/intersection redactable (set (nlp/tokens (name k)))))))

(defn redact
  [map]
  (u/map-key-values (fn [k v] (if (redact? k)
                                  "*REDACTED*"
                                  v))
                    map))

(defn map-table
  [name map]
  (let [map (redact map)]
    [:divto [:h2 name]
     [:table.table-bordered
      (for [key (sort (keys map))]
        [:tr
         [:th (str key)]
         [:td (if (map? (get map key))
                (map-table "" (get map key))
                (hu/escape-html (str (get map key))))]])]]))

(defn view
  [req]
  (if (config/config :dev-mode)
    (html/html-frame
     {}
     "Admin"
     [:div
      (map-table "Way Config" (config/config))
      (map-table "Env" env/env)
      (map-table "System/getenv" (System/getenv))
      (map-table "HTTP req" req)
      ])
    (throw (ex-info "Dev mode only" {:req req}))))
