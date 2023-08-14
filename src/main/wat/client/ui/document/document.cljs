(ns wat.client.ui.document.document
  (:require [com.fulcrologic.fulcro.components :as c :refer [defsc]]
            [com.fulcrologic.fulcro.routing.dynamic-routing :as dr]
            [com.fulcrologic.fulcro.data-fetch :as df]
            [wat.client.router :as r]
            [wat.client.util :as gcu]
            [wat.client.ui.material-ui :as mui]
            [taoensso.timbre :as log]
            [com.fulcrologic.fulcro.mutations :as m]
            [com.fulcrologic.fulcro.application :as app]
            [com.fulcrologic.fulcro.ui-state-machines :as uism]
            [wat.client.ui.common.forms :as forms]
            [wat.models.session :as sn]))

(defsc ProjectNameQuery
  [this props]
  {:query [:project/id :project/name]
   :ident :project/id})

(defsc Document
  [this {:document/keys [id name project] :as props}]
  {:query         [:document/id :document/name
                   {:document/project (c/get-query ProjectNameQuery)}
                   sn/session-join]
   :ident         :document/id
   :route-segment (r/last-route-segment :document)
   :will-enter    (fn [app {:keys [id] :as route-params}]
                    (let [parsed-id (gcu/parse-id id)
                          ident [:document/id parsed-id]]
                      (when parsed-id
                        (dr/route-deferred
                          ident
                          (fn []
                            (df/load! app ident Document
                                      {:post-mutation        `dr/target-ready
                                       :post-mutation-params {:target ident}
                                       :update-query         #(df/elide-query-nodes % #{sn/session-ident})}))))))}

  (mui/container {:maxWidth "xl"}
    (mui/page-title name)
    (mui/arrow-breadcrumbs {}
      (mui/link {:color "inherit" :href (r/route-for :projects) :key "projects"} "Projects")
      (mui/link {:color "inherit" :href (r/route-for :project {:id (:project/id project)}) :key "project"} (:project/name project))
      (mui/link {:color "textPrimary" :underline "none" :key "document"} name))
    (mui/paper {}
      "todo")))
