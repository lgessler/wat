(ns wat.client.ui.admin.project.settings
  "The UI for admin settings on a single project."
  (:require [com.fulcrologic.fulcro.components :as c :refer [defsc]]
            [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
            [com.fulcrologic.fulcro.data-fetch :as df]
            [taoensso.timbre :as log]
            [com.fulcrologic.fulcro.routing.dynamic-routing :as dr]
            [com.fulcrologic.fulcro.components :as comp]
            [com.fulcrologic.fulcro.mutations :as m]
            [wat.client.router :as r]
            [wat.models.project :as prj]
            [wat.client.ui.material-ui :as mui]
            [wat.client.ui.global-snackbar :as snack]
            [wat.client.util :as gcu]))

;; User permissions -----------------------------------------------------------------------------------
;; ====================================================================================================
(defsc UserPermissionListItem [this
                               {:user/keys [id name email privileges] :ui/keys [busy?] :as props}
                               {project-id :project/id}]
  {:query     [:user/id :user/name :user/email :user/privileges :ui/busy?]
   :ident     :user/id
   :pre-merge (fn [{:keys [data-tree]}]
                (merge {:ui/busy?            false
                        :original-privileges (:user/privileges data-tree)}
                       data-tree))}
  (let [on-result (fn [{:server/keys [error? message]}]
                    (m/set-value! this :ui/busy? false)
                    (when error?
                      (snack/message! {:severity "error" :message message})
                      (m/set-value! this :user/privileges (:original-privileges props))))
        on-change (fn [e]
                    (let [v (.-value (.-target e))]
                      (log/info "event" e)
                      (log/info "value" v)
                      (m/set-value! this :busy? true)
                      (m/set-value! this :user/privileges v)
                      (c/transact! this
                                   [(prj/set-user-privileges
                                      {:project/id      project-id
                                       :user/id         id
                                       :user/privileges v})]
                                   {:on-result on-result})))]
    (mui/list-item {}
      (mui/list-item-text {:primary name :secondary email})
      (mui/list-item-secondary-action
        {}
        (mui/minw-100-select
          {:variant  "filled"
           :value    privileges
           :disabled busy?
           :onChange on-change}
          (mui/menu-item {:value "none"} "None")
          (mui/menu-item {:value "reader"} "Reader")
          (mui/menu-item {:value "writer"} "Writer"))))))

(def ui-user-permission-list-item (c/computed-factory UserPermissionListItem {:keyfn :user/id}))

;; Top-level components -------------------------------------------------------------------------------
;; ====================================================================================================
(defsc ProjectSettings [this {:project/keys [id name users]}]
  {:query         [:project/id
                   :project/name
                   {:project/users (c/get-query UserPermissionListItem)}]
   :ident         :project/id
   :initial-state {}
   :pre-merge     (fn [{:keys [data-tree current-normalized] :as m}]
                    ;; initial-state doesn't work for some reason
                    (merge current-normalized
                           data-tree))
   :route-segment (r/last-route-segment :project-settings)
   :will-enter    (fn [app {:keys [id]}]
                    (let [parsed-id (gcu/parse-id id)]
                      (dr/route-deferred
                        [:project/id parsed-id]
                        #(df/load! app [:project/id parsed-id] ProjectSettings
                                   {:post-mutation        `dr/target-ready
                                    :post-mutation-params {:target [:project/id parsed-id]}}))))}
  (mui/container {:maxWidth "lg" :style {:position "relative"}}
    (mui/page-title name)
    (mui/arrow-breadcrumbs {}
      (mui/link {:color "inherit" :href (r/route-for :admin-home) :key "admin"} "Admin Settings")
      (mui/link {:color "inherit" :href (r/route-for :project-overview) :key "project"} "Project Management")
      (mui/link {:color "textPrimary" :underline "none" :key id} name))

    ;; Tab 1: user permissions
    (mui/container {:maxWidth "sm"}
      (mui/padded-paper
        (when users
          (mui/list
            {:subheading "Project Access Privileges"}
            (mapv ui-user-permission-list-item
                  (map #(c/computed % {:project/id id}) users))))))))
