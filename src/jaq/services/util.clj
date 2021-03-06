(ns jaq.services.util
  (:require
   [clojure.data.json :as json]
   [clojure.string :as string]
   [clojure.repl :refer [demunge]]
   [clj-http.lite.client :as http]
   [jaq.services.auth :as auth]
   [clojure.tools.logging :as log])
  (:import
   [java.net URLEncoder]
   [java.util Base64]))


;;; TODO(alpeware): need different credential stores
(def ^:dynamic *throw-exceptions* false)
(def ^:dynamic *credentials-file* ".credentials")
(def ^:dynamic credentials
  (atom
   (auth/load-credentials *credentials-file*)))

(defn get-token []
  (->> @credentials
       (auth/get-valid-credentials)
       (reset! credentials)
       :access-token))
(def ^:dynamic *token-fn* get-token)
;;;

(defn substitute [path base]
  (let [full-path (-> base
                      (concat path))]
    (map #(if (keyword? %) (name %) %) full-path)))

(defn make-url [v]
  (string/join (interpose "/" v)))

(defn get-response
  "Check an HTTP response, JSON decoding the body if valid"
  [res & [raw]]
  (let [#_ (println res)
        body (try
               (json/read-json (:body res))
               (catch Exception _
                 (:body res)))
        status (:status res)
        headers (:headers res)]
    (cond
      (true? raw) res
      (= status 200) body
      :else {:error body :status status})))

(defn action
  "Issues a HTTP request to `path` using the `verb` with `opts`.

  `path` is a vector that can include references to resources
  Ex. [:projects some-id]
  "
  [base verb path & [opts raw]]
  (let [url (make-url (substitute path base))
        headers (merge {"Authorization" (str "Bearer " (*token-fn*))} (:headers opts))
        req (merge {:method verb
                    :url url
                    :throw-exceptions *throw-exceptions*
                    :headers headers}
                   (dissoc opts :headers))]
    (get-response (http/request req) raw)))

;;;;;;;;;;;;;
#_(
   (in-ns 'jaq.services.util)
   (*token-fn*)
   )

(defn repl-server []
  (clojure.core.server/start-server
   {:address "0.0.0.0"
    :port 10010
    :name "repl"
    :server-daemon false
    :accept 'clojure.core.server/repl}))


;;;
(defn sleep [& [ms]]
  (Thread/sleep (or ms 1000)))

(defn fn->str [f]
  (when f
    (-> f
        (str)
        (demunge)
        (string/split #"@")
        (first))))

(defn call-fn [s & args]
  (when s
    (-> s
        #_((fn [e] (str "(" e " " (or args []) ")")))
        (read-string)
        (eval)
        (apply args))))

#_(
   (require 'jaq.services.util)
   (in-ns 'jaq.services.util)
   @credentials

   (get-token)
   (-> env
       (merge {:DEFAULT_BUCKET (->> (jaq.services.storage/buckets "alpeware-jaq-runtime")
                                    :items
                                    (map :name)
                                    first)})
       ((fn [e] (def env e))))

   (->> (jaq.services.storage/buckets "alpeware-jaq-runtime")
        :items
        (map :name))

   (->> (com.google.auth.oauth2.ComputeEngineCredentials/create)
        (.refreshAccessToken)
        ((fn [e] {:access-token (.getTokenValue e) :expires-in (-> e (.getExpirationTime) (.getTime))}))
        (reset! jaq.services.util/credentials))

   (defn foo [f]
     f
     #_(clojure.edn/read-string f))

   (-> foo
       (fn->str)
       (call-fn (pr-str {:foo 'jaq.bar})))

   (-> "jaq.services.util/foo"
       (read-string)
       (eval)
       (apply ['jaq.bar]))

   (url-encode "foo/bar")
   )

(defn url-encode [s]
  (URLEncoder/encode s "UTF-8"))

(defn encode [s]
  (.encodeToString (java.util.Base64/getEncoder) (.getBytes s)))

(defn decode [s]
  (String. (.decode (Base64/getDecoder) s)))
