(ns wat.client.ui.document.alignment-editor
  (:require [goog.object :as gobj]
            [com.fulcrologic.fulcro.components :as c :refer [defsc]]
            [com.fulcrologic.fulcro.mutations :as m]
            [com.fulcrologic.fulcro.dom :as dom]
            [com.fulcrologic.fulcro.application :as app]
            [com.fulcrologic.fulcro.mutations :as m]
            [com.fulcrologic.fulcro.algorithms.form-state :as fs]
            [com.fulcrologic.fulcro.ui-state-machines :as uism]
            [com.fulcrologic.fulcro.data-fetch :as df]
            [taoensso.timbre :as log]
            [wat.client.router :as r]
            [wat.models.document :as doc]
            [wat.client.ui.material-ui :as mui]
            [wat.models.session :as sn]))

(m/defmutation add-alignment [{:keys [b e sentence-index user-email] id :document/id}]
  (action [{:keys [state ref]}]
          (swap! state
                 (fn [s]
                   (-> s
                       (update-in (into ref [:document/alignments user-email sentence-index]) conj {:b b :e e})
                       (update-in (into ref [:document/alignments user-email sentence-index]) set)))))
  (remote [{:keys [ast]}]
          (assoc ast :key `doc/add-alignment))
  (result-error [{:keys [state ref]}]
                (swap! state
                       (fn [s]
                         (-> s
                             (update-in (into ref [:document/alignments user-email sentence-index]) disj {:b b :e e}))))))

(m/defmutation delete-alignment [{:keys [b e sentence-index user-email] id :document/id}]
  (action [{:keys [state ref]}]
          (swap! state
                 (fn [s]
                   (-> s
                       (update-in (into ref [:document/alignments user-email sentence-index]) disj {:b b :e e})))))
  (remote [{:keys [ast]}]
          (assoc ast :key `doc/delete-alignment))
  (result-error [{:keys [state ref]}]
                (swap! state
                       (fn [s]
                         (-> s
                             (update-in (into ref [:document/alignments user-email sentence-index]) conj {:b b :e e}))))))



(defsc ProjectQuery [this _]
  {:query [:project/id :project/name]
   :ident :project/id})

