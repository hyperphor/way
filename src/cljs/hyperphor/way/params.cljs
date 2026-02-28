(ns hyperphor.way.params
  (:require [re-frame.core :as rf]
            [hyperphor.way.cutils :as cu]
            [hyperphor.way.feeds :as f]
            [hyperphor.way.web-utils :as wu]
            ))

;;; TODO data-id here is treated different than feeds.

;;; TODO integrate with form

;;; TODO maybe generalize this pattern to -before, and other event handlers. 
(defmulti set-param-after (fn [db [_ data-id param value]] [data-id param]))

(defmethod set-param-after :default
  [db msg]
  db)

;;; Causes a new data fetch. TODO not always desired, need some flex here
(defn set-param
  [db [_ data-id param value :as msg]]
  (f/fetch data-id)                     ;TODO more sophisticated mechanism
  (set-param-after db msg)
  (-> (if (vector? param)                  ;? smell
        (assoc-in db (concat [:params data-id] param) value)
        (assoc-in db [:params data-id param] value))
      ))

(rf/reg-event-db
 :set-param
 set-param)

(rf/reg-event-db
 :set-param-if
 (fn [db [_ data-id param value :as msg]]
   (if (if (vector? param)
         (get-in db (concat [:params data-id] param))
         (get-in db [:params data-id param]))
     db
     (set-param db msg)
     )))

(rf/reg-event-db
 :update-param
 (fn [db [_ data-id param f & args]]
   (let [v (get-in db [:params data-id param])]
     (set-param db [:foo data-id param (apply f v args)])))) ;TODO :foo?

(rf/reg-sub
 :param
 (fn [db [_ data-id param]]
   (if (vector? param)
     (get-in db (concat [:params data-id] param))
     (get-in db [:params data-id param]))))

(rf/reg-sub
 :params
 (fn [db [_ data-id]]
   (get-in db [:params data-id])))

(defn safe-name
  [thing]
  (when thing
    (or (.-name thing)
        thing)))

;;; UI

;;; TODO use Boostrap horizontal form layout https://getbootstrap.com/docs/5.0/forms/layout/

(defn select-widget-parameter
  [data-id param-id values & {:keys [default extra-action]}]
  ;;; TODO didn't I mane this work better in Wayne? Thought I did...
  (when default                         ;TODO propagate to other param widgets
    (rf/dispatch [:set-param-if data-id param-id (safe-name default)])) 
  (wu/select-widget
   param-id
   @(rf/subscribe [:param data-id param-id])
   #(rf/dispatch [:set-param data-id param-id %])
   ;; TODO this breaks :optgroups
   (map (fn [v]
          {:value v :label (if v (cu/humanize v)  "---")})
        values)
   nil
   nil
   {:display "inherit" :width "inherit" :margin-left "2px"}))

;;; Abandoned, too many impossible issues with this
;;; - you can't tell it to NOT filter the options list based on current value
;;; - the option list suddenly became too narrow to be useful and no CSS kludging would fix it.  (On Chrome, works OK on Safari)
;;; TODO prob want to split into base in web-utils
#_
(defn combo-widget-parameter
  [data-id param-id values]
  (letfn [(valid? [v]
            (not (empty? v)))
          (update [evt]
            (let [v (-> evt .-target .-value)]
              (when (valid? v)
                (rf/dispatch [:set-param data-id param-id v]))))]
    
    [:span.parameter
     [:input.selector.form-control
      {:type "url"
       :id "foo"
       :style {:display "inline-block"
               :width #_ "500px" "100%"}
       :list (name param-id)
       ;; :on-click #(do (set! (.-value (-> % .-target)) ""))
       :on-blur update
       :on-select update
       }]
     [:button.btn.btn-secondary
      {:on-click  #(let [elt (.getElementById js/document "foo")]  (set! (.-value elt) "")) #_ #(rf/dispatch [:set-param data-id param-id ""])} "Clear" ]
     [:datalist {:id (name param-id) } (wu/select-widget-options values nil)]]
    ))

(defn param-value
  [data-id param-id]
  @(rf/subscribe [:param data-id param-id]))

(defn set-param-value
  [data-id param-id value]
  (rf/dispatch [:set-param data-id param-id value]))

(defn checkbox-parameter
  [data-id param-id & {:keys [label]}]
  [:span.parameter
   [:span.plabel label]
   [:input.form-check-input
    {:name param-id
     :id param-id
     :type "checkbox"
     :checked @(rf/subscribe [:param data-id param-id])
     :on-change (fn [e]
                  (rf/dispatch
                   [:set-param data-id param-id (-> e .-target .-checked)]))}
    ]])

(defn text-parameter
  [data-id param-id & {:keys [label]}]
  [:span.parameter
   [:span.plabel label]
   [:input.form-control
    {:name param-id
     :id param-id
     :value @(rf/subscribe [:param data-id param-id])
     :style {:display "inline"}         ;TODO 
     :on-change (fn [e]
                  (rf/dispatch
                   [:set-param data-id param-id (-> e .-target .-value)]))}
    ]])

