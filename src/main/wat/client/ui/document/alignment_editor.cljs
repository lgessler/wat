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

(defn sentence-list-item
  [this index v1 v2 selected-index]
  (mui/list-item {:key            (str index)
                  :button         true
                  :disableGutters true
                  :selected       (= index selected-index)
                  :onClick        #(m/set-integer! this :ui/selected-index :value index)}
    (mui/list-item-text {:primary   (str (+ 1 index) ". " (clojure.string/join " " (map first v1)))
                         :secondary (clojure.string/join " " (map first v2))})))

(defsc AlignmentEditor [this {:document/keys [id name project combined-target-sentences combined-transl-sentences]
                              :ui/keys       [busy? selected-index] :as props}]
  {:query     [:document/id
               :document/name
               :document/combined-target-sentences
               :document/combined-transl-sentences
               :ui/busy? :ui/selected-index
               {:document/project (c/get-query ProjectQuery)}]
   :ident     :document/id
   :pre-merge (fn [{:keys [data-tree]}]
                (merge {:ui/busy?          false
                        :ui/selected-index 0}
                       data-tree))}
  (let [selected-target (get combined-target-sentences selected-index)
        selected-transl (get combined-transl-sentences selected-index)]
    (mui/container {:maxWidth "xl"}
      (mui/box
        {:style {:maxHeight "200px"
                 :overflowY "scroll"}}
        (mui/list {:dense true}
          (map-indexed
            (fn [i [s1 s2]]
              (sentence-list-item this i s1 s2 selected-index))
            (partition 2 (interleave combined-target-sentences combined-transl-sentences)))))
      (mui/box
        {:style {:borderTop "2px solid gray"
                 :margin "1em 0"
                 :overflowX "auto"}}
        (dom/div {:style {:marginTop "5em" :white-space "nowrap"}}
          (for [word selected-target]
            (dom/div {:style {:display "inline-block" :margin "0 0.5em"}}
              (for [piece word]
                (dom/div piece)))))
        (dom/div {:style {:marginTop "5em" :marginBottom "5em" :white-space "nowrap"}}
          (for [word selected-transl]
            (dom/div {:style {:display "inline-block" :margin "0 0.5em"}}
              (for [piece word]
                (dom/div piece)))))))))

(def ui-alignment-editor (c/factory AlignmentEditor))

