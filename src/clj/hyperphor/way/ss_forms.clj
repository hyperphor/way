(ns hyperphor.way.ss-forms
  (:require
            [hyperphor.multitool.core :as u]
            [hyperphor.way.cutils :as cu]
            [clojure.string :as str]
            ))

;;; Server-side forms

;;; This is a copied-and-modified version of the cljs form code. They should be consolidated, but I don't have the time now


;;; Create a UI for a form field.
;;; Args:
;;;   type: keyword UI type, used for dispatch
;;;   path: keyseq identifying the value
;;;   label: optional
;;;   id: optional (HTML id)
;;;   hidden?
;;;   disabled?
;;;   doc (with optional HTML) 
;;;   editable? 
;;;   render    


;;; TODO optionally supply alzabo type and use its schema (for doc esp.)


(defmulti form-field (fn [{:keys [type path label id hidden? disabled? doc] :as args}]
                       type))

;;; form-field read-only, not to be confused with form-field-row
(defmulti form-field-ro (fn [{:keys [type] :as args}]
                          type))

(defmethod form-field-ro :default
  [{:keys [value render] :or {render str}}] ;TODO other methods need to respect render
  (render value))

;;; Note: need to turn on Show Scroll Bars in Mac System Prefs
;;; this might fix https://codepen.io/ppolyzos/pen/AWKgOv
(defmethod form-field-ro :textarea
  [{:keys [value max-height] :or {max-height "240px"}}]
  [:div.withlinebreaks.overflow-auto {:style (cu/style-arg {:max-height max-height})} value])

;;; Note: not actually specialized at all, so maybe doesnt need to be a method
(defmulti form-field-row (fn [{:keys [type path label id hidden? disabled? doc] :as args}]
                           type))

;;; TODO want a slightly more abstract validation mechanism
(defmulti form-field-warnings (fn [{:keys [type path label id hidden? disabled? doc] :as args}]
                                type))

(defmethod form-field-warnings :default
  [_]
  nil)

#_
(defmethod form-field-warnings :number
  [{:keys [path]}]
  (let [value @(rf/subscribe [:form-field-value path])]
    (cond (nil? value) nil
          (number? value) nil
          (empty? value) nil
          :else [:span.alert.alert-warning "Value must be numeric"])))

(defmethod form-field-row :default
  [{:keys [type path label id hidden? disabled? doc editable?] :as args}]
  (let [label (or label (cu/humanize (name (last path))))
        id (or id (str/join "-" (map name path)))]
    [:div.row {:class (if hidden? "d-none" nil)}
     [:label.col-sm-2.col-form-label {:for id} label]
     [:div.col-8
      (if editable?
        (form-field (assoc args :id id :label label))
        (form-field-ro (assoc args :id id :label label)))
      ]
     [:div.col-sm-2.form-field-doc (or (form-field-warnings args) doc)]
     ]))

;;; TODO propagate this to other methods. Really need :before, maybe I should switch to methodical
#_
(defn init?
  [path value init]
  (when (and init (not value)) (rf/dispatch [:set-form-field-value path init])))

(defmethod form-field :default
  [{:keys [type path label id hidden? disabled? value-fn style init value on-change] :as args :or {value-fn identity }}]
    [:input.form-control
     {:id id
      :name id
      :style style
      :value value
      ;;    :disabled false
      :on-change on-change
      }])

;;; TODO restrict keys or show warning if content is not legal number
(defmethod form-field :number
  [{:keys [] :as args}]
  (form-field (assoc args :type :default :value-fn u/coerce-numeric)))

(defmethod form-field :textarea
  [{:keys [type path label id hidden? disabled? value-fn style value on-change] :as args :or {value-fn identity width "100%"}}]
  [:textarea.form-control
   {:id id
    :name id
    :style style
;    :disabled false
    :on-change on-change
    :rows 10
    }
   value])

;;; TODO Rich text with BlockType


;;; TODO producing react warnings
(defmethod form-field :boolean
  [{:keys [path id read-only doc type hidden value on-change]}]
  [:input.form-check-input
   {:name id
    :type "checkbox"
    :checked value
    :disabled read-only
    :on-change on-change}])

