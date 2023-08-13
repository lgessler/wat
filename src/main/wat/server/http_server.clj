(ns wat.server.http-server
  (:require
    [clojure.pprint :refer [pprint]]
    [mount.core :refer [defstate]]
    [org.httpkit.server :as http-kit]
    [wat.server.config :refer [config]]
    [wat.server.middleware :refer [middleware]]
    [taoensso.timbre :as log]))

;; https://github.com/ptaoussanis/sente/blob/master/src/taoensso/sente/server_adapters/jetty9.clj
;; https://github.com/pedestal/pedestal/blob/master/jetty/src/io/pedestal/http/jetty/websockets.clj
;; https://github.com/pedestal/pedestal/blob/master/samples/jetty-web-sockets/src/jetty_web_sockets/service.clj
;; https://github.com/fulcrologic/fulcro-websockets/blob/develop/src/main/com/fulcrologic/fulcro/networking/websocket_remote.cljc

(defstate http-server
  :start
  (let [http-kit-config (::http-kit/config config)
        port (:port http-kit-config)]
    (when (nil? port)
      (throw (Exception. "You must set a port as the environment variable PORT.")))
    (log/info "Starting server on port" port)
    (let [stop-server (http-kit/run-server middleware http-kit-config)]
      (fn []
        (stop-server))))

  :stop
  (http-server))

