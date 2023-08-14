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
            [wat.client.ui.document.settings :as settings :refer [Settings ui-settings]]
            [wat.client.ui.document.alignment-editor :refer [AlignmentEditor ui-alignment-editor]]
            [wat.models.session :as sn]))

(declare Document)
(defsc ProjectNameQuery
  [this props]
  {:query [:project/id :project/name]
   :ident :project/id})

(defn do-load!
  ([app-or-comp doc-id load-opts]
   (do-load! app-or-comp doc-id load-opts false))
  ([app-or-comp doc-id load-opts mark-ready?]
   (let [ident [:document/id doc-id]]
     (when doc-id
       (df/load! app-or-comp ident Document
                 (merge {:without [sn/session-ident]}
                        load-opts
                        (when mark-ready?
                          {:post-mutation        `dr/target-ready
                           :post-mutation-params {:target ident}})))))))

(m/defmutation change-tab [{:keys [tab]}]
  (action [{:keys [state ref component]}]
          (swap! state (fn [s]
                         (-> s
                             (assoc-in (conj ref :ui/busy?) true)
                             (assoc-in (conj ref :ui/active-tab) tab)
                             (assoc-in sn/session-ident (sn/session-assoc (get-in s sn/session-ident) (conj ref ::tab) tab)))))
          (do-load! component (last ref) {:post-action #(m/set-value! component :ui/busy? false)})))

(defsc Document
  [this {:document/keys [id name project] :ui/keys [active-tab busy?] :>/keys [settings alignment-editor] :as props}]
  {:query         [:document/id :document/name
                   {:document/project (c/get-query ProjectNameQuery)}
                   {:>/settings (c/get-query Settings)}
                   {:>/alignment-editor (c/get-query AlignmentEditor)}
                   sn/session-join :ui/active-tab :ui/busy?]
   :ident         :document/id
   :route-segment (r/last-route-segment :document)
   :pre-merge     (fn [{:keys [data-tree state-map]}]
                    (let [q-params (r/get-query-params)
                          session (get-in state-map sn/session-ident)
                          ident [:document/id (:document/id data-tree)]
                          tab-session-key (conj ident ::tab)
                          tab (or (sn/session-get session tab-session-key)
                                  (:tab q-params)
                                  "align")]
                      (when (not= tab (:tab q-params))
                        (r/assoc-query-param! :tab tab))
                      (merge {:ui/active-tab tab
                              :ui/busy?      false}
                             data-tree
                             {sn/session-ident (sn/session-assoc session tab-session-key tab)})))
   :will-enter    (fn [app {:keys [id] :as route-params}]
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
      (mui/tab-context {:value active-tab}
        (mui/tabs {:value    active-tab
                   :onChange (fn [_ val]
                               (c/transact! this [(change-tab {:tab val})])
                               (when (= val "settings")
                                 (uism/begin! this forms/edit-form-machine ::settings/settings
                                              {:actor/form (uism/with-actor-class [:document/id id] Settings)})))}
          (mui/tab {:label "Align" :value "align" :disabled busy?})
          (mui/tab {:label "Settings" :value "settings" :disabled busy?}))

        (c/fragment
          (mui/tab-panel {:value "align"}
            (if busy?
              (wat.client.ui.common.core/loader)
              (ui-alignment-editor alignment-editor)))
          (mui/tab-panel {:value "settings"}
            (if busy?
              (wat.client.ui.common.core/loader)
              (ui-settings settings))))))))
