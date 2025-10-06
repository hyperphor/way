(ns com.hyperphor.way.handler
  (:require [compojure.core :refer [defroutes context GET POST make-route routes]]
            [compojure.route :as route]
            [ring.middleware.format :refer [wrap-restful-format]]
            [ring.middleware.format-response :refer [wrap-restful-response]]
            [ring.middleware.basic-authentication :refer [wrap-basic-authentication]]
            [org.candelbio.multitool.core :as u]
            [org.candelbio.multitool.cljcore :as ju]
            [com.hyperphor.way.oauth :as oauth]
            [com.hyperphor.way.views.html :as html]
            [com.hyperphor.way.views.admin :as admin]
            [com.hyperphor.way.views.login :as login]
            [com.hyperphor.way.data :as data]
            [com.hyperphor.way.config :as config]
            [ring.logger :as logger]
            [ring.middleware.session.memory :as ring-memory]
            [ring.middleware.resource :as resource]
            [taoensso.timbre :as log]
            [ring.middleware.defaults :as middleware]
            [ring.util.response :as response]
            [clojure.string :as str]
            [environ.core :as env]
            )
  (:use [hiccup.core])
  )

;;; Ensure API and site pages use the same store, so authentication works for API.
(defonce common-store (ring-memory/memory-store))

(defn authenticated?
  [name pass]
  (= [name pass] (config/config :basic-auth-creds)))

(defn content-response
  [data & [status]]
  ;; Try to return vectors for consistency. list? is false for lazy seq. Note doesn't do anything about internal lists.
  (let [data (if (and (sequential? data) (not (vector? data)))
               (into [] data)
               data)]
    {:status (or status 200)
     :headers {}
     ;; Warning: this breaks the file-upload response because it isn't under wrapper
     :body data}))

(defn spa
  [& args]
  (response/content-type
   (content-response
    (apply html/html-frame-spa args))
   "text/html"))

(defroutes base-site-routes
  (GET "/health" []                     ;TODO exclude from log, see /opt/mt/repos/dotfiles/.m2/repository/ring-logger/ring-logger/1.1.1/logger.clj
    (response/content-type
     {:status 200
      :body "I'm good"}
     "text/plain"))
  (GET "/login" [] (login/login-view)) ;TODO only if OAuth configured
  (GET "/authenticated" req           ;on mgen, its /callback or somesuch
    (let [original-page (get-in req [:cookies "way_landing" :value])] ;TODO
      (response/redirect (if (empty? original-page) "/" original-page))))
  (GET "/admin" req (admin/view req))
  (GET "/*" [] (spa))                    ;index handled by spa
  (route/not-found "Not found")
  )

;;; Must be something built-in for this?
(defn wrap-filter
  [handler path]
  (make-route nil path handler))

;;; Weird that this isn't a standard part of ring
(defn wrap-no-read-eval
  [handler]
  (fn [request]
    (binding [*read-eval* false]
      (handler request))))

;;; Weird that this isn't a standard part of ring
(defn wrap-exception-handling
  [handler]
  (fn [request]
    (try
      (handler request)
      (catch clojure.lang.ExceptionInfo e
        {:status 400 :headers {} :body (str "Error: " (ex-message e))})
      (catch Throwable e
        {:status 500 :headers {} :body (print-str e)}))))

(defn wrap-api-exception-handling
  [handler]
  (fn [request]
    (try
      (handler request)
      (catch clojure.lang.ExceptionInfo e
        (log/error "API error" (pr-str e))
        {:status 400 :headers {} :body {:error (ex-message e) :data (ex-data e)}})
      (catch Throwable e
        (log/error "API error" (pr-str e))
        {:status 500 :headers {} :body (print-str e)}))))

(def middleware-site-defaults
  "A default configuration for a browser-accessible website, based on current
  best practice."
  {:params    {:urlencoded true
               :multipart  true
               :nested     true
               :keywordize true}
   :cookies   true
   :session   {:flash true
               :cookie-attrs {:http-only true}
               :store common-store}
   :security  {:anti-forgery   true
               :frame-options  :sameorigin
               :content-type-options :nosniff}
   :static    {:resources "public"}
   :responses {:not-modified-responses true
               :absolute-redirects     false
               :content-types          true
               :default-charset        "utf-8"}})


