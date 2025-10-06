(ns com.hyperphor.way.material
  (:require [re-frame.core :as rf]
            [reagent.core :as reagent]
            ["@mui/material" :as m]
           )
  )

(def button-adapter (reagent/adapt-react-class m/Button))
(def slider-adapter (reagent/adapt-react-class m/Slider))
(def stack-adapter (reagent/adapt-react-class m/Stack))

(defn view
  []
  [:div
   [button-adapter {:variant "contained"} "Hello World"] ;why is this uppercased?
   (let [slider-v @(rf/subscribe [:form-field-value [:material :slider]])]
    [:div {:style {:width 400}}         ;or Box but why bother
     [slider-adapter {:valueLabelDisplay "on"
                      :value (or slider-v [10, 20])
                      :onChange (fn [_ v]
                                  (pr v)
                                  (rf/dispatch [:set-form-field-value [:material :slider] (js->clj v)])
                                  )}]
     ]
    )])
