(ns com.hyperphor.way.cards
  (:require [re-frame.core :as rf]
            [org.candelbio.multitool.core :as u]
   ))

;;; Based on way/tabs (but logic is different) and should go back there

;;; manages any kind of carded ui, or top level pages

(defn cards
  "Define a set of cards. id is a keyword, cards is a seq of maps:
  name, view, header-extra, open?"
  [id cards]
  (let [default-open (map :name (filter :open? cards))
        default-open (set
                      (if (u/nullish? default-open)                               
                        [(:name (first cards))]
                        default-open))
        open? @(rf/subscribe [:cards id default-open])]
    [:div
     (for [{:keys [name view header-extra]} cards]
       ^{:key name}
       (when name                       ;Ignore null elements, makes conditionalizing easier
         [:div.card
          [:div.card-header
           [:h2.mb-0
            [:button.btn.btn-link {:type "button"
                                   :on-click #(rf/dispatch [:toggle-card id name])
                                   :style {:text-decoration "none"} ;TODO CSS
                                   :aria-expanded "true"
                                   :aria-controls "compacted"}
             name]
            ;; Having this stuff within :h2 is weird, but works mostly
            header-extra]]
          [:div {:aria-labelledby "compacted-head"
                 :class "show" #_ (if (open? name) "show" nil)
                 :id id}
           (when (open? name)
             [:div.card-body
              [view]])]]))]))

(rf/reg-sub
 :cards
 (fn [db [_ id default]]
   (or (get-in db [:cards id])
       default
       )))

;;; TODO in multitool as set-toggle
(defn toggle-elt
  [cardset card]
  (if cardset
    (if (cardset card)
      (disj cardset card)
      (conj cardset card))
    (set [card])))

(rf/reg-event-db
 :toggle-card
 (fn [db [_ id card-id]]
   (update-in db [:cards id] toggle-elt card-id)))

(rf/reg-event-db
 :set-cards
 (fn [db [_ id card-set]]
   (assoc-in db [:cards id] card-set)))

;;; → multiool I guess
(defn sconj
  [s elt]
  (conj (or s #{}) elt))

(rf/reg-event-db
 :open-card
 (fn [db [_ id card]]
   (update-in db [:cards id] sconj card)))

