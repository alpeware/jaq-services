(ns jaq.services.iam
  (:refer-clojure :exclude [list get])
  (:require
   [clojure.data.json :as json]
   [clojure.tools.logging :as log]
   [clj-http.lite.client :as http]
   [clojure.string :as string]
   [jaq.services.util :as util]))

(def endpoint "https://iam.googleapis.com")
(def version "v1")
(def default-endpoint [endpoint version])
(def action (partial util/action default-endpoint))

(defn roles []
  (action :get [:roles]))
