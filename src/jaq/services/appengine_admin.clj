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

(defn deploy [project-id app-map]
  (action :post
          [:apps project-id :services :default :versions]
          {:content-type :json
           :body (json/write-str app-map)}))

(defn operation [name]
  (action :get [name]))

(defn migrate [project-id version]
  (action :post
          [:apps project-id :services :default]
          {:headers {"X-HTTP-Method-Override" "PATCH"} ;; https://stackoverflow.com/a/32503192/7947020
           :content-type :json
           :query-params {:updateMask "split"}
           :body (json/write-str {:split {:allocations {version "1"}}})}))

(defn delete [project-id version]
  (action :delete [:apps project-id :services :default :versions version]))

;; helpers
(defn different-keys? [content]
  (when content
    (let [dkeys (count (filter identity (distinct (map :tag content))))
          n (count content)]
      (= dkeys n))))

(defn xml->json [element]
  (cond
    (nil? element) nil
    (string? element) element
    (sequential? element) (if (> (count element) 1)
                            (if (different-keys? element)
                              (reduce into {} (map (partial xml->json ) element))
                              (map xml->json element))
                            (xml->json  (first element)))
    (and (map? element) (empty? element)) {}
    (map? element) (if (:attrs element)
                     #_{(:tag element) (xml->json (:content element))
                        (keyword (str (name (:tag element)) "Attrs")) (:attrs element)}
                     {(:tag element) (xml->json  (:content element))}
                     {(:tag element) (xml->json  (:content element))})
    :else nil))

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
                      :minPendingLatency "10s"}
   :handlers [{:urlRegex "/.*"
               :script {:scriptPath servlet}}]})

(defn app-definition [file-vec version servlet]
  (let [dep (deployment file-vec)
        defaults (app-defaults version servlet)
        #_app-json #_(-> appengine-web
                         (xml/parse-str)
                         (xml->json)
                         :appengine-web-app
                         (select-keys [:application :runtime :version :threadsafe :automatic-scaling])
                         )]
    (merge
     defaults
     #_(into
        {}
        (map (fn [[k v]]
               [(get {:application :name
                      :version :id} k k) v])
             app-json))
     dep)))

;; TODO(alpeware): call as deferred
(defn deploy-app [project-id bucket prefix version servlet]
  (let [;;file-vec (storage/copy dir bucket prefix)
        file-vec (get-files bucket prefix)]
    (deploy project-id (app-definition file-vec version servlet))))