(def site-defaults
  (-> middleware-site-defaults                   ;was middleware/site-defaults
      (assoc-in [:security :anti-forgery] false)          ;necessary for upload (TODO not great from sec viewpoint)
      (assoc :cookies true)
      (assoc-in [:session :cookie-attrs :same-site] :lax) ;for oauth
      (assoc-in [:session :store] common-store)))

(def log-exclude #{"/health"})

(defn site-routes
  [app-site-routes]
  (-> (routes app-site-routes base-site-routes)
      (wrap-restful-response)

      (oauth/wrap-oauth)

      ;; TODO isn't this redundant with middleware-site-defaults?
      (resource/wrap-resource "public" {:allow-symlinks? true}) ;allow symlinks in static dir
      (middleware/wrap-defaults site-defaults)                                  ;TODO turn off static thing in here
      wrap-no-read-eval
      wrap-exception-handling
      (logger/wrap-with-logger          ;hook Ring logger to Timbre
       {:log-fn (fn [{:keys [level throwable message]}]
                  (log/log level throwable message))
        :transform-fn (fn [{:keys [level throwable message] :as item}]
                        (when-not (contains? log-exclude (:uri message))
                          item))})
      ))

;;; Copied oout of middleware
(def middleware-api-defaults
  "A default configuration for a HTTP API."
  {:params    {:urlencoded true
               :keywordize true}
   :responses {:not-modified-responses true
               :absolute-redirects     false
               :content-types          true
               :default-charset        "utf-8"}})

(def api-defaults
  (-> middleware-api-defaults                      ;was middlewar/api-defaults
      (assoc :cookies true)
      (assoc-in [:session :flash] false)
      (assoc-in [:session :cookie-attrs] {:http-only true, :same-site :lax})
      (assoc-in [:session :store] common-store)))

;;; TODO should be in .cljc, should be parameterizable
(def api-base "/api")

(defroutes base-api-routes  
  (context api-base []
    (GET "/config" _                    ;TODO try to build config into compiled js and eliminate this
      (content-response (config/config)))
    (GET "/data" req                    ;params include data-id and other
      (content-response (data/data (:params req))))
    #_                                  ;TODO dev-mode only
    (GET "/error" req                   ;For testing error reporting
      (content-response (/ 0 0)))
    #_
    (POST "/error" req                   ;For testing error reporting
      (content-response (/ 0 0)))

    #_
    (route/not-found (content-response {:error "Not Found"}))
    ))

(defn api-routes
  [app-api-routes]
  (-> (routes app-api-routes base-api-routes)
      (middleware/wrap-defaults api-defaults)
      wrap-no-read-eval
      wrap-api-exception-handling
      (logger/wrap-with-logger          ;hook Ring logger to Timbre
       {:log-fn (fn [{:keys [level throwable message]}]
                  (log/log level throwable message))})
      (wrap-restful-format)
      (wrap-filter "/api/*")            ;filter early so edn responses don't go to regular site requests
      ))

(defn wrap-basic-authentication-except
  [base]
  (fn [request]
    (if (oauth/open-uri? (:uri request))
      (base request)
      ((wrap-basic-authentication base authenticated?) request))))

;;; This is Heroku-specific
(defn wrap-force-ssl
  [handler]
  (fn [req]
    (let [heroku-protocol (get-in req [:headers "x-forwarded-proto"])]
      (cond (and heroku-protocol (= "https" heroku-protocol))
            (handler req)
            heroku-protocol
            (response/redirect (str "https://" (:server-name req) (:uri req)))
            :else
            (handler req)))))

(defn wrap-if
  [route cond wrapper]
  (if cond
    (wrapper route)
    route))

(defn app
  [app-site-routes app-api-routes]
  (-> (routes (api-routes app-api-routes) (site-routes app-site-routes))
      (wrap-if (and (config/config :basic-auth-creds)
                    (not (config/config :dev-mode)))
               wrap-basic-authentication-except)
      (wrap-if (config/config :heroku :force-ssl)
               wrap-force-ssl)))

