(ns wat.client.ui.document.alignment-editor
  (:require [goog.object :as gobj]
            [com.fulcrologic.fulcro.components :as c :refer [defsc]]
            [com.fulcrologic.fulcro.mutations :as m]
            [com.fulcrologic.fulcro.dom :as dom]
            [com.fulcrologic.fulcro.mutations :as m]
            [com.fulcrologic.fulcro.algorithms.form-state :as fs]
            [com.fulcrologic.fulcro.ui-state-machines :as uism]
            [com.fulcrologic.fulcro.data-fetch :as df]
            [taoensso.timbre :as log]
            [wat.client.router :as r]
            [wat.models.document :as doc]
            [wat.client.ui.material-ui :as mui]
            [wat.client.ui.common.forms :as forms]
            [wat.client.ui.material-ui-icon :as muic]
            ["json-beautify" :as beautify]))

(defsc ProjectQuery [this _]
  {:query [:project/id :project/name]
   :ident :project/id})

(defsc AlignmentEditor [this {:document/keys [id name project combined-target-sentences combined-transl-sentences]
                              :ui/keys       [busy?] :as props}]
  {:query                  [:document/id
                            :document/name
                            :document/combined-target-sentences
                            :document/combined-transl-sentences
                            fs/form-config-join :ui/busy?
                            {:document/project (c/get-query ProjectQuery)}]
   :ident                  :document/id
   :pre-merge              (fn [{:keys [data-tree]}]
                             (merge {:ui/busy? false}
                                    data-tree))
   ::forms/validator       doc/validator
   ::forms/save-mutation   'wat.models.document/save-document
   ::forms/delete-mutation 'wat.models.document/delete-document
   ::forms/delete-message  "Document deleted"
   :form-fields            #{:document/name}}
  (let [dirty (fs/dirty? props)]
    (mui/container {:maxWidth "xl"}
      (mui/vertical-grid {:spacing 3}
        (dom/form
          {:onSubmit (fn [e]
                       (.preventDefault e)
                       (uism/trigger! this ::settings :event/save)
                       (c/transact! this [(fs/clear-complete! {:entity-ident [:document/id id]})]))}
          (mui/vertical-grid
            {:spacing 2}
            (forms/text-input-with-label this :document/name "Name" "Must have 1 to 80 characters"
              {:fullWidth true
               :onChange  (fn [e]
                            (m/set-string!! this :document/name :event e)
                            (c/transact! this [(fs/mark-complete! {:entity-ident [:document/id id]
                                                                   :field        :document/name})]))
               :disabled  busy?})
            ))))))

(def ui-alignment-editor (c/factory AlignmentEditor))

