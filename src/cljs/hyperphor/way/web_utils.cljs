(ns hyperphor.way.web-utils
  (:require [clojure.string :as str]
            [re-frame.core :as rf]
            [re-frame.db :as rf-db]
            [cemerick.url :as url]
            [hyperphor.way.cutils :as cu]
            [hyperphor.multitool.core :as u]
            )
  )

;;; Web utils


;;; ⦿⦾⦿ local storage (browser) ⦿⦾⦿⦾⦿⦾⦿⦾⦿⦾⦿⦾⦿⦾⦿⦾⦿⦾⦿⦾⦿⦾⦿⦾⦿⦾⦿⦾⦿⦾⦿⦾⦿⦾⦿⦾⦿⦾⦿⦾⦿⦾⦿⦾⦿⦾⦿⦾⦿⦾⦿⦾⦿⦾⦿⦾⦿⦾⦿

(defn get-local-storage
  [key]
  (.getItem js/localStorage key))

(defn set-local-storage
  [key value]
  (.setItem js/localStorage key value))

(defn doc-icon
  [url & [popup]]
  (cu/icon
   "help_outline"
   (or popup "documentation")
   #(.open js/window url "_doc")))

(defn url-decode
  [s]
  (js/decodeURIComponent s))

(defn browser-url
  []
  (url/url (-> js/window .-location .-href)))

(defn browser-pathname
  []
  (-> js/window .-location .-pathname))

(defn browser-path
  []
  (str/split (subs (browser-pathname) 1) "/"))

(defn browser-params
  "Parse query string parameters from a URL"
  []
  (let [query-string (-> js/window .-location .-search (subs 1))]
    (->> (str/split query-string #"&")
         (map #(str/split % #"=" 2))
         (reduce (fn [acc [k v]]
                   (assoc acc 
                          (keyword (url-decode k))
                          (url-decode (or v ""))))
                 {}))))


(def none-value "--none--")             ;Need a non-nil value to mean none.

(defn select-widget-options
  [options prompt]
  (let [options (if prompt
                  (cons {:value none-value :label (str "<" prompt ">")}
                        options)
                  options)
        ]
    (doall
     (for [option options]
       (let [option (if (map? option) option {:value option :label (name option)})
             {:keys [value label optgroup options]} option
             label (or label value)
             k (or value "nil")]
         (if optgroup
           [:optgroup {:label optgroup}
            (select-widget-options options)]
           ;; No, I think we want this enabled and meaning nil :disabled (nil? value)
           ^{:key k} [:option {:value value } label]))))))

;;; TODO option for allowing prompt=nil to be selectable 
(defn select-widget
  "Render a select widget.
  id: HTML id
  value: the current value (typically a rf/subscribe)
  dispatch: fn to call with new value (typically a rf/dispatch)
  options: a seq, each elt is either:
     - a string 
     - a map of the form {:value ... :label ...}  
     - a map of the form {:optgroup <name> :options <seq>}
  prompt: a value to display initially before a value is selected, and can be used to choose nil
    Note: select widget doesn't have a separate prompt, so gets added to the option list.
    If you don't want nil to be selectable, omit prompt (yes prompt is complecting a couple of things, but it works out)
  "
  [id value dispatch options prompt & [disabled? styles]]
  [:select.selector.form-control        ;TODO form selector is heavy weight and not always wanted
   {:on-change #(do
                  (.remove (.-classList (.-target %)) "font-italic")
                  (let [value (-> % .-target .-value)
                        value (if (= value none-value) nil value)]
                    (dispatch value)))
    :value (and value (str value))
    :id id
    :class (if (= none-value value) "font-italic" "")
    :style styles
    :disabled disabled?}
    (select-widget-options options prompt)
    ])

;;; More capable widgets
(rf/reg-event-db
 ::edit
 (fn [db [_ key edit?]]
   (assoc-in db [:page-state ::widgets key :editing] edit?)))
   
(rf/reg-sub
 ::editing?
 (fn [db [_ key]]
   (get-in db [:page-state ::widgets key :editing])))

(rf/reg-sub
 ::edited-value
 (fn [db [_ key]]
   (get-in db [:page-state ::widgets key :value])))

(rf/reg-event-db
 ::set-edited-value
 (fn [db [_ key value]]
   (assoc-in db [:page-state ::widgets key :value] value)))

;;; TODO maybe replace with re-com
;;; https://re-com.day8.com.au/#/input-text
(defn editable-text-widget-flex
  "The most abstract form of editable-text-widget. normal is a fn that displays the non-edited value. See editable-text-widget"
  [key {:keys [initial-value normal change-event validator normal-class borderless?] :as options}]
  (if @(rf/subscribe [::editing? key])
    (let [normal-class (or normal-class (when borderless? "borderless") "editable-text")
setter #(let [value @(rf/subscribe [::edited-value key])]
          (if (and validator (not (validator value)))
            (rf/dispatch [:error "Not a valid URL"]) ;TODO should be parameterizable
            (do
              (rf/dispatch (conj change-event value))
              (rf/dispatch [::edit key false])
              (rf/dispatch [:error nil])
              )))]
      [:span
       [:input.form-control-static
        {:value @(rf/subscribe [::edited-value key])
         :on-change (fn [evt]
                      (let [new-value (-> evt .-target .-value)]
                        (rf/dispatch [::set-edited-value key new-value])))
         :on-key-press (fn [evt]
                         (when (= "Enter" (.-key evt))
                           (setter)))
         }]
       (cu/icon "done" "save" setter :class "md-light")
       (cu/icon "cancel" "cancel" #(rf/dispatch [::edit key false]) :class "md-light")])
    [:span normal-class (normal)
     (cu/icon "create" "edit" #(do
                              (rf/dispatch [::set-edited-value key initial-value])
                              (rf/dispatch [::edit key true]))
            :class "md-light")]))

(defn editable-text-widget
  "Generate editable text widget, with controls
  key: A keyword to identify this in the db
  initial-vlue:
  change-event: an event vector, the value will be appended"
  [key {:keys [initial-value change-event] :as options}]
  (editable-text-widget-flex
   key
   (assoc options
          :normal #(or initial-value [:i {:style {:color "gray"}} "no value"])
          )))

(defn validate-url [u]
  (re-matches #"https?://.*" u))

(defn editable-url-widget
  "Generate editable url widget, with controls
  key: A keyword to identify this in the db
  initial-vlue:
  change-event: an event vector, the value will be appended"
  [key {:keys [initial-value link-text label change-event] :as options}]
  (editable-text-widget-flex
   key
   (assoc options
          :normal #(if initial-value
                     [:a {:href initial-value :target "#"} (or link-text label)]
                     [:i {:style {:color "gray"}} "no value"])
          :borderless? true
          :validator validate-url
          )))

(defn editable-text-widget-form
  "Same as editable-text-widget but formatted for a 2-column form"
  [key {:keys [label] :as options}]
  [:div.form-group.row
   (when label
     [:label.col-form-label.col-sm-2 {:for key} label])
   [:div.col-sm-10
    {:id key}
    (editable-text-widget key options)
    ]])

(defn editable-url-widget-form
  "Same as editable-url-widget but formatted for a 2-column form"
  [key {:keys [label] :as options}]
  [:div.form-group.row
   (when label
     [:label.col-form-label.col-sm-2 {:for key} label])
   [:div.col-sm-10
    {:id key}
    (editable-url-widget key options)
    ]])

;;; Very similar to above
;;; TODO needs extensions for enter key
(defn editable-textarea-widget
  [key initial-value change-fn]
  [:div
   {:id key}
   (if @(rf/subscribe [::editing? key])
     [:span
      [:textarea.form-control-static 
       {:value @(rf/subscribe [::edited-value key])
        :on-change (fn [evt]
                     (let [new-value (-> evt .-target .-value)]
                       (rf/dispatch [::set-edited-value key new-value])))
        :style {:width "90%"}
        }]
      (cu/icon "done" "save" #(do (change-fn @(rf/subscribe [::edited-value key]))
                               (rf/dispatch [::edit key false]))
            :class "md-light")
      (cu/icon "cancel" "cancel" #(rf/dispatch [::edit key false])
            :class "md-light")]
     [:div.editable-text
      (or initial-value [:i {:style {:color "gray"}} "no value"])
      (cu/icon "create" "edit" #(do
                               (rf/dispatch [::set-edited-value key initial-value])
                               (rf/dispatch [::edit key true]))
            :class "md-light")])])


;;; For debugging only – pull a value out the Re-frame database
(defn static-ref
  [path]
  (get-in @rf-db/app-db path))

(comment 
  (def default-time-format "MMMM dd, YYYY 'at' H:mm a")
  (def short-date-format "MM/dd/YY")
  (def short-time-format "MM/dd/YY H:mm")

  (defn format-time [instant & [format]]
    (let [format (or format default-time-format)]
      (and instant
           (cljs-time.format/unparse
            (cljs-time.format/formatter format)
            (cljs-time.coerce/to-local-date-time instant)))))
  )

(defn keyify
  "This adds :key metadata to any elt of seq that can have it.
  Use this to cure 'Warning: Every element in a seq should have a unique :key:' warning messages"
  [seq root]
  (map (fn [elt i]
         (if (satisfies? IMeta elt)
           (with-meta elt {:key (str root i)})
           elt))
       seq (range)))

(defn spinner
  "Make a spinner. Size 10 is big, size 1 or 2 is good"
  [& [size]]
  (let [size (or size 10)]
    ;; [:div.text-center
    [:div.spinner-border {:role "status"
                          :style {:width (str size "em")
                                  :height (str size "em")
                                  :flex-shrink 0
                                  :border-width (str (/ size 10.0) "em")}}
     [:span.visually-hidden "Loading..."]]))

(defn open-in-browser-tab
  [url name]
  (let [win (.open js/window name)]
    (.assign (.-location win) url)))

;;; Format for cljs
;;; → Multitool
;;; Not quite right eg if %s is at start or end
(defn js-format
  [s & args]
  (apply str
         (u/intercalate (str/split s #"%s")
                        args)))



;;; TODO here for back compatibility, should be replaced with version in cutils
(defn humanize
  [term]
  (when term
    (-> term
        name
        (str/replace "_" " "))))

(defn set-page-title
  [s]
  (set! (.-title (.-document js/window)) s))


;;; This stuff is from Wayne (aka BRUCE)

(defn open-popout
  [url & {:keys [width height id] :as args :or {id "_blank"}}]
  ;; Note: all the =no stuff doesn't work due to modern browser security features, oh well
  ;; TODO sizes seem to work radically differntly on Brave vs Safari. Not sure what's up with that.
  (.open js/window
         url
         id                       ; default _blank to make a new window each time
         (u/expand-template "resizable=no,toolbar=no,location=no,directories=no,status=no,menubar=no,scrollbars=no,height={{height}},width={{width}}"
                            (merge {:width 500 :height 500} args))))

(defn popout-button
  [url & {:keys [label] :as args :or {label "Popout"}}]
  [:button {:type "button" :title label
            :on-click #(open-popout url args)}
   label]
  )

(defn info
  [text]
  [:span {:data-tooltip text}
   [:img.info
    {:src "../img/help-icon.png"
     }]])

(defn show-image
  [id show?]
  (let [elt (.getElementById js/document id)]  
    (if show?
      (.remove (.-classList elt) "collapsed")
      (.add (.-classList elt) "collapsed"))))

(defn img-info
  [img width height]
  (let [id (gensym)]
    ;; Id is used bogth for popup image and popout window...just to be confusing. Should maybe use a more stable name for window to avoid popout proliferation problems
    [:span 
     [:img.info
      {:src  "../img/help-icon.png"
       :on-click #(open-popout img :width width :height height :id id)       ;TODO  title
       :on-mouse-over #(show-image id true)
       :on-mouse-out #(show-image id false)
       }]
     [:img.img-popup.collapsed {:src img :id id :height height}]]))
