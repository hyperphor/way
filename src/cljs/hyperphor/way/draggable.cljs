(ns hyperphor.way.draggable
  (:require [re-frame.core :as rf]
            [reagent.core :as reagent]
            ["react-draggable" :as drag :default Draggable]
           )
  )

;;; https://www.npmjs.com/package/react-draggable
;;; Mostly Claude generated (eg all the localStorage stuff is not something I'd do)

(def draggable-adapter (reagent/adapt-react-class drag/default)) ;/Draggable would make more sense, but this is js

;; Event to update widget position
(rf/reg-event-db
  :draggable/update-position
  (fn [db [_ widget-id x y]]
    (prn :update-position widget-id x y)
    (assoc-in db [:draggable/positions widget-id] {:x x :y y})))

;; Event to load layout
(rf/reg-event-db
  :draggable/load-layout
  (fn [db [_ positions]]
    (prn :load-layout positions)
    (assoc db :draggable/positions positions)))

;; Event to save layout to localStorage
(rf/reg-event-fx
  :draggable/save-layout
  (fn [{:keys [db]} [_ layout-name]]
    (let [current-positions (get db :draggable/positions {})
          saved-layouts (get db :draggable/saved-layouts {})
          updated-layouts (assoc saved-layouts layout-name current-positions)]
      (js/localStorage.setItem "draggable-layouts" (js/JSON.stringify (clj->js updated-layouts)))
      (js/console.log "Layout saved:" layout-name)
      {:db (assoc db :draggable/saved-layouts updated-layouts)})))

;; Event to load saved layouts from localStorage
(rf/reg-event-db
  :draggable/load-saved-layouts
  (fn [db _]
    (if-let [stored (.getItem js/localStorage "draggable-layouts")]
      (try
        (let [layouts (js->clj (js/JSON.parse stored) :keywordize-keys true)]
          (assoc db :draggable/saved-layouts layouts))
        (catch js/Error e
          (js/console.log "Error loading layouts:" e)
          db))
      db)))

;; Event to delete layout
(rf/reg-event-fx
  :draggable/delete-layout
  (fn [{:keys [db]} [_ layout-name]]
    (let [updated-layouts (dissoc (get db :draggable/saved-layouts {}) layout-name)]
      (js/localStorage.setItem "draggable-layouts" (js/JSON.stringify (clj->js updated-layouts)))
      {:db (assoc db :draggable/saved-layouts updated-layouts)})))

;; Subscription for widget position
(rf/reg-sub
  :draggable/position
  (fn [db [_ widget-id]]
    (get-in db [:draggable/positions widget-id])))

;; Subscription for all positions
(rf/reg-sub
  :draggable/positions
  (fn [db _]
    (get db :draggable/positions {})))

;; Subscription for saved layouts
(rf/reg-sub
  :draggable/saved-layouts
  (fn [db _]
    (get db :draggable/saved-layouts {})))

;; Initialize saved layouts on load
(rf/dispatch [:draggable/load-saved-layouts])

;;; Draggable widget with title bar and markdown content
(defn draggable-widget 
  [{:keys [id title content initial-x initial-y width height]}]
  (let [saved-pos @(rf/subscribe [:draggable/position id])
        start-x (or (:x saved-pos) initial-x 0)
        start-y (or (:y saved-pos) initial-y 0)]
    [draggable-adapter 
     {:handle ".drag-handle"
      :position {:x start-x :y start-y}
      :onStop (fn [e data]
                ;; Save position when dragging stops
                (rf/dispatch [:draggable/update-position id (.-x data) (.-y data)]))}
     [:div.card {:style {:width (or width "300px")
                         :height (or height "auto")
                         :background "white"
                         :border "1px solid #ccc"
                         :border-radius "4px"
                         :box-shadow "0 2px 4px rgba(0,0,0,0.1)"
                         :font-family "system-ui, sans-serif"}}
      
      ;; Title bar (draggable handle)
      [:div.drag-handle {:style {:background "#f5f5f5"
                                 :border-bottom "1px solid #ddd"
                                 :padding "8px 12px"
                                 :cursor "move"
                                 :user-select "none"
                                 :border-radius "4px 4px 0 0"
                                 :font-weight "bold"}}
       title]
      
      ;; Content area
      [:div.content {:style {:padding "12px"
                             :overflow "auto"
                             :max-height (when height (str "calc(" height " - 40px)"))}}
       content]]]))
