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
                  :onClick        (fn [_]
                                    (m/set-integer! this :ui/selected-index :value index))}
    (mui/list-item-text {:primary   (str (+ 1 index) ". " (clojure.string/join " " (map first v1)))
                         :secondary (clojure.string/join " " (map first v2))})))

(defn get-token-positions
  "Map tokens to SVG coordinates"
  [ref]
  (if (nil? ref)
    nil
    (let [children (.-children ref)
          left-px (.-x (.getBoundingClientRect ref))]
      (into
        {}
        (map-indexed
          (fn [i c]
            (let [bcr (.getBoundingClientRect (gobj/get (.-children c) 0))
                  x (.-x bcr)
                  width (.-width bcr)
                  rel-x (- x left-px)]
              [i (/ (+ rel-x (+ rel-x width)) 2)]))
          children)))))

(defsc AlignmentEditor [this {:document/keys [id name project combined-target-sentences combined-transl-sentences]
                              :ui/keys       [first-render? selected-index] :as props}]
  {:query          [:document/id
                    :document/name
                    :document/combined-target-sentences
                    :document/combined-transl-sentences
                    :ui/first-render? :ui/selected-index
                    {:document/project (c/get-query ProjectQuery)}]
   :ident          :document/id
   :initLocalState (fn [this _]
                     {:save-top-ref (fn [r] (gobj/set this "top-ref" r))
                      :save-bot-ref (fn [r] (gobj/set this "bot-ref" r))})
   :pre-merge      (fn [{:keys [data-tree]}]
                     (merge {:ui/first-render?  true
                             :ui/selected-index 0}
                            data-tree))}
  (let [selected-target (get combined-target-sentences selected-index)
        selected-transl (get combined-transl-sentences selected-index)
        save-top-ref (c/get-state this :save-top-ref)
        save-bot-ref (c/get-state this :save-bot-ref)
        top-ref (gobj/get this "top-ref")
        bot-ref (gobj/get this "bot-ref")
        top-width (and top-ref (.-width (.getBoundingClientRect top-ref)))
        bot-width (and bot-ref (.-width (.getBoundingClientRect bot-ref)))
        max-width (when (and top-width bot-width) (max top-width bot-width))
        top-tok-pos (get-token-positions top-ref)
        bot-tok-pos (get-token-positions bot-ref)]
    ;; On first render, we don't have width information yet. So force a re-render by changing this prop.
    (when (nil? max-width)
      (m/set-value! this :ui/first-render? false))

    ;; Note we hide the container way off-screen at first until we get information about dimensions
    (mui/container {:maxWidth "xl" :style {:position "relative"
                                           :top      (if (nil? max-width) "-10000px" "0")}}
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
                 :margin    "1em 0"
                 :overflowX "auto"}}
        (dom/div {:style {:marginTop "5em" :whiteSpace "nowrap"}
                  :ref   save-top-ref}
          (map-indexed
            (fn [i word]
              (dom/div {:style {:display "inline-block" :margin "0 0.5em"}
                        :key   (str i)}
                (map-indexed
                  (fn [j piece]
                    (dom/div {:key j} piece))
                  word)))
            selected-target))
        (when max-width
          (dom/svg {:width max-width :height 100}
            (let [x1 (get top-tok-pos 3)
                  x2 (get bot-tok-pos 8)]
              (c/fragment
                (dom/line {:x1 x1 :x2 x2 :y1 0 :y2 100
                           :fill "black" :stroke-width "8" :stroke-opacity 0.5 :stroke-linecap "butt" :stroke "white"
                           :style {:cursor "pointer"}})
                (dom/line {:x1    x1 :x2 x2 :y1 0 :y2 100
                           :fill  "black" :stroke-width "2" :stroke-linecap "butt" :stroke "black"
                           :style {:cursor "pointer"}})))))
        (dom/div {:style {:marginTop "0em" :marginBottom "5em" :whiteSpace "nowrap"}
                  :ref   save-bot-ref}
          (map-indexed
            (fn [i word]
              (dom/div {:style {:display "inline-block" :margin "0 0.5em"}
                        :key   (str i)}
                (map-indexed
                  (fn [j piece]
                    (dom/div {:key j} piece))
                  word)))
            selected-transl))))))

(def ui-alignment-editor (c/factory AlignmentEditor))