(defn set-element
  [s elt in?]
  ((if in? conj disj)
   (or s #{})
   elt))

(defmethod form-field :set
  [{:keys [path elements id read-only doc type hidden style value on-change] :as f}]
  [:div
   (doall 
    (for [elt elements
          :let [id (str/join "_" (list id (name elt)))]]
      [:span.form-check.form-check-inline
       {:style style}
       [:label.form-check-label {:for id} (name elt)]
       [:input.form-check-input
        {:name id
         :type "checkbox"
         :checked (contains? value elt)
         :disabled read-only
         :on-change on-change}
        ]]))])

(defmethod form-field-ro :set
  [{:keys [value]}] 
  (str/join ", " (map name value)))

;;; See radio-button groups https://getbootstrap.com/docs/5.3/components/button-group/#checkbox-and-radio-button-groups
(defmethod form-field :oneof
  [{:keys [path elements id read-only doc type hidden style init value on-change]}]
  [:div
   (doall
    (for [elt elements]
      [:span.form-check.form-check-inline
       {:style style}
       [:label.form-check-label {:for id} (name elt)]
       [:input.form-check-input
        {:name id
         :value elt
         :type "radio"
         :checked (= elt value)
         :disabled read-only
         :on-change on-change}
        ]]))])

(def none-value "--none--")             ;Need a non-nil value to mean none.

(defn select-widget-options
  [options prompt value]
  (let [options (if prompt
                  (cons {:value none-value :label (str "<" prompt ">")}
                        options)
                  options)
        ]
    (doall
     (for [option options]
       (let [option (if (map? option) option {:value option :label (name option)})
             option (if (= value (:value option)) (assoc option :selected true) option)
             {:keys [value label optgroup options selected]} option
             label (or label value)
             k (or value "nil")]
         (if optgroup
           [:optgroup {:label optgroup}
            (select-widget-options options prompt value)]
           ;; No, I think we want this enabled and meaning nil :disabled (nil? value)
           ^{:key k} [:option {:value value :selected selected} label]))))))

;;; TODO option for allowing prompt=nil to be selectable 
(defn select-widget
  "Render a select widget.
  id: HTML id
  value: the current value (typically a rf/subscribe)
  options: a seq, each elt is either:
     - a string 
     - a map of the form {:value ... :label ...}  
     - a map of the form {:optgroup <name> :options <seq>}
  prompt: a value to display initially before a value is selected, and can be used to choose nil
    Note: select widget doesn't have a separate prompt, so gets added to the option list.
    If you don't want nil to be selectable, omit prompt (yes prompt is complecting a couple of things, but it works out)
  "
  [id value dispatch options prompt on-change & [disabled? styles]]
  [:select.selector.form-control        ;TODO form selector is heavy weight and not always wanted
   {:on-change on-change
    ; :value (and value (str value))      ;TODO not working
    :id id
    :name id
    :class (if (= none-value value) "font-italic" "")
    :style styles
    :disabled disabled?}
    (select-widget-options options prompt value)
    ])

;;; TODO option processing, labels/hierarchy etc.
;;; TODO might need to translate from none-value to nil
(defmethod form-field :select
  [{:keys [path read-only doc hidden? options id width value on-change]}]
  (let [disabled? false]
    (select-widget id value on-change options nil disabled?)))

;;; TODO multiselect



;;; For upload
#_
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


#_
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



;;; react multiselect widget
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
 

;;; TODO, maybe condense fields
;;; TODO or use the same trick the pprint thing does...

;;; TODO separate non-SPA for files??
;;; TODO name-prefix or something for making sure fields are unique
;;; Or, just edit single tasks separately, why not?
(defn wform
  [object fields edit?]
  [:div.wform
   (map #(form-field-row
          (assoc %
                 :value (get-in object (:path %))
                 :editable? (and edit? (not (:read-only? %)))))
        fields)])

(defn aform
  [object fields & {:keys [action edit? cancel? submit] :or {submit "Save" cancel? true}}]
  (if edit?
    [:form {:action action :method :post}
     [:input.btn.btn-primary {:type :submit :value submit}]
     (when cancel? [:a.btn.btn-secondary {:href "?mode=view"} "Cancel"])
     (wform object fields edit?)]
    [:div
     [:div
      (cu/icon "edit" "?mode=edit" "Edit")
      (wform object fields edit?)
      ]]))












