(ns jaq.services.deferred
  (:require
   [clojure.data.json :as json]
   [clojure.tools.logging :as log]))

(defmulti defer-fn :fn)

(defmethod defer-fn :eval [{:keys [form] :as params}]
  (let [exp (eval form)]
    (log/info exp)))

(defmethod defer-fn :default [{:keys [fn]}]
  (throw (IllegalArgumentException. (str "Unknown deferred fn: " fn))))

;;;

(defn defer [m & [{:keys [delay-ms]
                   :or {delay-ms 0}}]]
  (Thread/sleep delay-ms)
  #_(defer-fn m)
  (future (defer-fn m)))

(def pq (atom {}))

(defn add [payload tag]
  (swap! pq update tag (fn [e] (-> (or e []) (conj payload)))))

(defn lease [{:keys [tag]}]
  (let [tasks (get @pq tag)]
    (swap! pq assoc tag nil)
    tasks))

(defn process [tasks]
  tasks)

(defn delete [tasks]
  nil)
