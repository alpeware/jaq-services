(ns jaq.services.deferred
  (:gen-class
   :implements [com.google.appengine.api.taskqueue.DeferredTask]
   :name jaq.services.deferred.Defer
   :init init
   :constructors {[java.util.Map] []}
   :state state)
  (:require
   [clojure.core :refer [eval]]
   [clojure.data.json :as json]
   [clojure.tools.logging :as log])
  (:import
   [java.util Base64]
   [java.util.concurrent TimeUnit]
   [java.io InputStreamReader ByteArrayInputStream ByteArrayOutputStream ObjectInputStream]
   [com.google.appengine.api.taskqueue
    DeferredTask
    Queue
    QueueFactory
    TaskOptions
    TaskOptions$Method
    TaskOptions$Builder]))


(defn push-queue []
  (QueueFactory/getDefaultQueue))

(defmacro with-deferred [body & {:keys [delay-ms]
                                 :or {delay-ms 0}}]
  `(let [q# (QueueFactory/getDefaultQueue)
         d# (+ (System/currentTimeMillis) ~delay-ms)]
     (if (> ~delay-ms 0)
       (-> q#
           (.add (->
                  (TaskOptions$Builder/withPayload (jaq.services.deferred.Defer. ~body))
                  (.etaMillis d#))))
       (-> q#
           (.add (TaskOptions$Builder/withPayload (jaq.services.deferred.Defer. ~body)))))))

(defn defer [m & [{:keys [delay-ms]
                   :or {delay-ms 0}}]]
  (if (> delay-ms 0)
    (-> (push-queue)
        (.add (->
               (TaskOptions$Builder/withPayload (jaq.services.deferred.Defer. m))
               (.etaMillis (+ (System/currentTimeMillis) delay-ms)))))
    (-> (push-queue)
        (.add (TaskOptions$Builder/withPayload (jaq.services.deferred.Defer. m))))))

(defn pull-queue []
  (QueueFactory/getQueue "pull"))

(defn add [payload tag]
  (let [options (-> (TaskOptions$Builder/withMethod TaskOptions$Method/PULL)
                    (.payload (pr-str payload))
                    ;;(.payload (top9.services.deferred.Defer. payload))
                    (.tag tag))]
    ;;(log/info "Adding task for" tag "with payload" payload)
    (.add (pull-queue) options)))

(defn lease [{:keys [tag lease unit countLimit]
              :or {lease 120 ;; 2min
                   unit TimeUnit/SECONDS
                   countLimit 100}}]
  (if tag
    (.leaseTasksByTag (pull-queue) lease unit countLimit tag)
    (.leaseTasks (pull-queue) lease unit countLimit)))

(defn deserialize [payload]
  (with-open [ba (ByteArrayInputStream. payload)
              oi (ObjectInputStream. ba)]
    (.readObject oi)))

(defn process [tasks]
  (->> tasks
       (map
        #(-> % .getPayload (String.) read-string))))

(defn delete [tasks]
  (-> (pull-queue)
      (.deleteTask tasks)))

;;;;

(defn -init [body]
  [[] body])

(defmulti defer-fn :fn)

(defmethod defer-fn :eval [{:keys [form] :as params}]
  (let [exp (eval form)]
    (log/info exp)))

(defmethod defer-fn :default [{:keys [fn]}]
  (log/error "Unknown deferred fn:" fn))

(defn -run [this]
  (let [state (.state this)]
    (defer-fn state)))
