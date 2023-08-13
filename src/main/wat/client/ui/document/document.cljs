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

(declare Document)
(defn do-load!
  ([app-or-comp doc-id load-opts]
   (do-load! app-or-comp doc-id load-opts false))
  ([app-or-comp doc-id load-opts mark-ready?]
   (let [ident [:document/id doc-id]]
     (when doc-id
       (df/load! app-or-comp ident Document
                 (merge load-opts
                        (when mark-ready?
                          {:post-mutation        `dr/target-ready
                           :post-mutation-params {:target ident}})))))))

(defsc Document
  [this {:document/keys [id name project] :ui/keys [busy?] :as props}]
  {:query                [:document/id :document/name
                          {:document/project (c/get-query ProjectNameQuery)}
                          :ui/active-tab
                          :ui/busy?
                          sn/session-join]
   :ident                :document/id
   :pre-merge            (fn [{:keys [data-tree state-map]}]
                           (merge {:ui/busy? false}
                                  data-tree))
   :route-segment        (r/last-route-segment :document)
   :will-enter           (fn [app {:keys [id] :as route-params}]
                           (let [parsed-id (gcu/parse-id id)]
                             (when parsed-id
                               (dr/route-deferred
                                 [:document/id parsed-id]
                                 (fn []
                                   (do-load! app parsed-id {} true))))))}

  (mui/container {:maxWidth "xl"}
    (mui/page-title name)
    (mui/arrow-breadcrumbs {}
      (mui/link {:color "inherit" :href (r/route-for :projects) :key "projects"} "Projects")
      (mui/link {:color "inherit" :href (r/route-for :project {:id (:project/id project)}) :key "project"} (:project/name project))
      (mui/link {:color "textPrimary" :underline "none" :key "document"} name))

    (mui/paper {}
      (if busy?
        (wat.client.ui.common.core/loader)
        "todo"))))
