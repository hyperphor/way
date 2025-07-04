(defproject com.hyperphor/way "0.1.16" 
  :description "Way"
  :license {:name "EPL-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :deploy-repositories [["clojars" {:sign-releases false}]]
  :plugins [[lein-shadow "0.4.1"]]
  :dependencies [[org.clojure/clojure "1.12.0"]
                 [org.candelbio/multitool "0.1.11"]
                 [org.clojure/data.json "2.5.1"]
                 [environ "1.2.0"]
                 [com.taoensso/timbre "6.5.0"]
                 [aero "1.1.6"]

                 ;; Backend
                 [clj-http "3.13.0" :exclusions [commons-io]]
                 [compojure "1.7.1"]
                 [ring "1.14.1"]
                 [ring/ring-core "1.14.1"]
                 [ring/ring-defaults "0.6.0"]
                 [ring/ring-jetty-adapter "1.14.1"]
                 [ring-basic-authentication "1.2.0"]
                 [ring-logger "1.1.1"]
                 [ring-middleware-format "0.7.5" :exclusions [javax.xml.bind/jaxb-api]]
                 [ring-oauth2 "0.3.0"]  ;TODO upgraded version, need to verify it works
                 [ring/ring-codec "1.3.0"]

                 ;; Data
                 [clj-http "3.13.0" :exclusions [commons-io]]
                 [org.clojure/data.csv "1.1.0"]

                 ;; frontend
                 ;; See packge.json for the real dependencies
                 #_ [org.clojure/clojurescript "1.11.132"] ;causes shadow-cljs error, who knows
                 [thheller/shadow-cljs "3.1.4"] ;TODO maybe only in dev profile
                 [reagent "1.2.0"]
                 [re-frame "1.4.3"]
                 [com.cemerick/url "0.1.1"]
                 ;; TODO for navigation when that works
                 ;; [bidi "2.1.6"]     
                 ;; [kibu/pushy "0.3.8"]
                 [cljs-ajax "0.8.4"]
                 [day8.re-frame/tracing "0.6.2"]      ;TODO dev only
                 [day8.re-frame/re-frame-10x "1.9.9"] ;TODO dev only

                 ]
  :source-paths ["src/cljc" "src/clj" "src/cljs"]
  :test-paths ["test/cljc" "test/clj" "test/cljs"] 
  :clean-targets ^{:protect false} ["target" ".shadow-cljs" "resources/public/cljs-out"]

  :profiles {:uberjar {;; :aot [com.hyperphor.way.demo.core]
                       ;; :omit-source true
                       :prep-tasks [["shadow" "release" "npm"] "javac" "compile"] ;NOTE if you omit the javac compile items, :aot stops working!  "javac" "compile"
                       ;; TEMP removed to try to shrink jar file
                       :resource-paths ["resources"]
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"]
                       }}

  :shadow-cljs {:lein true
                :builds
                {:app {:target :browser
                       :compiler-options {:infer-externs true}
                       :output-dir "resources/public/cljs-out"
                       :asset-path "/cljs-out"         ;webserver path
                       :modules {:dev-main {:entries [com.hyperphor.way.demo.app]}}
                       :devtools {:preloads [day8.re-frame-10x.preload.react-18]}
                       :dev {:compiler-options
                             {:closure-defines
                              {re-frame.trace.trace-enabled?        true
                               day8.re-frame-10x.show-panel         false ;does not work, afaict
                               day8.re-frame.tracing.trace-enabled? true}}}}}}
  )
