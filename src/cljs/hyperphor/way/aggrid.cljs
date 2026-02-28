(ns hyperphor.way.aggrid
  (:require [reagent.core :as reagent]
            [re-frame.core :as rf]
            ["ag-grid-community" :as ag]
            ["ag-grid-enterprise" :as age]
            ["ag-grid-react" :as agr]
            [hyperphor.multitool.core :as u]
            [hyperphor.way.web-utils :as wu]
            [clojure.string :as str]
            )
  #_ (:require-macros
      [hyperphor.way.macros :refer (ag-grid-license)]))

;;; Wrapping of ag-grid. 

;;; For export: mouse-right context menu works. To make more prominent affordance, see https://www.ag-grid.com/javascript-data-grid/csv-export/#reference-export-exportDataAsCsv

(def ag-adapter (reagent/adapt-react-class agr/AgGridReact))

;;; TODO not working, fix
(comment
  (def license-key (ag-grid-license))

;;; Supposed to be done once, thus this top-level call.
(when license-key
  (.setLicenseKey age/LicenseManager license-key)))

;;; Keep pointers to API objects for various purposes. This maps a keyword id to the appropriate API object.
;;; Note: this doesn't survive a figwheel reload. Maybe store in the re-frame db instead?
(def ag-apis (atom {}))

(defn jsx->clj
  [x]
  (into {} (for [k (.keys js/Object x)] [(keyword k) (aget x k)])))

;;; TODO needs some work
(defmulti ag-col-def (fn [col {:keys [url-template multiple?]:as col-def}]
                       (if (or url-template multiple?)
                         :html
                         :default)))

;;; Change default to really just use default ag-grid machinery, will save a world of pain
(defmethod ag-col-def :default
  [col _]
  {:headerName (name col)
   :field col
   }
  )

;;; :html, supoorts url-template, multiple values with count. (TODO split those out? But might want both behaviors)
;;; url-templates using %s format (TODO use u/expand-template)
;;; multiple values (with count)
(defmethod ag-col-def :html
  [col {:keys [url-template] :as col-def}]
  {:headerName (name col)
   :field col
   :cellRenderer (fn [params]
                   (let [value (get (js->clj (.-data params) :keywordize-keys true) col)
                         render (if url-template
                                  (fn [v] [:a.ent {:href (wu/js-format url-template v) :target "_ext"} (js/decodeURIComponent (str v))])
                                  (fn [v] [:span (str v)]))]
                     (reagent.core/as-element 
                      [:span.ag-cell-wrap-text   ;; .ag-cell-auto-height doesn't work, unfortunately.
                        (if (vector? value)
                          [:span
                           (when (> (count value) 1)
                             [:span.count (count value)])
                           (for [elt (butlast value)]
                             [:span (render elt) ", "])
                           (render (last value))]
                          (render value))])))
   }
  )

(defn ag-table
  "data: a seq of maps
  id: a keyword to identify this table
  columns: seq of column ids
  ag-grid-options: a map of values passed directly to ag-grid
  checkboxes?: control whether checkboxes appear, defaults true
  class: css class to use for grid
  col-defs: a map of col ids to maps. Fields are the standard ag-grid plus:
     :url-template : a format string from values to URL links
     :multiple? : support multiple elements, with count and truncation
"
  [data & {:keys [checkboxes? autosize? class col-defs id columns ag-grid-options]}]
  (let [columns (or columns (keys (first data)))
        id (or id (gensym "ag"))
        column-defs (mapv #(ag-col-def % (get col-defs %)) columns)]
    [:div.ag-container {:class class}
     [:div {:className "ag-theme-balham"}
      (let [grid-options
            (merge                     ;merges grid options, optional detail grid options, and user supplied options
             {:defaultColDef {:sortable true
                              :filter "agTextColumnFilter" ; TODO have type-specific filters, see https://www.ag-grid.com/javascript-grid-filtering/#configuring-filters-to-columns
                              :resizable true
                              :minWidth 55 ;TODO(ag-grid) autoSize doesn't seem to respect this, it should
                              }
              :onGridReady (fn [params]
                             (swap! ag-apis assoc id (.-api params)))
              :onFirstDataRendered (when autosize?
                                     (fn [params]
                                       (let [api (.-api params)] 
                                         (.autoSizeAllColumns api))))
              :columnDefs column-defs
              :rowData data
              :onColumnHeaderClicked (fn [params]
                                         (let [col (jsx->clj (.-column params))]
                                         (rf/dispatch [:col-select (:colId col)])))
              :pagination true
              :paginationAutoPageSize true
              :sideBar {:hiddenByDefault false ; visible but closed
                        :toolPanels [{:id "columns"
                                      :labelDefault "Columns"
                                      :labelKey "columns"
                                      :iconKey "columns"
                                      :toolPanel "agColumnsToolPanel"
                                      ;; Turning these off. If turned back on, also need to set :enablePivot true in default col def
                                      :toolPanelParams {:suppressRowGroups true
                                                        :suppressValues true
                                                        :suppressPivots true 
                                                        :suppressPivotMode true}
                                      }
                                     {:id "filters"
                                      :labelDefault "Filters"
                                      :labelKey "filters"
                                      :iconKey "filter"
                                      :toolPanel "agFiltersToolPanel"
                                      }]
                        }
              :animateRows true
              :statusBar {:statusPanels [{:statusPanel "agTotalAndFilteredRowCountComponent"
                                          :align "left"}]}
              }
             ag-grid-options)]
        ;; debug tool, for reporting config to ag-grid.com
        ;;         (print (.stringify js/JSON (clj->js grid-options)))
        [ag-adapter grid-options])
      ]]))

