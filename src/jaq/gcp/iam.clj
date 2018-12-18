(ns jaq.gcp.iam
  (:refer-clojure :exclude [list get])
  (:require
   [clojure.data.json :as json]
   [clojure.string :as string]
   [jaq.services.util :as util]))

(def service-name "iam.googleapis.com")
(def endpoint (str "https://" service-name))
(def version "v1")
(def default-endpoint [endpoint version])
(def action (partial util/action default-endpoint))

(defn roles []
  (action :get [:roles]))

(defn service-accounts [project-id & [{:keys [pageToken maxResults] :as params}]]
  (lazy-seq
   (let [{:keys [accounts nextPageToken error]} (action :get
                                                        [:projects project-id
                                                         :serviceAccounts]
                                                        {:query-params params})]
     (or
      error
      (concat accounts (when nextPageToken
                         (service-accounts project-id
                                           (assoc params :pageToken nextPageToken))))))))

#_(

   (in-ns 'jaq.services.iam)
   (action :get [:projects "alpeware-jaq-runtime" :serviceAccounts])

   (service-accounts "alpeware-jaq-runtime")
   )
