(ns jaq.gcp.script
  (:refer-clojure :exclude [list])
  (:require
   [clojure.data.json :as json]
   [jaq.services.util :as util]))

(def service-name "script.googleapis.com")
(def api-endpoint (str "https://" service-name))
(def api-version "v1")
(def default-endpoint [api-endpoint api-version])
(def action (partial util/action default-endpoint))

(defn processes [& [{:keys [pageToken pageSize] :as params}]]
  (lazy-seq
   (let [{:keys [queues nextPageToken error]} (action
                                               :get
                                               [:processes]
                                               {:query-params params})]
     (or
      error
      (concat queues (when nextPageToken
                       (processes (assoc params :pageToken nextPageToken))))))))

(defn project [scriptId]
  (action :get [:projects scriptId]))

(defn create-project [{:keys [title parentId] :as params}]
  (action :post [:projects] {:content-type :json
                             :body (json/write-str params)}))

(defn run-script [scriptId function args]
  (action :post [:scripts (str scriptId ":run")]
          {:content-type "application-json"
           :body (json/write-str {:function function
                                  :parameters args
                                  :devMode true})}))

#_(
   *ns*
   (in-ns 'jaq.gcp.script)

   (create-project {:title "foo bar"})

   (processes)

   )
