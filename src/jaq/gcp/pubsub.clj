(ns jaq.gcp.pubsub
  (:require
   [clojure.data.json :as json]
   [clojure.edn :as edn]
   [jaq.services.util :as util]))

(def service-name "pubsub.googleapis.com")
(def endpoint (str "https://" service-name))
(def version "v1")
(def default-endpoint [endpoint version])
(def action (partial util/action default-endpoint))

(defn create-topic [project-id topic & [labels]]
  (action :put [:projects project-id :topics topic]
          {:content-type :json
           :body (json/write-str {:labels labels})}))

(defn topics [project-id & [{:keys [pageToken pageSize] :as params}]]
  (lazy-seq
   (let [{:keys [nextPageToken error]
          topic-list :topics} (action
                               :get
                               [:projects project-id :topics]
                               {:query-params params})]
     (or
      error
      (concat topic-list
              (when nextPageToken
                (topics project-id (assoc params :pageToken nextPageToken))))))))

(defn publish [project-id topic messages]
  (action :post [:projects project-id :topics (str (name topic) ":publish")]
          {:content-type :json
           :body (json/write-str {:messages (->> messages
                                                 (map (fn [e] {:data (util/encode (pr-str e))})))})}))

(defn subscribe [project-id topic subscriber-id & [{:keys [pushConfig ackDeadlineSeconds retainAckedMessages
                                                           messageRetentionDuration labels]
                                                    :as params}]]
  (action :put [:projects project-id :subscriptions subscriber-id]
          {:content-type :json
           :body (json/write-str
                  (merge
                   {:topic (str "projects/" project-id "/topics/" (name topic))}
                   params))}))

(defn subscriptions [project-id & [{:keys [pageSize pageToken] :as params}]]
  (lazy-seq
   (let [{:keys [subscriptions nextPageToken error]
          subscription-list :subscriptions} (action
                                             :get
                                             [:projects project-id :subscriptions])]
     (or
       error
       (concat subscription-list
               (when nextPageToken
                 (subscriptions project-id (assoc params :pageToken nextPageToken))))))))

(defn ack [project-id subscriber-id ackIds]
  (action :post [:projects project-id :subscriptions (str subscriber-id ":acknowledge")]
          {:content-type :json
           :body (json/write-str {:ackIds ackIds})}))

(defn pull [project-id subscriber-id & [{:keys [maxMessages returnImmediately]
                                         :or {returnImmediately true
                                              maxMessages 100}}]]
  (action :post [:projects project-id :subscriptions (str subscriber-id ":pull")]
          {:content-type :json
           :body (json/write-str {:returnImmediately returnImmediately
                                  :maxMessages maxMessages})}))

(defn pack [project-id subscriber-id]
  (->> (pull project-id subscriber-id)
       :receivedMessages
       (map (fn [e]
              (let [{:keys [ackId message]} e
                    {:keys [data messageId publishTime]} message]
                (-> data
                    (util/decode)
                    (edn/read-string)
                    (merge {:pubsub/ackId ackId
                            :pubsub/messageId messageId
                            :pubsub/publishTime publishTime})))))
       ((fn [e]
          (ack project-id subscriber-id (->> e (map :pubsub/ackId)))
          e))))

#_(

   *ns*
   (in-ns 'jaq.gcp.pubsub)
   (create-topic "alpeware-jaq-runtime" :service)
   (topics "alpeware-jaq-runtime")
   (publish "alpeware-jaq-runtime" :service [{:foo :bar}])

   (subscribe "alpeware-jaq-runtime" :service "sub")
   (subscriptions "alpeware-jaq-runtime")

   (pull "alpeware-jaq-runtime" "sub")
   (pack "alpeware-jaq-runtime" "sub")

   (let [{:keys [foo bar] :or {foo :foo} :as p} {:bar :bar}]
     foo)
   )
