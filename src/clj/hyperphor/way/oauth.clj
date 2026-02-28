(ns hyperphor.way.oauth
  (:require [hyperphor.way.config :as config]
            [ring.middleware.oauth2 :refer [wrap-oauth2]]
            [ring.util.response :as response]
            [clojure.string :as str]
            [clojure.set :as set]
            [clojure.data.json :as json]
            [hyperphor.multitool.core :as u]
            [clj-http.client :as client]
            )
  (:import [org.apache.commons.codec Charsets]
           [org.apache.commons.codec.binary Base64]))

;;; It is so stupid that there isn't an easy to use library for this...

;;; TODO Make the rest of this configurable so usable with providers other than google
(defn oauth2-params
  []
  {:google
   {:authorize-uri    "https://accounts.google.com/o/oauth2/auth"
    :access-token-uri "https://accounts.google.com/o/oauth2/token"
    :client-id        (config/config :oauth :client-id)
    :client-secret    (config/config :oauth :client-secret)
    :scopes           ["https://www.googleapis.com/auth/userinfo.email"]
    :launch-uri       "/oauth2/google"
    :redirect-uri     (config/config :oauth :callback)
    :landing-uri      "/authenticated"
    }})

;;; Urls that do not require login. 
;;; Also used by basic-auth code
(def open-uris
  #{"/oauth2/google"
    "/oauth2/google/callback"
    "/login"
    "/img/google-signin.png"
    "/health"
    })

(defn open-uri?
  [uri]
  ((set/union
    open-uris
    (config/config :open-uris))
   uri))

(defn base64-json->
  [base64-str]
  (-> base64-str
      Base64/decodeBase64
      (String. Charsets/UTF_8)
      (json/read-str :key-fn keyword)))

(defn- parse-jwt
  [token]
  ;; Could also include header and/or verify signature
  (let [[_header payload _signature] (str/split token #"\.")]
    (base64-json-> payload)))

(defn wrap-jwt
  [handler]
  (fn [request]
    (handler
     (if (open-uri? (:uri request))  ; Open (allowed) URI
       request
       (if-let [token (get-in request [:oauth2/access-tokens :google :id-token])]
         (let [_expires [get-in request [:oauth2/access-tokens :google :expires]]
               claims (parse-jwt token)
               now (/ (System/currentTimeMillis) 1000)]
           (if (< (:iat claims) now (:exp claims)) 
             (assoc request :oauth2/claims claims)
             (response/redirect "/login"))) ; Token expired
         request)))))

;;; Code is what the user copies off the Google Sign in page
;;; Memoization makes this sticky so user only has to authenticate once.
(u/defn-memoized validate-oauth-code
  [code]
  (:body
   (client/post
    "https://www.googleapis.com/oauth2/v4/token"
    {:as :json
     :form-params {:code code
                   :redirect_uri "urn:ietf:wg:oauth:2.0:oob"
                   :client_id (config/config :oauth :client-id)
                   :client_secret (config/config :oauth :client-secret)
                   :scope ""
                   :grant_type "authorization_code" }})))

(defn wrap-oauth-code
  [handler]
  (fn [request]
    (let [code (get-in request [:params :oauth-code])
          token (and code (validate-oauth-code code))]
      (handler
       (if token
         (assoc-in request [:oauth2/access-tokens :google]
                   {:token (:access_token token)
                    :id-token (:id_token token)
                    ;; :expires ...
                    })
         request)))))

(defn wrap-enforce-login
  [handler responder]
  (fn [request]
    (let [oauth-email (get-in request [:oauth2/claims :email])] ;TODO removed config option here, I don't remember what it was used for...
      (cond (open-uri? (:uri request))  ; Open (allowed) URI
            (handler request)
            oauth-email                 ; This request is supplying identity (or simulation thereof)
            (handler (assoc-in request [:login :email] oauth-email)) ; add info to request
            :else                                                    ; No id
            (responder request)         ; call the responder (which can (eg) return an error response)
            ))))

(defn wrap-oauth
  [handler]
  (if (config/config :oauth :client-id)
    (-> handler
        (wrap-enforce-login (fn [req]
                              (response/set-cookie
                               (response/redirect "/login")
                               "way_landing" 
                               (:uri req) ;TODO this leaves out query params, see enflame for better way
                               {:same-site :lax :path "/"}
                               )))
        wrap-jwt                                  ;has to come before (that is, after) wrap-oauth2
        (wrap-oauth2 (oauth2-params))
        wrap-oauth-code
        )
    handler))
