(ns hyperphor.way.views.login
  (:require [environ.core :as env]
            [hyperphor.way.views.html :as html]
            [hyperphor.way.config :as config]
            [hiccup.util :as hu]
            )
  )

(defn login-view
  []
  (html/html-frame
   {:page :login}
   "Login"
   [:div.black
    [:div.login-panel.p-4
     [:table
      [:tr
       [:td
        [:h4 (or (config/config :oauth :signin-text)
                 (config/config :app-title))]]
       [:td
        [:div {:style (html/style-arg {:margin-left "60px"}) }
         [:a {:href "/oauth2/google"}
          [:img {:src "/img/google-signin.png"}]]]]]]]]))

