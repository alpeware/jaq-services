(ns jaq.services.env
  (:require
   [clojure.walk :as walk]))

(def env
  (->> (System/getenv)
       (into {})
       (walk/keywordize-keys)))
