(ns hyperphor.way.views.eval
  (:require [ring.util.response :as response]
            [hyperphor.way.config :as config]
            [hyperphor.way.ss-forms :as forms]
            ))

;;; Danger will robinson
;;; TODO should maintain a replayale history (eg a repl)

(def eval-enabled? (atom false))
(def magic-hash 932508623)

(defn remote-eval
  [form magic]
  (str
   (try
     ;; Extra protection
     (assert (= (hash magic) magic-hash) "Needs more magic")
     ;; TODO result needs HTML quoting
     (eval (read-string form)) ;danger will robinson
     (catch Exception e
       {:status 500                     ;TODO this is wrong
        :headers {}
        :body (str e)}))))

(defn eval-page
  [req]
  (if (and
       (config/config :dev-mode)
       @eval-enabled?)
    (let [params (:params req)
          form (:form params)
          magic (:magic params)
          result (and form
                      (remote-eval form magic))
          ]
      [:div
       (forms/aform
        params
        [{:path [:form] :type :textarea} ;TODO make wider
         {:path [:magic]}
         ]
        :edit? true
        :submit "Eval"                  ;TODO put at bottom
        :cancel? false)

       (when result
         [:div
          [:h4 "Result"]
          [:pre result]])])
    [:h3 "Nothing to see here"]))


