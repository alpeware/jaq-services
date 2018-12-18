(ns jaq.gae.datastore
  (:require
   [clojure.data.json :as json]
   [clojure.tools.logging :as log])
  (:import
   [com.google.appengine.api.appidentity AppIdentityServiceFactory AppIdentityService]
   [com.google.appengine.api
    ThreadManager]
   [com.google.appengine.api.datastore
    Cursor
    DatastoreServiceFactory
    DatastoreService
    Entity
    EmbeddedEntity
    EntityNotFoundException
    FetchOptions$Builder
    KeyFactory
    KeyFactory$Builder
    Key
    Query
    Query$SortDirection
    Query$CompositeFilter
    Query$CompositeFilterOperator
    Query$FilterPredicate
    Query$FilterOperator
    TransactionOptions$Builder
    Text]))

;; multimethods

;; map->entity
(declare add-properties)

(defmulti propertify (fn [_ e]
                       (do
                         ;;(log/info "type " type " value " e)
                         (type e))))

(defmethod propertify clojure.lang.Keyword [entity e]
  (str e))

;; TODO: unicode seems to require larger than 1500 char
(defmethod propertify java.lang.String [entity e]
  ;;(log/info "string len" (.length e))
  (if (-> e
          .length
          (> 500))
    (Text. e)
    e))

(defmethod propertify Text [entity e]
  e)

(defmethod propertify java.lang.Boolean [entity e]
  e)

(defmethod propertify EmbeddedEntity [entity e]
  e)

(defmethod propertify java.lang.Long [entity e]
  e)

(defmethod propertify java.lang.Double [entity e]
  e)

(defmethod propertify nil [entity e]
  e)

(defmethod propertify clojure.lang.LongRange [entity e]
  e)

(defmethod propertify clojure.lang.PersistentArrayMap [entity e]
  (let [ee (EmbeddedEntity.)
        m (into {} (for [[k v] e] [(propertify entity k) (propertify entity v)]))]
    (add-properties ee m)))

(defmethod propertify clojure.lang.PersistentHashMap [entity e]
  (let [ee (EmbeddedEntity.)
        m (into {} (for [[k v] e] [(propertify entity k) (propertify entity v)]))]
    (add-properties ee m)))

(defmethod propertify clojure.lang.PersistentVector [entity e]
  (into [] (for [v e]
             (propertify entity v))))

(defmethod propertify clojure.lang.PersistentList [entity e]
  (into [] (for [v e]
             (propertify entity v))))

(defmethod propertify clojure.lang.Cons [entity e]
  (into [] (for [v e]
             (propertify entity v))))

(defmethod propertify clojure.lang.LazySeq [entity e]
  (vec e))

(defmethod propertify :default [_ e]
  (throw (IllegalArgumentException.
          (str "Unable to propertify " e (type e)))))

(defn add-properties [e m]
  ;;  (log/info m)
  (doseq [[k v] m]
    ;;    (log/info "key" k "value" v "type" (type v))
    (.setProperty e (propertify e k) (propertify e v)))
  e)

;; entity->map
(declare into-map)

(defmulti mapify (fn [_ v] (type v)))

(defmethod mapify java.lang.Long [m v]
  v)

(defmethod mapify java.lang.Double [m v]
  v)

(defmethod mapify java.lang.Boolean [m v]
  v)

(defmethod mapify nil [m v]
  nil)

