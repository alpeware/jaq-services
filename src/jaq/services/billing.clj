(ns jaq.services.billing
  (:require
   [clojure.tools.logging :as log]
   [clojure.string :as string]
   [jaq.services.management :as management]
   [jaq.services.util :as util]))

(def service-name "cloudbilling.googleapis.com")
(def endpoint (str "https://" service-name))
(def version "v1")
(def default-endpoint [endpoint version])
(def action (partial util/action default-endpoint))

(defn enable [project-id]
  (management/enable service-name project-id))

(defn accounts [& [{:keys [pageToken pageSize] :as params}]]
  (lazy-seq
   (let [{:keys [billingAccounts nextPageToken error]} (action :get [:billingAccounts]
                                                      {:query-params params})]
     (if error
       error
       (concat billingAccounts
               (when nextPageToken
                 (accounts (assoc params :pageToken nextPageToken))))))))

(defn billing-info [project-id]
  (action :get [:projects project-id :billingInfo]))

(defn projects [account-id]
  (action :get [:billingAccounts account-id :projects]))

#_(
   (in-ns 'jaq.services.billing)
   (enable "alpeware-jaq-runtime")
   (management/operation (:name *1))
   (accounts)

   (-> (billing-info "alpeware-jaq-runtime")
        :billingAccountName
        (string/split #"/")
        (last)
        (projects))

   )
