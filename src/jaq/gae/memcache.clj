(ns jaq.gae.memcache
  (:refer-clojure :exclude [push pop peek read])
  (:require
   [cognitect.transit :refer [writer write reader read]])
  (:import
   [java.io ByteArrayInputStream ByteArrayOutputStream]
   [com.google.appengine.api.memcache
    MemcacheService
    MemcacheServiceFactory]))

(def ^:dynamic *string-encoding* "UTF-8")

(defn write-str
  "Writes a value to a string."
  ([o] (write-str o :json {}))
  ([o type] (write-str o type {}))
  ([o type opts]
   (let [out (ByteArrayOutputStream.)
         writer (writer out type opts)]
     (write writer o)
     (.toString out *string-encoding*))))

(defn read-str
  "Reads a value from a decoded string"
  ([s] (read-str s :json {}))
  ([s type] (read-str s type {}))
  ([^String s type opts]
   (let [in (ByteArrayInputStream. (.getBytes s *string-encoding*))]
     (read (reader in type opts)))))

(defn push
  "Pushes a map to memcache based on its keys."
  [m]
  (let [ms (MemcacheServiceFactory/getMemcacheService)]
    (.putAll ms (into {} (for [[k v] m] [k (write-str v)])))))

(defn pop
  "Pops a key/vec of keys from memcache."
  [ks]
  (let [ms (MemcacheServiceFactory/getMemcacheService)
        keys (if (vector? ks) ks [ks])
        m (.getAll ms keys)
        _ (doseq [k ks]
            (.delete ms k))]
    (into {} (for [[k v] m] [k (read-str v)]))))

(defn peek
  "Peeks a key/vec of keys from memcache."
  [ks]
  (let [ms (MemcacheServiceFactory/getMemcacheService)
        keys (if (vector? ks) ks [ks])
        m (.getAll ms keys)]
    (into {} (for [[k v] m] [k (read-str v)]))))

#_(

   *ns*
   (in-ns 'jaq.services.memcache)

   (-> (javax.cache.CacheManager/getInstance)
       (clojure.reflect/reflect)
       :members
       )
   javax.cache.CacheFactory
   javax.cache.Caching

   (-> (^com.google.apphosting.api.ApiProxy$Environment com.google.apphosting.api.ApiProxy/getCurrentEnvironment)
       (clojure.reflect/reflect)
       :members
       )
   (import 'com.google.apphosting.api.ApiProxy$Environment)

   (-> com.google.apphosting.api.ApiProxy
       (clojure.reflect/reflect)
       :members
       #_count)

   (-> (com.google.appengine.api.memcache.MemcacheServicePb$MemcacheGetRequest/newBuilder)
       (.addKey (com.google.appengine.api.memcache.AsyncMemcacheServiceImpl/makePbKey "foo")))

   (-> (com.google.apphosting.api.ApiProxy/getDelegate)
       (.makeAsyncCall (com.google.apphosting.api.ApiProxy/getCurrentEnvironment) "memcache" "Get"  nil)
       )

   (-> (com.google.appengine.api.memcache.MemcacheServiceFactory/getMemcacheService)
       (.contains :foo))

   (-> (com.google.appengine.api.memcache.MemcacheServiceFactory/getMemcacheService)
       (.put :foo :bar))

   (-> (com.google.appengine.api.memcache.MemcacheServiceFactory/getMemcacheService)
       (.get :foo))


   (-> (com.google.appengine.api.memcache.AsyncMemcacheServiceImpl. nil))

   )
