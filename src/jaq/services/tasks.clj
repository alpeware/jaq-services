(ns jaq.services.tasks
  (:require
   [clojure.data.json :as json]
   [clojure.edn :as edn]
   [clojure.tools.logging :as log]
   [clojure.java.io :as io]
   [clojure.walk :as walk]
   [clojure.string :as string]
   [clj-http.lite.client :as http]
   [ring.util.mime-type :refer [ext-mime-type]]
   [jaq.services.deferred :refer [defer defer-fn]]
   [jaq.services.util :as util]))

(def endpoint "https://cloudtasks.googleapis.com")
(def version "v2beta2")
(def default-endpoint [endpoint version])
(def action (partial util/action default-endpoint))

(defn locations [project-id]
  (action :get [:projects project-id :locations]))

(defn location [project-id location-id]
  (action :get [:projects project-id :locations location-id]))

(defn queues [project-id location-id & [{:keys [pageToken pageSize] :as params}]]
  (lazy-seq
   (let [result (action :get [:projects project-id :locations location-id :queues]
                        {:query-params params})
         next-token (:nextPageToken result)]
     (concat (:queues result) (when next-token
                                (queues project-id location-id (assoc params :pageToken next-token)))))))

(defn queue [project-id location-id queue-id]
  (action :get [:projects project-id :locations location-id :queues queue-id]))

(defn patch-queue [project-id location-id queue-id m]
  (action :post [:projects project-id :locations location-id :queues queue-id]
          {:content-type :json
           :headers {"X-HTTP-Method-Override" "PATCH"}
           :body (json/write-str m)}))

(defn purge-queue [project-id location-id queue-id]
  (action :post [:projects project-id :locations location-id :queues (str queue-id ":purge")]))

(defn resume-queue [project-id location-id queue-id]
  (action :post [:projects project-id :locations location-id :queues (str queue-id ":resume")]))

(defn create-pull-queue [project-id location-id queue-name]
  (action :post [:projects project-id :locations location-id :queues]
          {:content-type :json
           :body (json/write-str {:name (->> [:projects project-id :locations location-id :queues queue-name]
                                             (map name)
                                             (clojure.string/join "/"))
                                  :pullTarget {}})}))

(defn tasks [project-id location-id queue-id & [{:keys [pageToken pageSize] :as params}]]
  (lazy-seq
   (let [result (action :get [:projects project-id :locations location-id :queues queue-id :tasks]
                        {:query-params params})
         next-token (:nextPageToken result)]
     (concat (:tasks result) (when next-token
                                (tasks project-id location-id queue-id (assoc params :pageToken next-token)))))))



#_(
   *ns*
   (in-ns 'jaq.runtime)
   (require 'jaq.services.tasks :reload)
   (in-ns 'jaq.services.tasks)
   (jaq.services.tasks/locations "alpeware-jaq-runtime")
   (location "alpeware-jaq-runtime" "us-central1")
   (queues "alpeware-jaq-runtime" "us-central1")
   (queue "alpeware-jaq-runtime" "us-central1" "default")
   (purge-queue "alpeware-jaq-runtime" "us-central1" "default")
   (resume-queue "alpeware-jaq-runtime" "us-central1" "default")

   (create-queue "alpeware-jaq-runtime" "us-central1" "pull")

   (->> (tasks "alpeware-jaq-runtime" "us-central1" "pull")
        count)

   (->> )
   (->> (queue "alpeware-jaq-runtime" "us-central1" "default")
        ((fn [e] (merge e {:rateLimits {:maxTasksDispatchedPerSecond 1
                                        :maxBurstSize 3
                                        :maxConcurrentTasks 1}})))
        (patch-queue "alpeware-jaq-runtime" "us-central1" "default"))

   (->> [:foo :bar]
        (interleave (repeatedly (constantly "/")))
        (map name)
        (clojure.string/join))


   )
