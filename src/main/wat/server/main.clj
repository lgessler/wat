(ns wat.server.main
  (:require
    [mount.core :as mount]
    wat.server.http-server)
  (:gen-class))

(defn -main [& args]
  (println "args: " args)
  (mount/start-with-args {:config "config/prod.edn"}))
