(ns com.hyperphor.way.feeds
  (:require [re-frame.core :as rf]
            [com.hyperphor.way.api :as api]
            [reagent.dom]
            [org.candelbio.multitool.core :as u]
            )
  )

;;; Feed theory:
;;;;; Simple case: 
;;; Apps get data by @(rf/subscribe :data-id <params>)
;;; Backend managed data/data method.
;;;;; More complex case:
;;;  @(rf/subscribe [:data-id <s1>..] <params>)
;;; Here the id is a vector, so you can have more than one of a type. 


;;; → Multitool, maybe, kind of silly. Would make more sense if you could include lists or generators in a path.
(defn assoc*
  [m k v]
  (if (= k :*)
    (u/map-values (fn [_] v) m)
    (assoc m k v)))

(defn assoc-in*
  [m [k & ks] v]
  (if ks
    (assoc* m k (assoc-in* (get m k) ks v))
    (assoc* m k v)))

;;; Data id theory
;;; [:id {param-map}]

(rf/reg-sub
 :loading?
 (fn [db _]
   (:loading? db)))

;;; Does the actual fetch, using parameters from the database
;;; TODO that is ugly and unmodular, should be fixed.
;;;    Or is it? Kind of declarative, fits in with react dataflow model
(rf/reg-event-db
 :fetch
 (fn [db [_ data-id params]]
   (api/api-get
    "/data"
    {:params (assoc params :data-id data-id)
     :handler #(rf/dispatch [::loaded data-id params %])
     :error-handler #(rf/dispatch [:data-error data-id %1]) ;Override standard error handler
     })
   (-> db
       (assoc :loading? true)
       (assoc-in [:data-status data-id] :fetching)
       (assoc-in [:data-params data-id] params))))

(rf/reg-event-db
 :data-error
 (fn [db [_ data-id message]]
   (rf/dispatch [:flash (if message
                        {:class "alert-danger" :message message}
                        {:show? false})])
   (-> db
       (assoc-in [:data-status data-id] :error)
       (assoc-in [:data-status-error data-id] message) ;TODO nothing looks at this yet
       (assoc :loading? false))))

(rf/reg-event-db
 :fetch-once
 (fn [db [_ data-id params]]
   (when-not (get-in db [:data data-id])
     (rf/dispatch [:fetch data-id params]))))  ;TODO seems wrong? it shoudl be [:fetch [data-id]] I think?

;;; Changing: data-id is a keyword or vector, params is a map and optional
(rf/reg-sub
 :data
 (fn [db [_ data-id params]]
   (let [data (or (get-in db [:data data-id]) [])]
     (let [status (get-in db [:data-status data-id])
           last-params (get-in db [:data-params data-id])
           invalid? (or (= status :invalid) (not (= params last-params)))]
       (when invalid?
         (rf/dispatch [:fetch data-id params]))
       data))))

;;; Any adjustments to downloaded data. Called after the data is inserted into db, returns db
(defmulti postload (fn [db id data] id))

(defmethod postload :default
  [db id data]
  db)

(rf/reg-event-db
 ::loaded
 (fn [db [_ data-id params data]]
   (if (= params (get-in db [:data-params data-id]))
     (-> db
       (assoc-in [:data data-id] data)
       (assoc-in [:data-status data-id] :valid) ;not necessarily, UI could have changed while we were loading!
       (assoc :loading? false)
       (postload data-id data)
       )
     (do
       ;; TODO 
       (prn :timing-issue? data-id params (get-in db [:data-params data-id]))
       db)
     )))

(defn from-url
  [url]
  (when url
    @(rf/subscribe [:data [:url url]])))

(defmulti fetch identity)

(defmethod fetch :default
  [data-id]
  (rf/dispatch [:fetch data-id]))
