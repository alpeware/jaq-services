(ns jaq.gcp.resource
  (:refer-clojure :exclude [list get])
  (:require
   [clojure.data.json :as json]
   [jaq.services.util :as util]))

(def service-name "cloudresourcemanager.googleapis.com")
(def endpoint (str "https://" service-name))
(def version "v1")
(def default-endpoint [endpoint version])
(def action (partial util/action default-endpoint))

(defn projects [& [{:keys [pageToken pageSize filter] :as params}]]
  (lazy-seq
   (let [{:keys [nextPageToken error]
          project-list :projects} (action
                                   :get
                                   [:projects]
                                   {:query-params params})]
     (or
      error
      (concat project-list
              (when nextPageToken
                (projects (assoc params :pageToken nextPageToken))))))))

(defn project [project-id]
  (action :get [:projects project-id]))

(defn delete [project-id]
  (action :delete [:projects project-id]))

(defn create [project-id name]
  (action :post [:projects] {:body (json/write-str {:projectId project-id
                                                    :name name})
                             :content-type :json}))

(defn operation [name]
  (action :get [name]))

#_(
   (in-ns 'jaq.gcp.resource)
   (projects)
   (defn operation [name]
     {:done true})
   )
