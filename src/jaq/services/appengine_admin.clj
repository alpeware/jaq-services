(ns jaq.services.appengine-admin
  (:refer-clojure :exclude [list])
  (:require
   [clojure.data.json :as json]
   [clojure.data.xml :as xml]
   [clojure.tools.logging :as log]
   [clojure.java.io :as io]
   [clj-http.lite.client :as http]
   [clojure.string :as string]
   [jaq.services.storage :as storage]
   [jaq.services.util :as util]))

(def api-endpoint "https://appengine.googleapis.com")
(def api-version "v1beta")
(def default-endpoint [api-endpoint api-version])
(def action (partial util/action default-endpoint))

(defn app [project-id]
  (action :get [:apps project-id]))

(defn create [project-id location-id]
  (action :post [:apps] {:content-type :json
                         :body (json/write-str {"id" project-id
                                                "locationId" location-id})}))

(defn services [app-id]
  (action :get [:apps app-id :services]))

(defn service [name]
  (action :get [name]))

(defn versions [name]
  (action :get [name :versions]))

(defn version [name]
  (action :get [name] {:query-params {:view "FULL"}}))

(defn instances [name]
  (action :get [name :instances]))

(defn instance [name]
  (action :get [name]))

(defn debug [name]
  (action :get [(str name ":debug")]))

(defn locations [project-id]
  (action :get [:apps project-id :locations]))

(defn deploy [project-id service app-map]
  (action :post
          [:apps project-id :services service :versions]
          {:content-type :json
           :body (json/write-str app-map)}))

(defn operation [name]
  (action :get [name]))

(defn migrate [project-id service version]
  (action :post
          [:apps project-id :services service]
          {:headers {"X-HTTP-Method-Override" "PATCH"} ;; https://stackoverflow.com/a/32503192/7947020
           :content-type :json
           :query-params {:updateMask "split"}
           :body (json/write-str {:split {:allocations {version "1"}}})}))

(defn delete [project-id service & [version]]
  (if (some? version)
    (action :delete [:apps project-id :services service :versions version])
    (action :delete [:apps project-id :services service])))

;; helpers
(defn deployment [file-vec]
  {:deployment
   {:files
    (->> file-vec
         (map (fn [[file-name {:keys [selfLink]}]]
                {file-name {:sourceUrl selfLink}}))
         (into {}))}})

(defn get-files [bucket prefix]
  (->> (storage/objects bucket {:prefix prefix})
       (filter (fn [f]
                 (not (string/ends-with? (:name f) "/"))))
       (map (fn [f]
              (let [path (:name f)
                    file-name (string/replace path (str prefix "/") "")
                    url (str "https://storage.googleapis.com/" bucket "/" path)]
                [file-name {:selfLink url}])))))

;; deployments
(defn app-defaults [version servlet]
  {:id version
   :runtime "java8"
   :threadsafe true
   :automaticScaling {:maxConcurrentRequests 80
                      :maxIdleInstances 1
                      :maxPendingLatency "15s"
                      :minIdleInstances 0
                      :minPendingLatency "10s"}})

(defn service-defaults [version servlet]
  {:id version
   :runtime "java8"
   :threadsafe true
   :basicScaling {:maxInstances 1}
   :instanceClass "B1"})

(defn app-handlers [file-vec servlet]
  {:handlers [{:urlRegex "/public/.*"
               :staticFiles {:path "WEB-INF/classes/public/\1"
                             :uploadPathRegex "WEB-INF/classes/public/.*"
                             :requireMatchingFile false
                             :applicationReadable true}}
              {:urlRegex "/.*"
               :script {:scriptPath servlet}}]})

(defn app-definition [service file-vec version servlet]
  (let [dep (deployment file-vec)
        defaults (if (= service :default)
                   (app-defaults version servlet)
                   (service-defaults version servlet))
        handlers (app-handlers file-vec servlet)]
    (merge defaults dep handlers)))

(defn deploy-app [project-id service bucket prefix version servlet]
  (let [file-vec (get-files bucket prefix)
        app-def (app-definition service file-vec version servlet)]
    (log/info app-def)
    (deploy project-id service app-def)))
