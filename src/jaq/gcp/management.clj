(ns jaq.gcp.management
  (:refer-clojure :exclude [list])
  (:require
   [clojure.data.json :as json]
   [jaq.services.util :as util]))

(def service-name "servicemanagement.googleapis.com")
(def api-endpoint (str "https://" service-name))
(def api-version "v1")
(def default-endpoint [api-endpoint api-version])
(def action (partial util/action default-endpoint))

(defn services [& [{:keys [pageToken pageSize] :as params}]]
  (lazy-seq
   (let [{:keys [nextPageToken error]
          services-list :services} (action :get
                                                        [:services]
                                                        {:query-params params})]
     (or
      error
      (concat
       services-list
       (when nextPageToken
         (services (assoc params :pageToken nextPageToken))))))))

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
   (let [{:keys [serviceConfigs nextPageToken error]} (action
                                                       :get
                                                       [:services service-name
                                                        :config]
                                                       {:query-params params})]
     (or
      error
      (concat
       serviceConfigs
       (when nextPageToken
         (configs (assoc params :pageToken nextPageToken))))))))

(defn operation [name]
  (action :get [name]))

#_(
   (defn operation [name]
     {:done true})
   )
