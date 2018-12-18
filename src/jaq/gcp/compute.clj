(ns jaq.gcp.compute
  (:refer-clojure :exclude [list])
  (:require
   [clojure.data.json :as json]
   [clojure.walk :as walk]
   [clojure.string :as string]
   [jaq.services.util :as util]))

(def service-name "compute.googleapis.com")
(def endpoint "https://www.googleapis.com")
(def version "v1")
(def default-endpoint [endpoint :compute version])
(def action (partial util/action default-endpoint))

(defn set-service-account [project-id zone instance-name email scopes]
  (action :post [:projects project-id :zones zone :instances instance-name :setServiceAccount]
          {:content-type :json
           :body (json/write-str {:email email
                                  :scopes scopes})}))

(defn instances [project-id zone & [{:keys [pageToken maxResults] :as params}]]
  (lazy-seq
   (let [{:keys [items nextPageToken error]} (action
                                              :get
                                              [:projects project-id
                                               :zones zone :instances]
                                              {:query-params params})]
     (or
      error
      (concat items (when nextPageToken
                      (instances project-id zone
                                 (assoc params :pageToken nextPageToken))))))))

(defn templates [project-id & [{:keys [pageToken maxResults] :as params}]]
  (lazy-seq
   (let [{:keys [items nextPageToken error]} (action :get
                                                     [:projects project-id
                                                      :global :instanceTemplates]
                                                     {:query-params params})]
     (or
      error
      (concat items (when nextPageToken
                      (templates project-id
                                 (assoc params :pageToken nextPageToken))))))))

(defn zones [project-id & [{:keys [pageToken maxResults] :as params}]]
  (lazy-seq
   (let [{:keys [items nextPageToken error]} (action :get
                                                     [:projects project-id :zones]
                                                     {:query-params params})]
     (or
       error
       (concat items (when nextPageToken
                       (zones project-id
                              (assoc params :pageToken nextPageToken))))))))

#_(
   (in-ns 'jaq.services.compute)
   (zones "alpeware-jaq-runtime")
   (def a *1)
   (->> a
        #_(take 1)
        (map :region)
        (distinct)
        #_count
        #_(first)
        #_keys)
   )

(defn reset [project-id zone instance-name]
  (action :post [:projects project-id :zones zone :instances instance-name :reset]
          {:content-type :json
           :body ""}))

(defn stop [project-id zone instance-name]
  (action :post [:projects project-id :zones zone :instances instance-name :stop]
          {:content-type :json
           :body ""}))

(defn delete [project-id zone instance-name]
  (action :delete [:projects project-id :zones zone :instances instance-name]))

(defn operation [project-id zone name]
  (action :get [:projects project-id :zones zone :operations name]))

#_(
   (defn operation [project-id zone name]
     {:done true})

   )

(def default-startup-script "#!/bin/bash\n#\n# JAQ VM Startup Script\n#\n\n# install packages\napt-get update && apt-get install -y tmux htop openjdk-8-jdk-headless git rlwrap\n\n# install clojure\nif [ ! $(which clj) ]; then\n    echo \"Installing Clojure\"\n    curl https://download.clojure.org/install/linux-install-1.9.0.397.sh | bash -\nfi\n\n# add swap\nif [ ! -f /swapfile ]; then\n    echo \"Enabling swap\"\n    fallocate -l 4G /swapfile\n    chmod 600 /swapfile\n    mkswap /swapfile\n    swapon /swapfile\nfi")

