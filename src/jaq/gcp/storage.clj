(ns jaq.gcp.storage
  (:refer-clojure :exclude [list])
  (:require
   [clojure.data.json :as json]
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.walk :as walk]
   [clojure.string :as string]
   [clj-http.lite.client :as http]
   [ring.util.mime-type :refer [ext-mime-type]]
   [jaq.services.deferred :refer [defer defer-fn]]
   [jaq.services.env :as env]
   [jaq.services.util :as util])
  (:import
   [java.io File]
   [com.google.appengine.api.appidentity
    AppIdentityServiceFactory
    AppIdentityService]))

(def service-name "storage-api.googleapis.com")
(def endpoint "https://www.googleapis.com")
(def version "v1")
(def default-endpoint [endpoint :storage version])
(def action (partial util/action default-endpoint))

(def extra-mime-types {"mf" "text/plain"})

#_(
   *ns*
   (in-ns 'jaq.gcp.storage)
   (defn default-bucket []
     "alpeware-jaq-runtime.appspot.com"
     #_(:DEFAULT_BUCKET util/env))
   (default-bucket)

   (slurp "http://metadata.google.internal/computeMetadata/v1/project/project-id")
   (slurp "http://metadata.google.internal/computeMetadata/v1")
   (get-file (default-bucket) "jaq-config.edn")
   (->> (buckets "alpeware-jaq-runtime" {:prefix "us"
                                         :projection "full"})
        #_(map :name))

   )

;; buckets
(defn buckets [project-id & [{:keys [pageToken maxResults prefix
                                     projection userProject] :as params}]]
  (lazy-seq
   (let [{:keys [items nextPageToken error]} (action
                                              :get [:b]
                                              {:query-params (merge
                                                              {"project" project-id}
                                                              params)})]
     (or
      error
      (concat items
              (when nextPageToken
                (buckets project-id (assoc params :pageToken nextPageToken))))))))

;; TODO: query metadata server for default project
;; TODO: move to services?
(defn default-bucket []
  (or
   (try
     (let [app-id-service (AppIdentityServiceFactory/getAppIdentityService)]
       (.getDefaultGcsBucketName app-id-service))
     (catch Exception _ nil))
   (:DEFAULT_GCS_BUCKET env/env)
   (:DEFAULT_BUCKET env/env)
   (->> (buckets "alpeware-jaq-runtime")
        :items
        (first)
        :name)))

(defn new [{:keys [project-id bucket location storage-class]}]
  (action :post [:b] {:query-params {"project" project-id}
                      :content-type :json
                      :body (json/write-str {"name" bucket
                                             "location" location
                                             "storageClass" storage-class})}))

(defn delete [project-id bucket]
  (action :delete [:b bucket] {:query-params {"project" project-id}}))

;; objects
(defn put-simple [{:keys [bucket file-name content-type content]}]
  (util/action [endpoint :upload :storage version]
               :post [:b bucket :o]
               {:content-type content-type
                :query-params {:uploadType "media"
                               :name file-name}
                :body content}))

(defn create-session-uri [{:keys [bucket path base-dir prefix]}]
  (let [file (io/file path)
        content-length (-> file .length str)
        dir base-dir ;; (.getParent file)
        file-name (string/replace-first path dir prefix)
        content-type (or (ext-mime-type path extra-mime-types) "application/octet-stream")]
    (->
     (jaq.services.util/action [endpoint :upload :storage version]
                               :post [:b bucket :o]
                               {:headers {"X-Upload-Content-Type" content-type
                                          "X-Upload-Content-Length" content-length}
                                :content-type :json
                                :query-params {:uploadType "resumable"
                                               :name file-name}
                                :body ""}
                               true)
     :headers
     walk/keywordize-keys
     :location)))

(defn upload-chunk [{:keys [session-uri path file-size index chunk-size]}]
  (with-open [f (java.io.RandomAccessFile. path "r")]
    (let [buffer (byte-array chunk-size)
          _ (.seek f index)
          bytes-read (.read f buffer)
          file-pointer (-> f .getFilePointer)
          offset (dec file-pointer)
          content-range (str "bytes " index "-" offset "/" file-size)
          resp (->
                (http/put session-uri {:headers {"Content-Range" content-range}
                                       :content-length (str bytes-read)
                                       :body-encoding "application/octet-stream"
                                       :throw-exceptions false
                                       :body (->> buffer
                                                  (take bytes-read)
                                                  byte-array)})
                (walk/keywordize-keys))]
      resp)))

;; TODO: handle vm vs. app engine differences somewhere
(defmethod defer-fn ::upload [{:keys [session-uri path file-size index chunk-size
                                      callback-fn callback-args] :as params}]
  (let [resp (upload-chunk params)
        offset (-> resp :headers :range (or "-0") (string/split #"-") (last) (edn/read-string) (inc))
        status (:status resp)]
    (cond
      (= status 200) (let [response (-> resp :body (json/read-str))
                           args (merge {:response response} params)]
                       (util/call-fn callback-fn args))
      (= status 308) (defer (merge params {:fn ::upload :index offset}))
      :else (throw (IllegalStateException. (str "Re-trying chunk upload" index path))))))

(defn put-large [{:keys [bucket path base-dir prefix callback args] :as params}]
  (let [session-uri (create-session-uri params)
        chunk-size (* 256 1024)
        file-size (-> (io/file path) .length)
        chunks (-> (/ file-size 100) int inc)
        callback-fn (util/fn->str callback)]
    (defer {:fn ::upload :session-uri session-uri :path path :file-size file-size :index 0
            :chunk-size chunk-size :callback-fn callback-fn :callback-args args})
    path))

(defn objects [bucket & [{:keys [prefix pageToken maxResults] :as params}]]
  (lazy-seq
   (let [{:keys [items nextPageToken error]} (action :get
                                                     [:b bucket :o]
                                                     {:query-params params})]
     (or
      error
      (concat items (when nextPageToken
                      (objects bucket (assoc params :pageToken nextPageToken))))))))

(defn get-file [bucket file-name]
  (let [file-path (util/url-encode file-name)]
    (action :get [:b bucket :o file-path] {:query-params {:alt "media"}})))

;; helper
(def file-counter (atom 0))

(defn file-upload-done [& args]
  (swap! file-counter dec))

(defn file-upload-start []
  (swap! file-counter inc))

(defn copy [src-dir bucket prefix]
  (let [dir (->> (string/split src-dir #"/") (interpose "/") (string/join))]
    (->> (file-seq (io/file dir))
         (filter #(.isFile %))
         (map (fn [f]
                (let [path (.getPath f)
                      relative (string/replace path (str dir "/") "")
                      file-name (string/replace path dir prefix)]
                  (file-upload-start)
                  (put-large bucket path dir prefix {:callback file-upload-done}))))
         (doall))))

(defn get-files [bucket prefix dest-dir]
  (->> (objects bucket {:prefix prefix})
       (map :name)
       (map (fn [file-name]
              (let [dest-path (->> (string/split file-name #"/")
                                   (into (string/split dest-dir (re-pattern File/separator)))
                                   (string/join File/separator))]
                (io/make-parents dest-path)
                (->> (get-file bucket file-name)
                     (spit dest-path))
                dest-path)))
       (doall)))

;;;;; used outside app engine

(defn upload-local [{:keys [session-uri path file-size start-index chunk-size] :as params}]
  (loop [index start-index]
    (let [resp (upload-chunk {:session-uri session-uri :path path :file-size file-size
                              :index index :chunk-size chunk-size})
          offset (-> resp :headers :range (or "-0") (string/split #"-") last edn/read-string inc)
          status (:status resp)]
      (cond
        (= status 200) (-> resp :body (json/read-str))
        (= status 308) (recur offset)
        :else (throw (IllegalStateException. (str "Re-trying chunk upload" index path)))))))

(defn put-local [{:keys [bucket path base-dir prefix]}]
  (let [session-uri (create-session-uri {:bucket bucket :path path
                                         :base-dir base-dir :prefix prefix})
        chunk-size (* 256 1024)
        file-size (-> (io/file path) .length)
        chunks (-> (/ file-size 100) int inc)
        content-type (or (ext-mime-type path extra-mime-types) "application/octet-stream")
        file-name (string/replace-first path base-dir prefix)]
    (println "Uploading" path)
    (upload-local {:session-uri session-uri :path path :file-size file-size :start-index 0
                   :chunk-size chunk-size})
    (println "Uploaded" path)
    path))

(defn copy-local [src-dir bucket prefix]
  (let [dir (->> (string/split src-dir #"/") (interpose "/") (string/join))]
    (->> (file-seq (io/file dir))
         (filter #(.isFile %))
         (pmap (fn [f]
                 (put-local {:bucket bucket :path (.getPath f)
                             :base-dir dir :prefix prefix})))
         (doall))))

#_(
   (in-ns 'jaq.gcp.storage)
   )
