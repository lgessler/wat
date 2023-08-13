(ns wat.client.ui.project.core
  (:require
    [com.fulcrologic.fulcro.components :as c :refer [defsc]]
    [com.fulcrologic.fulcro.routing.dynamic-routing :as dr :refer [defrouter]]
    [com.fulcrologic.fulcro.data-fetch :as df]
    [wat.client.router :as r]
    [wat.client.ui.common.core :refer [loader]]
    [wat.client.ui.project.projects-page :refer [ProjectsPage]]
    [wat.client.ui.project.project :refer [Project]]
    [taoensso.timbre :as log]))


(defrouter ProjectRouter
  [this {:keys [current-state route-factory route-props]}]
  {:route-segment  (r/router-segment :project-router)
   :router-targets [ProjectsPage Project]
   :always-render-body? false}
  (loader))
