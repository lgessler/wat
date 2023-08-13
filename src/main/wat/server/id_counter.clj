(ns wat.server.id-counter
  (:require [duratom.core :as duratom]
            [mount.core :as mount]
            [wat.server.config :refer [config]]
            [clojure.java.io :as io]))

(mount/defstate id-counter
  :start
  (let [path (str (get-in config [:wat.server.xtdb/config :main-db-dir]) "/"  "id.edn")]
    (io/make-parents path)
    (duratom/duratom :local-file :file-path path :init 1)))