(ns com.hyperphor.way.form
  (:require [re-frame.core :as rf]
            [org.candelbio.multitool.core :as u]
            [clojure.string :as str]
            [com.hyperphor.way.web-utils :as wu]
            ))

;;; Status: carved out of traverse.ops, not yet integrated
;;; TODO param stuff should go through here. Or do we need both levels of abstraction?
;;; TODO way to supply extras or customizations
;;; TODO should probably incorporate component library like https://mantine.dev/
;;; TODO password field

(rf/reg-sub
 :form-field-value
 (fn [db [_ field]]
   (get-in (:form db) field)))

(rf/reg-event-db
 :set-form-field-value
 (fn [db [_ field value]]
   (assoc-in db (cons :form field) value)))

(rf/reg-event-db
 :update-form-field-value
 (fn [db [_ field f & args]]
   (apply update-in db (cons :form field) f args)))

;;; Create a UI for a form field.
;;; Args:
;;;   type: keyword UI type, used for dispatch
;;;   path: keyseq identifying the value
;;;   label: optional
;;;   id: optional (HTML id)
;;;   hidden?
;;;   disabled?
;;;   a docstring (with optional HTML) (TODO)

(defmulti form-field (fn [{:keys [type path label id hidden? disabled? doc] :as args}]
                       type))

;;; TODO want a slightly more abstract validation mechanism
(defmulti form-field-warnings (fn [{:keys [type path label id hidden? disabled? doc] :as args}]
                                type))

(defmethod form-field-warnings :default
  [_]
  nil)

(defmethod form-field-warnings :number
  [{:keys [path]}]
  (let [value @(rf/subscribe [:form-field-value path])]
    (cond (nil? value) nil
          (number? value) nil
          (empty? value) nil
          :else [:span.alert.alert-warning "Value must be numeric"])))

(defn form-field-row
  [{:keys [type path label id hidden? disabled? doc] :as args}]
  (let [label (or label (name (last path)))
        id (or id (str/join "-" (map name path)))]
    ^{:key (str "row" id)}
    [:div.row
     [:label.col-sm-2.col-form-label {:for id} label]
     [:div.col-8
      (form-field (assoc args :id id :label label))
      ]
     [:div.col-sm-2.form-field-doc (or (form-field-warnings args) doc)]
     ]))

;;; TODO propagate this to other methods. Really need :before, maybe I should switch to methodical
(defn init?
  [path value init]
  (when (and init (not value)) (rf/dispatch [:set-form-field-value path init])))

(defmethod form-field :default
  [{:keys [type path label id hidden? disabled? value-fn style init] :as args :or {value-fn identity }}]
  (let [value @(rf/subscribe [:form-field-value path])]
    (init? path value init)
    [:input.form-control
     {:id id
      :style style
      :value value
      ;;    :disabled false
      :on-change (fn [e]
                   (rf/dispatch
                    [:set-form-field-value path (value-fn (-> e .-target .-value))]))
      ;; TODO
      #_ :on-key-press #_ (fn [evt]
                            (when (= "Enter" (.-key evt))
                              nil))
      }]))

;;; TODO restrict chars
;;; TODO bug, you can't type "3." partly because weird parseFloat behavior...I think it means I need to store both string and parsed value in the db
(defmethod form-field :number
  [{:keys [] :as args}]
  (form-field (assoc args :type :default :value-fn u/coerce-numeric)))

(defmethod form-field :textarea
  [{:keys [type path label id hidden? disabled? value-fn style] :as args :or {value-fn identity width "100%"}}]
  [:textarea.form-control
   {:id id
    :style style
    :value @(rf/subscribe [:form-field-value path])
;    :disabled false
    :on-change (fn [e]
                 (rf/dispatch
                  [:set-form-field-value path (-> e .-target .-value)]))
    }])

;;; TODO Rich text with BlockType

;;; TODO producing react warnings
(defmethod form-field :boolean
  [{:keys [path id read-only doc type hidden]}]
  [:input.form-check-input
   {:id id
    :type "checkbox"
    :checked @(rf/subscribe [:form-field-value path])
    :disabled read-only
    :on-change (fn [e]
                 (rf/dispatch
                  [:set-form-field-value path (-> e .-target .-checked)]))}])

