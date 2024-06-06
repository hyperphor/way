(ns hyperphor.way.views.html
  (:require [clojure.string :as str]
            [hyperphor.way.config :as config]
            [environ.core :as env]
            )
  (:use [hiccup.core]))

;;; can't believe this isn't built into hiccup
(defn style-arg
  [m]
  (str/join (map (fn [[p v]] (format "%s: %s;" (name p) v)) m)))

(defn html-flatten
  [html]
  (if (string? html)
    html
    (str/join " " (filter string? (flatten html)))))

(defn nav-item
  [page & params]
  [:li.nav-item 
   [:a.nav-link.u {:href #_ (apply cnav/url-for page params) "bogus"} ;TODO
    (name page)
    ]])

(defn old-nav-item
  [name url active?]
  [:li.nav-item {:class (when active? "active")}
   [:a.nav-link {:href url}
    name
    ]])

(defn home-link []
  [:a {:href "/"} "Home"])              ;TODO should be customizable

(defn html-frame
  [{:keys [page]} title contents]
  ;; should be a template I suppose but this was faster
  (html
   [:html
    [:head
     [:title (html-flatten title)]
     [:meta {:charset "UTF-16"}]
     [:link {:href "https://fonts.googleapis.com/icon?family=Material+Icons"
             :rel "stylesheet"}]
     [:link {:href "https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css"
             :rel "stylesheet"
             :integrity "sha384-QWTKZyjpPEjISv5WaRU9OFeRpok6YctnYmDr5pNlyT2bRjXh0JMhjY6hW+ALEwIH"
             :crossorigin "anonymous"}]
     [:link {:rel "stylesheet"
             :href "/css/way.css"}]   ;TODO
     [:link {:href "https://fonts.googleapis.com/css2?family=Roboto&display=swap"
             :rel"stylesheet"}]
     [:link {:href "https://fonts.googleapis.com/icon?family=Material+Icons"
             :rel "stylesheet"}]
     ]
    [:body 
     [:div.header
      [:div.header-ic]
      [:h1.titles (home-link) "/" title]
      #_
      (when-let [email (login/user)]
        [:span "Hello, " email ])
      #_ cnav/pici
      (when-not (= page :login)
        [:nav.navbar.navbar-expand-lg.bg-dark.navbar-dark
         [:ul.navbar-nav.mr-auto
          (nav-item :home)
          ]])
      ]
     [:div.container.main
      contents]
     [:script {:src "https://code.jquery.com/jquery-3.5.1.slim.min.js"
               :integrity "sha384-DfXdz2htPH0lsSSs5nCTpuj/zy4C+OGpamoFVy38MVBnE+IbbVYUew+OrCXaRkfj"
               :crossorigin "anonymous"}]
     [:script {:src "https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/js/bootstrap.bundle.min.js"
               :integrity "sha384-YvpcrYf0tY3lHB60NNkmXc5s9fDVZLESaAA55NDzOxhy9GkcIdslK1eN7N6jIeHz"
               :crossorigin "anonymous"}]]]))

(defn app-url []
  (format "/cljs-out/%s-main.js"
          "dev" ; TODO tier
          ))
(defn app
  []
  [:script {:src (app-url)}])

(defn app-html
  []
  [:script {:src (app-url)}])

(defn html-frame-spa
  []
  (html
   [:html
    [:head
     [:title (config/config :app-title)]
     [:meta {:charset "UTF-16"}]
     [:link {:href "https://fonts.googleapis.com/icon?family=Material+Icons"
             :rel "stylesheet"}]
     [:link {:href "https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css"
             :rel "stylesheet"
             :integrity "sha384-QWTKZyjpPEjISv5WaRU9OFeRpok6YctnYmDr5pNlyT2bRjXh0JMhjY6hW+ALEwIH"
             :crossorigin "anonymous"}]
     [:link {:rel "stylesheet" :href "/css/way.css"}] ;TODO
     #_ [:link {:rel "stylesheet" :href "/css/re-com.css"}]
     ;; Seems to not work with bootstrap?
     [:link {:href "https://fonts.googleapis.com/icon?family=Material+Icons"
             :rel "stylesheet"}]
     [:link {:href "/css/ag-grid/ag-grid.css"
             :rel "stylesheet"}]
     [:link {:href "/css/ag-grid/ag-theme-balham.css"
             :rel "stylesheet"}]
     ]

    [:body {:height 5000}              ;TODO prevents aannoying scroll behavior, but clearly not the right thing
     [:div#app]
     (app-html)
     [:script {:src "https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/js/bootstrap.bundle.min.js"
               :integrity "sha384-YvpcrYf0tY3lHB60NNkmXc5s9fDVZLESaAA55NDzOxhy9GkcIdslK1eN7N6jIeHz"
               :crossorigin "anonymous"}]
     [:script (format "window.onload = function() { %s(); }"
                      (config/config :app-main ))]
     ]]))




