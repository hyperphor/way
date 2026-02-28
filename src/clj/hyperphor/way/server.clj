(ns hyperphor.way.server
  (:require [ring.adapter.jetty :as jetty]
            [taoensso.timbre :as log]))

(def server (atom nil))

(defn stop
  []
  (when @server
    (.stop @server)))

(def saved-handler (atom nil))           ;dev convenience

(defn start
  [port handler]
  (reset! saved-handler handler)
  (log/infof "Starting server at port %s" port) ;TODO name of app
  (stop)
  (reset! server (jetty/run-jetty handler {:port port :join? false})))

(defn restart
  [port]
  (start port @saved-handler))



  






