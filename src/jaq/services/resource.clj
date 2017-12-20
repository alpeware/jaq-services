(ns jaq.services.resource
  (:refer-clojure :exclude [list get])
  (:require
   [clojure.data.json :as json]
   [clojure.tools.logging :as log]
   [clj-http.lite.client :as http]
   [clojure.string :as string]
   [jaq.services.util :as util]))

(def endpoint "https://cloudresourcemanager.googleapis.com")
(def version "v1")
(def default-endpoint [endpoint version])
(def action (partial util/action default-endpoint))

(defn projects []
  (action :get [:projects]))

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
