(ns hyperphor.way.modal
  (:require
   [re-frame.core :as rf]
   [hyperphor.way.web-utils :as wu]))

;;; Modal popups.


(rf/reg-event-db
 :modal
 (fn [db [_ data]]
   (assoc-in db [:modal] data)))

(rf/reg-sub
 :modal
 (fn [db _] (:modal db)))

;;; Based on
;;; https://github.com/benhowell/re-frame-modal/blob/master/modal.cljs
(defn- close-modal
  []
  (rf/dispatch [:modal {:show? false :contents nil}]))

;;; This "pops out" the main window, which will be counterintuitive, although still useful
;;; TODO this isn't really the right thing, because there is some user-set state (scrolling, checkmarks, etc) that isn't captured in the new window. Think about redoing this.
(defn popout
  [_]
  (wu/open-in-browser-tab  (.-href (.-location js/window))
                           "_popout"))

;;; TODO ok-text and ok-enabled? should default better
(defn- modal-contents
  [{:keys [title contents ok-handler ok-text ok-enabled? popout?] :as foo}]
  [:div.modal-content.panel-danger
   [:div.modal-header.panel-heading
    title
    [:span.close                        ;floats right
     (when popout?
       [:button {:type "button" :title "Popout"
                 :on-click #(popout %)}
        [:i.material-icons "open_in_new"]])
     [:button {:type "button" :title "Cancel"
               :on-click #(close-modal)}
      [:i.material-icons "close"]]]]
   [:div.modal-body
    [contents]
    ]
   [:div.modal-footer
    (when ok-handler
      [:button.btn.btn-primary {:type "button" :title "OK"
                                :disabled (not (ok-enabled?))
                                :on-click ok-handler} ok-text])]])

(defn modal-panel
  [{:keys [show?] :as modal-state}]
  [:div.modal-wrapper
   [:div.modal-backdrop
    {:on-click (fn [event]
                 (rf/dispatch [:modal {:show? (not show?)
                                       :contents nil
                                       }])
                 (.preventDefault event)
                 (.stopPropagation event))}]
   [:div.modal-child
    (modal-contents modal-state)
    ]])

(defn modal
  []
  (let [modal (rf/subscribe [:modal])]
    [:div
     (when (:show? @modal)
       [modal-panel @modal])]))


;;; Still ahs ops stuff, but should have something conveniet
#_
(defn expose-modal
  [op]
  (rf/dispatch [:modal {:show? true
                        :title (or (:label op) (:id op))
                        :contents contents
                        :ok-handler (fn [] (rf/dispatch [:do-op op]))
                        :ok-enabled? (fn [] true)
                        :ok-text (cond (:sheet-producer op) "Create sheet" ;TODO ops should be able to specify
                                       :else "Add column")
                        }
                (:params op)
                ]))
