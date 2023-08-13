(ns wat.client.ui.drawer
  (:require
    [com.fulcrologic.fulcro.components :as c :refer [defsc]]
    [com.fulcrologic.fulcro.components :as comp]
    [wat.models.session :as session :refer [Session session-join valid-session?]]
    [wat.client.router :as r]
    [wat.client.ui.material-ui :as mui]
    [wat.client.ui.material-ui-icon :as muic]))

(def ident [:component/id :drawer])

(defn drawer-item
  ([route-key text icon onClose]
   (drawer-item route-key text icon onClose nil))
  ([route-key text icon onClose divider]
   (mui/list-item {:key     text
                   :button  true
                   :divider (boolean divider)
                   :onClick (fn [e]
                              (onClose)
                              (r/route-to! route-key))}
     (mui/list-item-icon {} (icon))
     (mui/list-item-text {} text))))

(defsc Drawer [this props {:keys [onClose open?]}]
  {:query         [session-join]
   :ident         (fn [] ident)
   :initial-state {}}
  (let [admin? (session/admin? props)]
    (mui/drawer
      {:open    open?
       :onClose onClose
       :anchor  "left"}
      ((mui/styled-list {:width 300}) {}

       (drawer-item :projects "Projects" muic/home onClose)
       (drawer-item :user-settings "Settings" muic/settings onClose admin?)

       (when admin?
         (drawer-item :admin-home "Admin Settings" muic/supervisor-account onClose))))))

(def ui-drawer (c/factory Drawer))