(defn sentence-list-item
  [this index v1 v2 sentence-index]
  (mui/list-item {:key            (str index)
                  :button         true
                  :disableGutters true
                  :selected       (= index sentence-index)
                  :onClick        (fn [_]
                                    (m/set-integer! this :ui/sentence-index :value index)
                                    ;; TODO: bandaid fix for alignments being rendered wrongly, find right solution
                                    (js/setTimeout #(app/force-root-render! wat.client.application/SPA) 200))}
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

(defsc AlignmentEditor [this {:document/keys [id name project combined-target-sentences combined-transl-sentences
                                              alignments]
                              :ui/keys       [first-render? sentence-index potential-alignment] :as props}]
  {:query          [:document/id
                    :document/name
                    :document/combined-target-sentences
                    :document/combined-transl-sentences
                    :document/alignments
                    :ui/first-render? :ui/sentence-index :ui/potential-alignment
                    {:document/project (c/get-query ProjectQuery)}
                    sn/session-ident]
   :ident          :document/id
   :initLocalState (fn [this _]
                     {:save-top-ref (fn [r] (gobj/set this "top-ref" r))
                      :save-bot-ref (fn [r] (gobj/set this "bot-ref" r))})
   :pre-merge      (fn [{:keys [data-tree]}]
                     (merge {:ui/first-render?       true
                             :ui/potential-alignment nil
                             :ui/sentence-index      0}
                            data-tree))}
  (let [user-email (get-in (app/current-state this) (conj sn/session-ident :user/email))
        selected-target (get combined-target-sentences sentence-index)
        selected-transl (get combined-transl-sentences sentence-index)
        save-top-ref (c/get-state this :save-top-ref)
        save-bot-ref (c/get-state this :save-bot-ref)
        top-ref (gobj/get this "top-ref")
        bot-ref (gobj/get this "bot-ref")
        top-width (and top-ref (.-width (.getBoundingClientRect top-ref)))
        bot-width (and bot-ref (.-width (.getBoundingClientRect bot-ref)))
        max-width (when (and top-width bot-width) (max top-width bot-width))
        top-tok-pos (get-token-positions top-ref)
        bot-tok-pos (get-token-positions bot-ref)
        on-mouse-enter (fn [top? token-index]
                         (fn [e]
                           (.preventDefault e)
                           (let [started-top? (:started-top? potential-alignment)]
                             (cond (nil? potential-alignment)
                                   nil

                                   (= started-top? top?)
                                   nil

                                   (and top? (not (contains? potential-alignment :b)))
                                   (m/set-value! this :ui/potential-alignment (assoc potential-alignment :b token-index))

                                   (and (not top?) (not (contains? potential-alignment :e)))
                                   (m/set-value! this :ui/potential-alignment (assoc potential-alignment :e token-index))))))
        on-mouse-down (fn [top? token-index]
                        (fn [e]
                          (.preventDefault e)
                          (m/set-value!
                            this
                            :ui/potential-alignment
                            {:started-top?   top?
                             (if top? :b :e) token-index})))
        specific-alignments (get-in alignments [user-email sentence-index])]

    ;; On first render, we don't have width information yet. So force a re-render by changing this prop.
    (when (nil? max-width)
      (m/set-value! this :ui/first-render? false))

    ;; Note we hide the container way off-screen at first until we get information about dimensions
    (mui/container {:maxWidth  "xl" :style {:position "relative" :top (if (nil? max-width) "-10000px" "0")}
                    :onMouseUp (fn [e]
                                 (if (and (contains? potential-alignment :e)
                                          (contains? potential-alignment :b))
                                   (do
                                     (c/transact! this [(add-alignment (-> potential-alignment
                                                                           (select-keys [:b :e])
                                                                           (merge {:ident          [:document/id id]
                                                                                   :sentence-index sentence-index
                                                                                   :user-email     user-email})))])
                                     (m/set-value! this :ui/potential-alignment nil))
                                   (m/set-value! this :ui/potential-alignment nil)))}
      ;; Sentences
      (mui/box
        {:style {:maxHeight "200px"
                 :overflowY "scroll"}}
        (mui/list {:dense true}
          (map-indexed
            (fn [i [s1 s2]]
              (sentence-list-item this i s1 s2 sentence-index))
            (partition 2 (interleave combined-target-sentences combined-transl-sentences)))))

      ;; Alignments
      (mui/box
        {:style {:borderTop "2px solid gray"
                 :margin    "1em 0"
                 :overflowX "auto"}}

        ;; Target
        (dom/div {:style {:marginTop "5em" :whiteSpace "nowrap"}
                  :ref   save-top-ref}
          (map-indexed
            (fn [i word]
              (dom/div {:style        {:display "inline-block" :margin "0.5em" :cursor "pointer"}
                        :key          (str i)
                        :onMouseEnter (on-mouse-enter true i)
                        :onMouseDown  (on-mouse-down true i)}
                (map-indexed
                  (fn [j piece]
                    (dom/div
                      {:key j}
                      piece))
                  word)))
            selected-target))

        ;; SVG
        (when max-width
          (dom/svg {:width       max-width :height 100
                    :style       {:padding "0em" :margin "0em"}
                    :onMouseMove (fn [e]
                                   (.preventDefault e)
                                   (when potential-alignment
                                     (let [rect (.getBoundingClientRect (gobj/get (.-children (.-parentNode bot-ref)) 1))
                                           base-x (.-left rect)
                                           base-y (.-top rect)
                                           page-x (.-clientX e)
                                           page-y (.-clientY e)]
                                       (m/set-value! this :ui/potential-alignment
                                                     (-> potential-alignment
                                                         (assoc :x (- page-x base-x))
                                                         (assoc :y (- page-y base-y)))))))}
            (when-let [{:keys [x y started-top? b e]} potential-alignment]
              (let [x1 (if started-top? (get top-tok-pos b) (get bot-tok-pos e))
                    y1 (if started-top? 0 100)]
                (when (and x y)
                  (dom/line {:x1   x1 :y1 y1 :x2 x :y2 y
                             :fill "black" :strokeWidth "2" :strokeLinecap "butt" :stroke "black"}))))
            (for [{:keys [b e]} specific-alignments]
              (let [x1 (get top-tok-pos b)
                    x2 (get bot-tok-pos e)
                    delete #(c/transact! this [(delete-alignment {:ident          [:document/id id]
                                                                  :sentence-index sentence-index
                                                                  :user-email     user-email
                                                                  :b              b
                                                                  :e              e})])]
                (c/fragment
                  (dom/line {:x1      x1 :x2 x2 :y1 0 :y2 100
                             :fill    "black" :strokeWidth "8" :strokeOpacity 0.5 :strokeLinecap "butt" :stroke "white"
                             :style   {:cursor "pointer"}
                             :onClick delete})
                  (dom/line {:x1      x1 :x2 x2 :y1 0 :y2 100
                             :fill    "black" :strokeWidth "2" :strokeLinecap "butt" :stroke "black"
                             :style   {:cursor "pointer"}
                             :onClick delete}))))))

        ;; Translation
        (dom/div {:style {:marginTop "0em" :marginBottom "5em" :whiteSpace "nowrap"}
                  :ref   save-bot-ref}
          (map-indexed
            (fn [i word]
              (dom/div {:style        {:display "inline-block" :margin "0.5em" :cursor "pointer"}
                        :key          (str i)
                        :onMouseEnter (on-mouse-enter false i)
                        :onMouseDown  (on-mouse-down false i)}
                (map-indexed
                  (fn [j piece]
                    (dom/div {:key j} piece))
                  word)))
            selected-transl))))))

(def ui-alignment-editor (c/factory AlignmentEditor))