(defn set-element
  [s elt in?]
  ((if in? conj disj)
   (or s #{})
   elt))

(defmethod form-field :set
  [{:keys [path elements id read-only doc type hidden style]}]
  [:div
   (doall 
    (for [elt elements
          :let [id (str/join "-" (cons id (map name (conj path elt))))]]
      ^{:key (str "e" id)}
      [:span.form-check.form-check-inline
       {:style style}
       [:label.form-check-label {:for id} (name elt)]
       [:input.form-check-input
        {:id id
         :type "checkbox"
         :checked @(rf/subscribe [:form-field-value (conj path elt)])
         :disabled read-only
         :on-change (fn [e]
                      (rf/dispatch
                       [:update-form-field-value path set-element elt (-> e .-target .-checked)]))}
        ]]))])


;;; See radio-button groups https://getbootstrap.com/docs/5.3/components/button-group/#checkbox-and-radio-button-groups
(defmethod form-field :oneof
  [{:keys [path elements id read-only doc type hidden style init]}]
  (let [value @(rf/subscribe [:form-field-value path])]
    (init? path value init)
    [:div
     (doall
    (for [elt elements]
      ^{:key (str "e" id elt)}
      [:span.form-check.form-check-inline
       {:style style}
       [:label.form-check-label {:for id} (name elt)]
       [:input.form-check-input
        {:name id
         :type "radio"
         :checked (= elt value)
         :disabled read-only
         :on-change (fn [e]
                      (rf/dispatch
                       [:set-form-field-value path elt]))}
        ]]))]))

;;; TODO option processing, labels/hierarchy etc.
;;; TODO might need to translate from none-value to nil
(defmethod form-field :select
  [{:keys [path read-only doc hidden? options id width]}]
  (let [disabled? false
        value @(rf/subscribe [:form-field-value path])
        dispatch #(rf/dispatch [:set-form-field-value path %])]
    (wu/select-widget id value dispatch options nil disabled?)))

;;; TODO multiselect
;;; TODO select from a data feed (see https://github.com/mtravers/exobrain/blob/source-docs/src/cljs/exobrain/ui/forms.cljs#L174)

;;; For upload
(defmethod form-field :local-files
  [{:keys [id path read-only doc type hidden]}]
  [:input.form-control
   {:id id
    :type "file"
    :multiple true                    ;TODO should be an option
    :on-change (fn [e]
                 (rf/dispatch
                  ;; TODO wrong for multiple files? Argh
                  [:set-form-field-value path (-> e .-target .-value)]))
    }])


(defmethod form-field :local-directory
  [{:keys [id path read-only doc type hidden]}]
  [:input.form-control
   {:id id
    :type "file"
    :multiple true
    :webkitdirectory "true"               ;black magic to enable folder uploads
    :mozdirectory "true"
    :directory "true"
    :on-change (fn [e]
                 (rf/dispatch
                  ;; TODO wrong for multiple files? Argh
                  [:set-form-field-value path (-> e .-target .-value)]))    
    }])


;; --}{--}{--}{--}{--}{--}{--}{--}{--}{--}{--}{--}{--}{--}{--}{--}{--}{--}{--}{--}{--}{--}{--}{--}{--}{--}{--}{--}{--}{--}{--}{--}{--}{--}{--}{--}{--}{--}{--}{--}{--}{--}{--}{--}{--}{--}{--}{--}{--}{--}{--}{--}{--}{--}{--}{--}{--}{--}{--}{--}{--}{--}{--}{--}{



;;; React multiselect widget
;;; TODO make order editable. This is possible but hairy: https://github.com/reagent-project/reagent/blob/master/examples/react-sortable-hoc/src/example/core.cljs

#_ (def react-select (reagent/adapt-react-class js/Select))

#_
(defn multi-select-widget [name options selected-values]
  [react-select
   {:value             (filter (fn [{:keys [value]}]
                                 (some #(= value %) selected-values))
                               options)
    :on-change         (fn [selected]
                         (rf/dispatch [:set-form-field-value name (map :value (js->clj selected :keywordize-keys true))]))
    :is-clearable      true
    :is-multi          true
    :options           options
    :class-name        "react-select-container"
    :class-name-prefix "react-select"
    }])
 
;;; Assumes all :hidden fields are sequences from checkboxes, which is true for now
#_
(defn hidden-ui [{:keys [read-only doc type hidden] :as arg}]
  (let [arg-name (:name arg)
        value @(rf/subscribe [:form-field-value arg-name])]
    [:span.form-control.read-only
     (inflect/pluralize (count value) (inflect/singular (name arg-name))) " checked"]
    ))

;;; TODO, maybe condense fields
;;; TODO or use the same trick the pprint thing does...
(defn gather-fields
  [fields]
  (u/clean-map
   (into {}
         (for [field fields]
           (let [path (:path field)]
             [path @(rf/subscribe [:form-field-value path])])))))

;;; TODO separate non-SPA for files??
;;; TODO THis is completely broke, gather-fields calls subscribe which is not legal
(defn wform
  [fields action]
  [:div.wform                           ;Not :form, to prevent a page trnasition
   #_
   {:enc-type "multipart/form-data"
    :method "POST"}
   #_ (when doc                            ;TODO
        [:div.alert doc])
   (doall (map form-field-row fields))
   (when action
     [:button.btn.btn-primary {:type "submit" :on-click #(action (gather-fields fields))} "Submit"])])








