(def sdk-version "1.9.64")

(defproject com.alpeware/jaq-services "0.1.0-SNAPSHOT"
  :description "JAQ - Bringing Clojure to Google App Engine"
  :url "http://www.alpeware.com"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :local-repo ".m2"
  :dependencies [[org.clojure/clojure "1.9.0"]

                 [org.clojure/data.json "0.2.6"]
                 [org.clojure/tools.logging "0.4.0"]
                 [clj-http-lite "0.3.0"]
                 [com.cognitect/transit-clj "0.8.300"]]

  :aot [jaq.services.deferred]

  :plugins [[com.alpeware/lein-jaq "0.1.0-SNAPSHOT"]]

  :jaq {:sdk-version ~sdk-version})
