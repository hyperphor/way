(defproject com.hyperphor/way "0.1.21" 
  :description "Way"
  :license {:name "EPL-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :deploy-repositories [["clojars" {:sign-releases false}]]
  :plugins [[lein-shadow "0.4.1"]]
  :dependencies [[org.clojure/clojure "1.12.3"]
                 [org.candelbio/multitool "0.1.12"]
                 [org.clojure/data.json "2.5.1"]
                 [environ "1.2.0"]
                 [com.taoensso/timbre "6.5.0"]
                 [aero "1.1.6"]

                 ;; Backend
                 [hato "1.0.0"]         ;replaces clj-http
                 [cheshire "6.1.0"]    ;necessary for json parse
                 [me.raynes/fs "1.4.6"]

                 [compojure "1.7.2"]
                 [ring "1.15.3"]
                 [ring/ring-core "1.15.3"]
                 [ring/ring-defaults "0.7.0"]
                 [ring/ring-jetty-adapter "1.15.3"]
                 [ring-basic-authentication "1.2.0"]
                 [ring-logger "1.1.1"]
                 [ring-middleware-format "0.7.5" :exclusions [javax.xml.bind/jaxb-api]]
                 #_ [ring-oauth2 "0.3.0"]  ;TODO upgraded version, need to verify it works
                 [ring/ring-codec "1.3.0"]

                 ;; Data
                 [clj-http "3.13.0" :exclusions [commons-io]]
                 [org.clojure/data.csv "1.1.0"]
                 [io.github.nextjournal/markdown "0.7.186"] 

                 ;; frontend
                 ;; See packge.json for the real dependencies
                 [thheller/shadow-cljs "3.1.4"] ;TODO maybe only in dev profile
                 [reagent "1.2.0"]
                 [re-frame "1.4.3"]
                 [com.cemerick/url "0.1.1"]
                 [cljs-ajax "0.8.4"]
                 [secretary/secretary "1.2.3"] ;TODO perhaps not necessary
                 [venantius/accountant "0.2.5"]
                 ]
  :source-paths ["src/cljc" "src/clj" "src/cljs"]
  :test-paths ["test/cljc" "test/clj" "test/cljs"] 
  :clean-targets ^{:protect false} ["target" ".shadow-cljs" "resources/public/cljs-out"]

  :jar-exclusions [#".*public/css/node.*"
                   #".*public/css/ag-grid.*"]

  :profiles {
             :dev {:dependencies [[thheller/shadow-cljs "3.1.7"]
                                  [day8.re-frame/tracing "0.6.2"]     
                                  [day8.re-frame/re-frame-10x "1.9.9"]]}}

  :shadow-cljs {:lein true
                ;; No builds, see way-demo 
                }
  )
