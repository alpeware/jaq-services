(ns jaq.services.util
  (:require
   [clojure.data.json :as json]
   [clojure.string :as string]
   [clj-http.lite.client :as http]
   [jaq.services.auth :as auth])
  (:import
   [com.google.appengine.tools KickStart]
   [com.google.appengine.tools.development DevAppServerMain]
   [com.google.appengine.api.utils SystemProperty]
   [com.google.appengine.tools.remoteapi
    RemoteApiInstaller
    RemoteApiOptions]))


;;; TODO(alpeware): need different credential stores
(def credentials
  (atom
   (auth/load-credentials ".credentials")))

(defn get-token []
  (->> @credentials
      (auth/get-valid-credentials)
      (reset! credentials)
      :access-token))
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
        headers (merge {"Authorization" (str "Bearer " (get-token))} (:headers opts))]
    (get-response
     (http/request
      (merge {:method verb
              :url url
              ;;:throw-exceptions false
              :headers headers}
             (dissoc opts :headers)))
     raw)))

;;;;;;;;;;;;;

(defn- property-or [property alternative]
  (or (.get property) alternative))

(defn environment []
  (property-or SystemProperty/environment "Development"))

(def prod?
  (not= (environment) "Development"))

(def dev? (not prod?))

(defn application-id []
  (property-or SystemProperty/applicationId "localhost"))

(defn sdk-version []
  (property-or SystemProperty/version "Google App Engine/1.x.x"))

(defn remote! [host port]
  (-> (RemoteApiInstaller.)
      (.install (-> (RemoteApiOptions.)
                    (.server host port)
                    (.useDevelopmentServerCredential)))))

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
