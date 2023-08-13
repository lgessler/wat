(ns wat.client.ui.admin.project.core
  (:require
    [com.fulcrologic.fulcro.routing.dynamic-routing :as dr :refer [defrouter]]
    [wat.client.router :as r]
    [wat.client.ui.common.core :refer [loader]]
    [wat.client.ui.admin.project.overview :refer [ProjectOverview]]
    [wat.client.ui.admin.project.settings :refer [ProjectSettings]]))

(defrouter ProjectAdminRouter
           [this {:keys [current-state route-factory route-props pending-path-segment]}]
           {:route-segment       (r/router-segment :project-admin-router)
            :router-targets      [ProjectOverview ProjectSettings]
            :always-render-body? false}
           (loader))
