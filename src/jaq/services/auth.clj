(ns jaq.services.auth
  (:require
   [clojure.data.json :as json]
   [clj-http.lite.client :as h]
   [clojure.string :as string])
  (:import
   [java.net URLEncoder]
   [java.io FileNotFoundException]))

(def auth-message "Please open below link in a browser:\n")
(def enter-code-message "\nPlease enter the verification code and press [ENTER]:\n")

(def google-client-id "32555940559.apps.googleusercontent.com")
(def google-client-secret "ZmssLNjJy2998hD4CTg2ejr2")

(def auth-uri "https://accounts.google.com/o/oauth2/auth")
(def token-uri "https://accounts.google.com/o/oauth2/token")
(def revoke-uri "https://accounts.google.com/o/oauth2/revoke")
(def local-redirect-uri "urn:ietf:wg:oauth:2.0:oob")

(def cloud-scopes (atom ["https://www.googleapis.com/auth/appengine.admin" "https://www.googleapis.com/auth/cloud-platform"]))

;; helpers
(defn encode
  [key val]
  (str (URLEncoder/encode (str key) "UTF-8") "=" (URLEncoder/encode (str val) "UTF-8")))

(defn valid? [{:keys [expires-in]}]
  (try
    (< (System/currentTimeMillis) expires-in)
    (catch NullPointerException _ false)))

(defn to-underscore [s]
  (clojure.string/replace s #"-" "_"))

(defn to-dash [s]
  (clojure.string/replace s #"_" "-"))

(defn to-params [m]
  (->> (zipmap (map (comp to-underscore name) (keys m)) (vals m))
       (map (fn [[k v]] (encode k v)))
       (string/join "&")))

(defn to-url [url params]
  (string/join "?" [url params]))

;; main api
(defn load-credentials [path]
  (try
    (clojure.edn/read-string (slurp path))
    (catch FileNotFoundException _ nil)))

(defn save-credentials [path credentials]
  (spit path credentials)
  credentials)

(defn init-credentials [client-id client-secret redirect-uri]
  {:client-id client-id
   :client-secret client-secret
   :access-token nil
   :refresh-token nil
   :redirect-uri redirect-uri
   :auth-uri auth-uri
   :token-uri token-uri
   :revoke-uri revoke-uri
   :expires-in (System/currentTimeMillis)})

;; TODO(alpeware): add state code
(defn generate-auth-url [credentials scopes]
  (let [opts (merge {:access-type "offline"
                     :prompt "consent"
                     :include-granted-scopes "true"
                     :response-type "code"
                     :scope (string/join " " scopes)}
                    (select-keys credentials [:client-id :redirect-uri]))]
    (to-url (:auth-uri credentials) (to-params opts))))

(defn exchange-token [credentials code]
  (let [opts (merge {:grant-type "authorization_code"
                     :code code}
                    (select-keys credentials [:client-id :redirect-uri :client-secret]))
        params (to-params opts)
        http-resp (h/post (:token-uri credentials)
                          {:body params
                           :content-type "application/x-www-form-urlencoded"})
        resp (json/read-json (http-resp :body))]
    (merge credentials
           {:access-token (:access_token resp)
            :refresh-token (:refresh_token resp)
            :expires-in (+ (System/currentTimeMillis) (* (resp :expires_in) 1000))})))

(defn refresh-token
  "Generate a new authentication token using the refresh token."
  [credentials]
  (let [opts (merge {:grant-type "refresh_token"}
                    (select-keys credentials [:client-id :client-secret :refresh-token]))
        params (to-params opts)
        http-resp (h/post (:token-uri credentials)
                          {:body params
                           ;;:throw-exceptions false
                           :content-type "application/x-www-form-urlencoded"})
        resp (json/read-json (http-resp :body))]
    (merge credentials
           {:access-token (:access_token resp)
            :expires-in (+ (System/currentTimeMillis) (* (resp :expires_in) 1000))})))

(defn get-valid-credentials [credentials]
  (cond
    (valid? credentials) credentials
    (:refresh-token credentials) (refresh-token credentials)
    :else (or (try
                (->> (clojure.lang.Reflector/invokeStaticMethod
                      (Class/forName "com.google.appengine.api.appidentity.AppIdentityServiceFactory")
                      "getAppIdentityService"
                      (into-array []))
                     ((fn [e] (.getAccessToken e @cloud-scopes)))
                     ((fn [e]
                        {:access-token (.getAccessToken e)
                         :expires-in (-> e (.getExpirationTime) (.getTime))})))
                (catch Exception _ nil))
              (try
                (->> (clojure.lang.Reflector/invokeStaticMethod
                      (Class/forName "com.google.auth.oauth2.ComputeEngineCredentials")
                      "create"
                      (into-array []))
                     #_(com.google.auth.oauth2.ComputeEngineCredentials/create)
                     (.refreshAccessToken)
                     ((fn [e] {:access-token (.getTokenValue e)
                               :expires-in (-> e (.getExpirationTime) (.getTime))})))
                (catch Exception _ nil)))))

#_(

   (in-ns 'jaq.services.auth)
   (com.google.appengine.api.appidentity.AppIdentityServiceFactory/getAppIdentityService)
   (->>
    (clojure.lang.Reflector/invokeStaticMethod
     (Class/forName "com.google.appengine.api.appidentity.AppIdentityServiceFactory")
     "getAppIdentityService"
     (into-array [])))
   )

(defn default-credentials []
  (init-credentials google-client-id google-client-secret local-redirect-uri))

#_(
   (default-credentials)
   )

(defn local-credentials [path]
  (let [credentials (or (load-credentials path)
                        (init-credentials google-client-id google-client-secret local-redirect-uri))]
    (if (:refresh-token credentials)
      (save-credentials path (get-valid-credentials credentials))
      (let [url (generate-auth-url credentials @cloud-scopes)
            _ (println auth-message url)
            _ (println enter-code-message)
            code (read-line)]
        (save-credentials path (exchange-token credentials code))))))

#_(
   (in-ns 'jaq.services.auth)

   @jaq.services.util/credentials
   (reset! jaq.services.util/credentials nil)
   (jaq.services.util/get-token)

   )