(defn create-instance [{:keys [project-id zone machine-type instance-name metadata
                               disks networks tags description scheduling
                               service-account-email scopes]
                        :as params
                        :or {machine-type :f1-micro
                             disks [{:kind "compute#attachedDisk"
                                     :type :PERSISTENT
                                     :boot true
                                     :autoDelete true
                                     :deviceName instance-name
                                     :initializeParams {:sourceImage "projects/debian-cloud/global/images/debian-9-stretch-v20181210"
                                                        :diskType (->> [:projects project-id :zones zone :diskTypes :pd-standard]
                                                                       (map name)
                                                                       (string/join "/"))
                                                        :diskSizeGb 10}}]
                             networks [{:kind "compute#networkInterface"
                                        :subnetwork (->> [:projects project-id :regions
                                                          (-> zone (name) (string/split #"-") (butlast) (->> (string/join "-")))
                                                          :subnetworks :default]
                                                         (map name)
                                                         (string/join "/"))
                                        :accessConfigs [{:kind "compute#accessConfig"
                                                         :name "External NAT"
                                                         :type :ONE_TO_ONE_NAT
                                                         :networkTier :PREMIUM}]
                                        :aliasIpRanges []}]
                             scheduling {:preemtible false
                                         :onHostMaintenance :MIGRATE
                                         :autmoaticRestart true
                                         :nodeAffinities []}
                             scopes ["https://www.googleapis.com/auth/cloud-platform"]
                             tags [:http-server :https-server]
                             description "JAQ runtime VM"
                             metadata {:startup-script default-startup-script
                                       :JAQ_REPL_TOKEN "foobarbaz"
                                       :DEFAULT_BUCKET (str project-id ".appspot.com")}}}]
  (action :post [:projects project-id :zones zone :instances]
          {:content-type :json
           :body (json/write-str
                  {:kind "compute#instance"
                   :name instance-name
                   :zone (->> [:projects project-id :zones zone]
                              (map name)
                              (string/join "/"))
                   :machineType (->> [:projects project-id :zones zone :machineTypes machine-type]
                                     (map name)
                                     (string/join "/"))
                   :metadata {:kind "compute#metadata"
                              :items (->> metadata
                                          (map (fn [[k v]]
                                                 {:key k :value v})))}
                   :tags {:items tags}
                   :description description
                   :disks disks
                   :canIpForward false
                   :networkInterfaces networks
                   :scheduling scheduling
                   :serviceAccounts [{:email service-account-email
                                      :scopes scopes}]})}))

#_(

   (create-instance {:project-id "alpeware-wealth" :instance-name "alpeware-wealth-vm" :zone :us-central1-c
                     :service-account-email "328831522370-compute@developer.gserviceaccount.com"
                     :scopes ["https://www.googleapis.com/auth/cloud-platform" "https://www.googleapis.com/auth/spreadsheets"]})
   (def a *1)

   )

#_(
   *ns*
   (in-ns 'clojure.core)
   (require 'jaq.services.compute)
   (in-ns 'jaq.services.compute)
   ;; "projects/328831522370/zones/us-central1-c"
   (set-service-account "alpeware-wealth" "us-central1-c" "jaq-runtime-vm"
                        "328831522370-compute@developer.gserviceaccount.com"
                        ["https://www.googleapis.com/auth/cloud-platform"
                         "https://www.googleapis.com/auth/spreadsheets"])

   (instances "alpeware-jaq-runtime" "us-central1-c")
   (-> (instances "alpeware-jaq-runtime" "us-central1-c")
       (first)
       :networkInterfaces
       first
       :accessConfigs
       (first)
       :natIP)

   (-> (instances "alpeware-wealth" "us-central1-c")
       (first)
       :networkInterfaces
       first
       :accessConfigs
       (first)
       :natIP
       (string/split #"\.")
       (reverse)
       (concat ["bc" "googleusercontent" "com"])
       (->> (string/join "."))
       )

   ;; get FQDN from IP
   (-> (instances "alpeware-wealth" "us-central1-c")
       (first)
       :networkInterfaces
       first
       :accessConfigs
       (first)
       :natIP
       (string/split #"\.")
       (reverse)
       (concat ["in-addr" "arpa"])
       (->> (string/join "."))
       (->> (str "https://dns.google.com/resolve?type=PTR&name="))
       (slurp)
       (json/read-str)
       (walk/keywordize-keys)
       :Answer
       (first)
       :data
       (string/split #"\.")
       (->> (string/join ".")))

   "https://dns.google.com/resolve?name="
   (templates "alpeware-wealth")

   (reset "alpeware-wealth" "us-central1-c" "jaq-runtime-vm")
   (delete "alpeware-wealth" "us-central1-c" "jaq-runtime-vm")
   (stop "alpeware-wealth" "us-central1-c" "jaq-runtime-vm")

   (def a *1)
   (-> (operation "alpeware-wealth" "us-central1-c" (:name a))
       (select-keys [:progress :status]))
   )
