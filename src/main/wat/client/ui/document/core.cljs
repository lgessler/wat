(ns wat.client.ui.document.core
  (:require
    [com.fulcrologic.fulcro.components :as c :refer [defsc]]
    [com.fulcrologic.fulcro.routing.dynamic-routing :as dr :refer [defrouter]]
    [wat.client.router :as r]
    [wat.client.ui.common.core :refer [loader]]
    [wat.client.ui.document.document :refer [Document]]
    [taoensso.timbre :as log]))

(defrouter DocumentRouter
  [this props]
  {:route-segment       (r/router-segment :document-router)
   :router-targets      [Document]
   :always-render-body? false}
  (loader))
