(ns jaq.gce.metadata
  (:refer-clojure :exclude [list])
  (:require
   [jaq.services.util :as util]))

(def service-name )
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
   (require 'jaq.gce.metadata)
   (in-ns 'jaq.gce.metadata)
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
