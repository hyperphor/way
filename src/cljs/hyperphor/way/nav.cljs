(ns hyperphor.way.nav
  (:require 
   [re-frame.core :as rf]
   [hyperphor.multitool.core :as u]
   [accountant.core :as accountant]
   [secretary.core :as secretary :refer-macros [defroute]]
   [clojure.string :as str]
   )
  )


;;; Theory:
;;; Route is in rf db as a sequence
;;; Components can cdr down this sequence, maybe by dynamic binding. Right now I just need one level working

;;; Status: not quite right but at least it works for the simple case


(defn nav-to
  [url]
  (accountant/navigate! url)
  #_ (secretary/dispatch! url))


(rf/reg-sub
 :route
 (fn [db _]
   (:route db)))                        ;Warning: init sets this to {:handler ...}, that should be fixedb

(defn route->url
  [route]
  (str/join "/" (map name route)))

(defn url->route
  [url]
  (mapv keyword (remove u/nullish? (str/split url #"/" ))))

(defn set-route
  [db route]
  (if (= route (:route db))
    db
    (let [url (route->url route)]
      (nav-to url)
      (assoc db
             :route route
             :url url))))

(rf/reg-event-db
 :set-route
 (fn [db [_ route]]
   (set-route db route)
   ))

(rf/reg-event-db
 :set-path
 (fn [db [_ path]]
   (set-route db (url->route path))))

;;; TODO maybe events for going down or up hierarchy?

;; Initialize

(accountant/configure-navigation!
 {:nav-handler (fn [path]
                 (prn :nav-handler path)
                 #_ (rf/dispatch [:set-route (url->route path)]) ;AARDVARK
                 #_ (secretary/dispatch! path)
                 )
  :path-exists? (fn [path]
                  (prn :path-exists? path)
                  (secretary/locate-route path))})


(accountant/dispatch-current!)
