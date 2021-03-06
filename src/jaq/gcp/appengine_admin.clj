(ns jaq.gcp.appengine-admin
  (:refer-clojure :exclude [list])
  (:require
   [clojure.data.json :as json]
   [clojure.data.xml :as xml]
   [clojure.tools.logging :as log]
   [clojure.java.io :as io]
   [clj-http.lite.client :as http]
   [clojure.string :as string]
   [jaq.gcp.storage :as storage]
   [jaq.services.util :as util]))

(def service-name "appengine.googleapis.com")
(def api-endpoint (str "https://" service-name))
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

(defn contains-file? [file-vec file-name]
  (->> file-vec
       (filter (fn [[f _]]
                (= f file-name)))
       (empty?)
       (not)))

(defn static-file [url-regex file-path path-regex]
  {:urlRegex url-regex
   :staticFiles {:path file-path
                 :uploadPathRegex path-regex
                 :applicationReadable false}})

(defn app-handlers [file-vec servlet]
  (let [index-file-path "WEB-INF/classes/public/index.html"
        favicon-file-path "WEB-INF/classes/public/favicon.ico"
        public-folder-regex "WEB-INF/classes/public/(.*)"
        handlers [{:urlRegex "/public/(.*)"
                   :staticFiles {:path "WEB-INF/classes/public/\\1"
                                 :uploadPathRegex "WEB-INF/classes/public/(.*)"
                                 :applicationReadable false}}
                  {:urlRegex "/.*"
                   :script {:scriptPath servlet}}]]
    {:handlers (concat (when (contains-file? file-vec index-file-path)
                         (static-file "/" index-file-path public-folder-regex))
                       (when (contains-file? file-vec favicon-file-path)
                         (static-file "/" favicon-file-path public-folder-regex))
                       handlers)}))

#_(
   *ns*
   (require 'jaq.gcp.appengine-admin)
   (in-ns 'jaq.gcp.appengine-admin)

   (let [code-bucket "staging.alpeware-jaq-runtime.appspot.com"
         code-prefix "apps/v33"]
     (->> (get-files code-bucket code-prefix)
          (count)))
   )

(defn app-definition [file-vec
                      {:keys [service version servlet env-vars defaults]
                       :or {servlet "servlet"
                            env-vars {}}}]
  (let [dep (deployment file-vec)
        defaults (merge
                  (if (= service :default)
                    (app-defaults version servlet)
                    (service-defaults version servlet))
                  defaults)
        handlers (app-handlers file-vec servlet)
        env-variables {"envVariables" env-vars}]
    (merge defaults dep handlers env-variables)))

(defn deploy-app [{:keys [project-id service code-bucket code-prefix version servlet
                          env-vars defaults] :as params}]
  (let [file-vec (get-files code-bucket code-prefix)
        app-def (app-definition file-vec params)]
    (deploy project-id service app-def)))
