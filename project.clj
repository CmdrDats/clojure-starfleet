(defproject
  clojure-starfleet "0.1.0-SNAPSHOT"
  :description "Learn and teach Clojure and rank up with alien powers!"
  :url "http://www.github.com/CmdrDats/clojure-starfleet"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies
  [[org.clojure/clojure "1.7.0-beta2"]
   [org.clojure/core.async "0.1.346.0-17112a-alpha"]
   [org.clojure/clojurescript "0.0-3269"]

   [environ "1.0.0"]
   [joda-time/joda-time "2.7"]
   [yuppiechef/cqrs-server "0.1.4"]
   [com.datomic/datomic-free "0.9.5153" :exclusions [org.slf4j/slf4j-nop org.slf4j/log4j-over-slf4j]]
   [com.taoensso/faraday "1.6.0" :exclusions [joda-time]]
   [org.quartz-scheduler/quartz "2.2.1" :exclusions [org.slf4j/slf4j-api]]
   [org.danielsz/system "0.1.7"]


   ;; Logging, meh
   [org.clojure/tools.logging "0.3.1"]
   [ch.qos.logback/logback-classic "1.1.3"]

   ;; Web deps
   [http-kit "2.1.18"]
   [ring/ring-core "1.3.2"]
   [ring/ring-defaults "0.1.5"]
   [compojure "1.3.4"]
   [com.taoensso/sente "1.4.1"]

   ;; NRepl dep
   [org.clojure/tools.nrepl "0.2.10"]

   ;; Clojurescript deps.
   [org.omcljs/om "0.8.8"]
   [kioo "0.4.0"]]
  :plugins
  [[lein-cljsbuild "1.0.5"]
   [lein-figwheel "0.3.1"]]
  :profiles
  {:dev
   {:dependencies
    [[ring/ring-devel "1.3.2"]]}}
  :clean-targets ^{:protect false} ["resources/public/js/compiled" "target"]
  :cljsbuild
  {:builds [{:id "dev"
             :source-paths ["cljs/"]
             :figwheel
             {}
             :compiler
             {:main starfleet.main
              :asset-path "js/compiled/out"
              :output-to "resources/public/js/compiled/starfleet.js"
              :output-dir "resources/public/js/compiled/out"
              :optimizations :none
              :source-map true
              :source-map-timestamp true
              :cache-analysis true}}]}
  :figwheel
  {:css-dirs ["resources/public/css"]
   :nrepl-port 7888}
  :main starfleet.command
  )
