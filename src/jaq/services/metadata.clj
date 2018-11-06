(ns jaq.services.metadata
  (:refer-clojure :exclude [list])
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

(def endpoint "http://metadata.google.internal")
(def version "v1")
(def default-endpoint [endpoint :computeMetadata version])
(def action (partial util/action default-endpoint))

(defn instance []
  (action :get [:instance] {:headers {"Metadata-Flavor" "Google"}
                            :query-params {:recursive true}}))

(defn attributes []
  (action :get [:instance :attributes] {:headers {"Metadata-Flavor" "Google"}
                                        :query-params {:recursive true}}))

(defn project []
  (action :get [:project] {:headers {"Metadata-Flavor" "Google"}
                           :query-params {:recursive true}}))

(defn token [email]
  (action :get [:instance :service-accounts email :token] {:headers {"Metadata-Flavor" "Google"}
                                                           :query-params {:recursive true}}))

#_(
   *ns*
   (in-ns 'clojure.core)
   (require jaq.services.metadata)
   (in-ns 'jaq.services.metadata)
   ;;service-accounts/${this.serviceAccountEmail}/token

   (->> (instance)
       :serviceAccounts
       (vals)
       (map :email)
       (first)
       (token)
       )

   (->> (instance)
        :serviceAccounts
        (vals)
        #_(map :email)
        #_(first))

   (attributes)
   (-> (project) :projectId)
   )
