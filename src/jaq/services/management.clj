(ns jaq.services.management
  (:refer-clojure :exclude [list])
  (:require
   [clojure.data.json :as json]
   [clojure.tools.logging :as log]
   [clojure.java.io :as io]
   [clj-http.lite.client :as http]
   [clojure.string :as string]
   [jaq.services.util :as util]
   [jaq.services.storage :as storage]))

(def api-endpoint "https://servicemanagement.googleapis.com")
(def api-version "v1")
(def default-endpoint [api-endpoint api-version])
(def action (partial util/action default-endpoint))

(defn services [& [{:keys [pageToken pageSize] :as params}]]
  (lazy-seq
   (let [resp (action :get [:services] {:query-params params})
         next-token (:nextPageToken resp)]
     (concat
      (:services resp)
      (when next-token
        (services (assoc params :pageToken next-token)))))))

(defn service [service-name]
  (action :get [:services service-name]))

(defn enable [service-name project-id]
  (action :post
          [:services (str service-name ":enable")]
          {:content-type :json
           :body (json/write-str {:consumerId (str "project:" project-id)})}))

(defn config [service-name]
  (action :get [:services service-name :config] {:query-params {:view "FULL"}}))

(defn configs [service-name & [{:keys [pageToken pageSize] :as params}]]
  (lazy-seq
   (let [resp (action :get [:services service-name :config] {:query-params params})
         next-token (:nextPageToken resp)]
     (concat
      (:serviceConfigs resp)
      (when next-token
        (configs (assoc params :pageToken next-token)))))))

(defn operation [name]
  (action :get [name]))

#_(
   (defn operation [name]
         {:done true})
   )
