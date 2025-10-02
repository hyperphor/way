(ns com.hyperphor.way.tabs
  (:require [re-frame.core :as rf]
            [com.hyperphor.way.nav :as nav]
            [com.hyperphor.way.cutils :as cu]
   ))

;;; manages any kind of tabbed ui, or top level pages

;;; TODO belongs elsewhere
(rf/reg-sub
 :page
 (fn [db _]
   (:page db)))

(defn tabs
  "Define a set of tabs. id is a keyword, tabs is a map (array-map is best to preserve order) mapping keywords to ui fns "
  [id tabs]
  (let [active (or @(rf/subscribe [:active-tab id])
                    (ffirst tabs))]      ;Default to first tab 
    [:div
     [:ul.nav.nav-tabs
      (for [[name view] tabs]
        ^{:key name}
        [:li.nav-item
         (if name
           [:a.nav-link {:class (when (= name active) "active")
                         :on-click #(rf/dispatch [:choose-tab id name])}
            (cu/humanize name)]
           [:a.nav-link.disabled.vtitle view])])]
     (when active
       ((tabs active)))]))

(defn tabs-nav
  "Define a set of tabs. id is a keyword, tabs is a map (array-map is best to preserve order) mapping keywords to ui fns. Uses :set-route so URL updates."
  [id tabs base-route]
  (let [route @(rf/subscribe [:route])
        active (or (first route)
                   (ffirst tabs))]      ;TODO use base-route
    [:div
     [:ul.nav.nav-tabs
      (for [[name view] tabs]
        ^{:key name}
        [:li.nav-item
         (if name
           [:a.nav-link {:class (when (= name active) "active")
                         :on-click #(rf/dispatch [:set-route (conj base-route name)])}
            (cu/humanize name)]
           [:a.nav-link.disabled.vtitle view])])]
     (when active
       ((tabs active)))]))

(rf/reg-sub
 :active-tab
 (fn [db [_ id]]
   (get-in db [:active-tab id])))

;;; Multimethod to handle tab initialization
;;; Can return an upddated db or nil
(defmulti set-tab (fn [id tab db] [id tab]))

(defmethod set-tab :default
  [id tab db]
  (prn "no set-tab for" [id tab]))      ;Not an error, this might be very normal

(rf/reg-event-db
 :choose-tab
 (fn [db [_ id tab]]
   (let [ndb (set-tab id tab db)]
     (assoc-in (or ndb db) [:active-tab id] tab))))

