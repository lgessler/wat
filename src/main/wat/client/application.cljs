(ns wat.client.application
  (:require
    [com.fulcrologic.fulcro.application :as app]
    [com.fulcrologic.fulcro.networking.http-remote :as net]
    [com.fulcrologic.fulcro.networking.websockets :as fws]
    [com.fulcrologic.fulcro.algorithms.tx-processing :as txp]
    [com.fulcrologic.fulcro.algorithms.tx-processing.synchronous-tx-processing :as stx]
    [com.fulcrologic.fulcro.rendering.keyframe-render :as kfr]
    [com.fulcrologic.fulcro.rendering.keyframe-render2 :as kfr2]
    [com.fulcrologic.fulcro.rendering.ident-optimized-render :as ier]
    [com.fulcrologic.fulcro.react.version18 :refer [with-react18]]
    [com.fulcrologic.fulcro.mutations :as m]
    [taoensso.timbre :as log]
    [wat.algos.subs :refer [push-handler]])
  (:import [goog.async Debouncer]))

(defn debounce [f interval]
  (let [dbnc (Debouncer. f interval)]
    ;; We use apply here to support functions of various arities
    (fn [& args] (.apply (.-fire dbnc) dbnc (to-array args)))))

(goog-define LOG-RESPONSES false)

(defn wrap-accept-transit
  "For servers that do content negotiation, tell them we want transit+json"
  [handler]
  (fn [req]
    (handler (assoc-in req [:headers "Accept"] "application/transit+json"))))

(defn get-token []
  (if (exists? js/fulcro_network_csrf_token)
    js/fulcro_network_csrf_token
    "TOKEN-NOT-IN-HTML!"))

(defn request-middleware []
  (js/console.log "js/fulcro_network_csrf_token: " (get-token))
  ;; The CSRF token is embedded via service.clj
  (->
    (net/wrap-csrf-token (get-token))
    (net/wrap-fulcro-request)
    (wrap-accept-transit)))

;; To view the map response as a map of data uncomment this:
(defn resp-logger [handler]
  (fn [resp]
    (let [out (handler resp)]
      (log/info "\nRESPONSE: ")
      ;(log/info "RESP_LOGGER: ") (log/info (with-out-str (pprint out)))
      out)))

(defn response-middleware []
  (cond-> (net/wrap-fulcro-response)
          LOG-RESPONSES resp-logger))

(defn api-remote []
  (net/fulcro-http-remote
    {:url                 "/api"
     :response-middleware (response-middleware)
     :request-middleware  (request-middleware)}))

(defn mutation?
  [body]
  (and
    (map? body)
    (-> body keys first symbol?)))

(defn mutation-error? [result]
  (let [body (:body result)
        is-mutation? (mutation? body)]
    (if is-mutation?
      (let [mutation-sym (-> body keys first)
            response-error? (-> body mutation-sym :server/error?)
            pathom-error (-> body mutation-sym :com.wsscode.pathom.core/reader-error)]
        (log/info "Result body: " body)
        (boolean (or response-error? pathom-error)))
      false)))

(defn read-error? [result]
  (let [body (:body result)]
    (and (map? body)
         (= (-> body keys first body) :com.wsscode.pathom.core/reader-error))))

(defn remote-error?
  [result]
  (let [status (:status-code result)
        resp (or (not= status 200) (mutation-error? result)
                 (read-error? result))]
    (log/info "Remote error? " resp)
    resp))

(defonce SPA
         (->
           (app/fulcro-app
             {:remote-error?     remote-error?
              :remotes           {:remote  (fws/fulcro-websocket-remote {:csrf-token   (get-token)
                                                                         :push-handler (debounce push-handler 1000)})
                                  :session (api-remote)}
              :optimized-render! kfr2/render!
              ;; Modify the default result action so that it looks for :on-result, :on-ok and :on-error
              ;; see, for an example, change_password.cljs
              :default-result-action!
              (fn [{:keys [transacted-ast result] ::txp/keys [options] :as env}]
                (js/console.log (pr-str options))
                (js/console.log (pr-str (keys env)))
                (m/default-result-action! env)
                (when-let [on-result (:on-result options)]
                  (on-result (get-in result [:body (:dispatch-key transacted-ast)])))
                (if (remote-error? result)
                  (when-let [on-error (:on-error options)]
                    (on-error (get-in result [:body (:dispatch-key transacted-ast)])))
                  (when-let [on-ok (:on-ok options)]
                    (on-ok (get-in result [:body (:dispatch-key transacted-ast)])))))})
           with-react18
           stx/with-synchronous-transactions))