(defmethod mapify java.lang.String [m v]
  (let [kwd? (= (first (str :foo)) (first v))
        ns? (and kwd? (clojure.string/includes? v "/"))]
    (cond
      (and ns? kwd?) (let [[vns vkwd] (clojure.string/split v #"/")]
                       (keyword (clojure.string/join "" (rest vns))
                                vkwd))
      (true? kwd?) (let [vkwd (clojure.string/join "" (rest v))]
                     (keyword vkwd))
      :else v)))

(defmethod mapify java.util.ArrayList [m v]
  (map #(mapify m %) (vec v)))

(defmethod mapify EmbeddedEntity [m v]
  (into-map m v))

(defmethod mapify Text [m v]
  (.getValue v))

(defmethod mapify clojure.lang.PersistentVector [m v]
  v)

(defmethod mapify clojure.lang.PersistentArrayMap [m v]
  v)

(defmethod mapify clojure.lang.PersistentHashMap [m v]
  v)

(defmethod mapify java.util.Collections$UnmodifiableMap [m v]
  (into-map m (into m v)))

(defmethod mapify :default [_ e]
  (throw (IllegalArgumentException.
          (str "Unable to mapify " e (type e)))))

(defn into-map [m e]
  (if e
    (let [mm (into {} (.getProperties e))]
      ;;(log/info "into-map call" mm)
      (clojure.walk/postwalk #(mapify m %) mm))
    m))

(defn get-root-key [ds id]
  (let [name (:name ds)]
    ;;(log/info "root key" name id)
    (->
     (KeyFactory$Builder. name id)
     (.getKey))))

(defn get-child-key [root-key name version]
  (->
   (KeyFactory$Builder. root-key)
   (.addChild name version)
   (.getKey)))

(defn get-entity-by-key [ds id version]
  (let [dss (:dss ds)
        name (:name ds)
        key-fn (:key-fn ds)
        root-key (get-root-key ds id)
        key (get-child-key root-key name version)]
    (.get dss key)))

(defn exists? [ds id version]
  (let [dss (:dss ds)
        name (:name ds)
        root-key (get-root-key ds id)
        key (get-child-key root-key name version)
        filter (Query$FilterPredicate. Entity/KEY_RESERVED_PROPERTY Query$FilterOperator/EQUAL key)
        query (->
               (Query. name)
               (.setFilter filter)
               (.setKeysOnly))
        cnt (-> (.prepare dss query)
                (.countEntities (FetchOptions$Builder/withDefaults)))]
    (> cnt 0)))

(defn latest-entity [ds id]
  (let [dss (:dss ds)
        name (:name ds)
        root-key (get-root-key ds id)
        filter (Query$FilterPredicate. Entity/KEY_RESERVED_PROPERTY Query$FilterOperator/GREATER_THAN root-key)
        query (->
               (Query. name)
               (.setFilter filter)
               (.addSort "__key__" Query$SortDirection/DESCENDING)
               (.setKeysOnly))]
    (->
     (.prepare dss query)
     (.asIterable (FetchOptions$Builder/withLimit 1))
     first)))

(defn prepared-query [ds id]
  (let [dss (:dss ds)
        name (:name ds)
        root-key (get-root-key ds id)
        filter (Query$FilterPredicate. Entity/KEY_RESERVED_PROPERTY Query$FilterOperator/GREATER_THAN root-key)
        query (->
               (Query. name)
               (.setFilter filter)
               (.addSort "__key__" Query$SortDirection/DESCENDING))
        ;; TODO: fix
        ;; (.setKeysOnly)
        ]
    (.prepare dss query)))

(defn query [ds id]
  (->
   (prepared-query ds id)
   (.asIterable (FetchOptions$Builder/withDefaults))))

(defn count-query [ds id]
  (->
   (prepared-query ds id)
   (.countEntities (FetchOptions$Builder/withDefaults))))

(defn filter-by-geohash [ds geohash]
  (let [dss (:dss ds)
        name (:name ds)
        upper (str geohash "0")
        lower (str geohash "z")
        gt (Query$FilterPredicate. ":geohash" Query$FilterOperator/GREATER_THAN_OR_EQUAL upper)
        lt (Query$FilterPredicate. ":geohash" Query$FilterOperator/LESS_THAN_OR_EQUAL lower)
        cmp (Query$CompositeFilterOperator/and [gt lt])
        query (->
               (Query. name)
               (.setFilter cmp)
               (.addSort ":geohash" Query$SortDirection/ASCENDING))
        results (-> (.prepare dss query)
                    (.asIterable (FetchOptions$Builder/withDefaults)))]
    (map #(into-map {} %) results)))

(defn fetch [ds id & [version]]
  ;;(log/info version)
  (if version
    (into-map {} (get-entity-by-key ds id version))
    (into-map {} (-> (query ds id)
                     (first)))))

(defn fetch-all
  "Fetch all entities of a datastore"
  [ds & {:keys [cursor limit]
         :or {limit 100}}]
  (let [dss (:dss ds)
        name (:name ds)
        query (->
               (Query. name)
               (.addSort "__key__" Query$SortDirection/DESCENDING))
        prepared-query (.prepare dss query)
        fetch-options (FetchOptions$Builder/withLimit limit)
        _ (when cursor
            (.startCursor fetch-options (Cursor/fromWebSafeString cursor)))
        iter (->
              prepared-query
              (.asQueryResultIterator fetch-options))]
    {:cursor (fn [] (-> iter (.getCursor) (.toWebSafeString)))
     :seq (lazy-seq (map #(into-map {} %) (iterator-seq iter)))}))

;; TODO: clean-up fetch call
(defn next-version [ds m key-fn version-default version-set-fn version-key-fn version-fn]
  (let [current (or (version-key-fn m) (version-key-fn (fetch ds (key-fn m))))]
    (if current
      (version-fn (version-set-fn m current))
      (version-set-fn m version-default))))


;; TODO: clean-up
(comment
  (defn store! [ds m]
    (let [name (:name ds)
          dss (:dss ds)
          key-fn (:key-fn ds)
          version-keyword (:version-keyword ds)
          version-default (:version-default ds)
          version-key-fn (:version-key-fn ds)
          version-set-fn (:version-set-fn ds)
          version-fn (:version-fn ds)
          current-version (or (version-key-fn m) version-default)
          root-key (get-root-key ds (key-fn m))
          new-map (next-version ds m key-fn version-default version-set-fn version-key-fn version-fn)
          version (version-key-fn new-map)
          entity (add-properties (Entity. name version root-key) new-map)
          update? (not= (dissoc m version-keyword) (dissoc new-map version-keyword))
          save? (or (not (exists? ds (key-fn m) current-version)) update?)]
      (log/info "should update" update? save?)
      (when save?
        (.put dss entity))
      new-map)))

#_(defn equal-map? [a b]
    (let [[cnt e] (di/diff a b)]
      ;;(log/info cnt e)
      (= 0 cnt)))

(defn store-all! [ds v]
  (let [name (:name ds)
        dss (:dss ds)
        key-fn (:key-fn ds)
        version-key-fn (:version-key-fn ds)
        ;;root-key (get-root-key ds (key-fn m))
        ;;id (key-fn m)
        version (version-key-fn (first v))
        ;;_ (log/info version (first v))
        entities (map #(add-properties (Entity. name version (get-root-key ds (key-fn %))) %)
                      (filter key-fn v))
        ;;entities (map #(Entity. name version (get-root-key ds (key-fn %))) v)
        ]
    ;;(log/info "version" version "entity" (first entities))
    (.put dss entities)
    ))

#_(defn store! [ds m]
    (let [name (:name ds)
          dss (:dss ds)
          key-fn (:key-fn ds)
          version-keyword (:version-keyword ds)
          version-default (:version-default ds)
          version-key-fn (:version-key-fn ds)
          version-set-fn (:version-set-fn ds)
          version-fn (:version-fn ds)
          root-key (get-root-key ds (key-fn m))
          has-version? (version-key-fn m)
          id (key-fn m)]
      (if has-version?
        ;; let client manage versions
        (let [version (version-key-fn m)
              entity (add-properties (Entity. name version root-key) m)
              present? (exists? ds id version)]
          (when (not present?)
            (.put dss entity))
          m)
        (if (not (exists? ds id version-default))
          ;; no default version
          (let [new-map (version-set-fn m version-default)
                version (version-key-fn new-map)
                entity (add-properties (Entity. name version root-key) new-map)]
            (.put dss entity)
            new-map)
          ;; grab latest entity
          (let [last-entity (latest-entity ds id)
                last-version (-> last-entity
                                 .getKey
                                 .getId)
                last-map (into-map {} (get-entity-by-key ds id last-version))
                last-version (version-key-fn last-map)
                new-map (version-fn (version-set-fn m last-version))
                update? (not (equal-map? (dissoc new-map version-keyword) (dissoc last-map version-keyword)))]
            ;;(log/info update?)
            (when update?
              (let [version (version-key-fn new-map)
                    entity (add-properties (Entity. name version root-key) new-map)]
                (.put dss entity)))
            new-map)))))

(defn update! [store id f & args]
  (let [old-value (fetch store id 1)
        new-value (apply f old-value args)]
    (store-all! store [new-value])
    new-value))

(defn cull! [ds id]
  (let [name (:name ds)
        dss (:dss ds)
        version-max (:version-max ds)
        key-fn (:key-fn ds)
        version-keyword (:version-keyword ds)
        version-key-fn (:version-key-fn ds)
        cull-fn (:cull-fn ds)
        cnt (count-query ds id)
        num-entities (- cnt version-max)]
    ;;(log/info cnt num-entities)
    (when (cull-fn cnt)
      ;; TODO: use keys only query
      (let [entities (query ds id)
            keys (map #(.getKey %) (take num-entities (reverse entities)))]
        (.delete dss keys)))))

#_(defn store-n-cull! [ds m]
  (let [key-fn (:key-fn ds)
        id (key-fn m)
        new-map (store! ds m)]
    (cull! ds id)
    new-map))

(defn create-store [name & {:keys [key-keyword key-fn version-keyword version-default version-max version-key-fn version-set-fn version-fn cull-fn]
                            :or {key-keyword :id
                                 version-keyword :v
                                 key-fn :id
                                 version-key-fn :v
                                 version-default 1
                                 version-set-fn (fn [m version] (assoc m :v version))
                                 version-fn (fn [m] (assoc m :v (inc (:v m))))
                                 version-max 30
                                 cull-fn (fn [cnt] (> cnt 30))}}]
  (let [dss (DatastoreServiceFactory/getDatastoreService)]
    {:dss dss
     :name (propertify nil name)
     :key-keyword key-keyword
     :version-keyword version-keyword
     :key-fn key-fn
     :version-default version-default
     :version-key-fn version-key-fn
     :version-set-fn version-set-fn
     :version-fn version-fn
     :version-max version-max
     :cull-fn cull-fn}))
