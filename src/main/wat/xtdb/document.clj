(ns wat.xtdb.document
  (:require [xtdb.api :as xt]
            [wat.xtdb.util :as xutil]
            [wat.xtdb.easy :as gxe])
  (:refer-clojure :exclude [get merge]))

(def attr-keys [:document/id
                :document/name
                :document/project
                :document/combined-target-sentences
                :document/combined-transl-sentences
                :document/alignments])

(defn xt->pathom [doc]
  (when doc
    (-> doc
        (update :document/project xutil/identize :project/id))))

(defn create* [{:document/keys [id] :as attrs}]
  (gxe/put* (xutil/create-record "document" id attrs attr-keys)))

(defn create [node attrs]
  (let [[_ {:document/keys [id]} :as put] (create* attrs)
        tx-status (gxe/submit! node [put])]
    {:success tx-status
     :id      id}))

;; Queries ------------------------------------------------------------------------
(defn get
  [node id]
  (xt->pathom (gxe/find-entity node {:document/id id})))

;; Mutations ----------------------------------------------------------------------
(defn merge
  [node eid m]
  (gxe/merge node eid (select-keys m [:document/name])))

(gxe/deftx delete [node eid]
  [(gxe/delete* eid)])

(gxe/deftx add-alignment [node eid user-email sentence-index amap]
  (let [{:document/keys [alignments] :as d} (gxe/entity node eid)
        new-doc (if (contains? alignments user-email)
                  (update-in d [:document/alignments user-email sentence-index] conj amap)
                  (assoc-in d [:document/alignments user-email sentence-index] #{amap}))]
    [(gxe/put* new-doc)]))

(gxe/deftx delete-alignment [node eid user-email sentence-index amap]
  (let [{:document/keys [alignments] :as d} (gxe/entity node eid)
        new-doc (if (contains? alignments user-email)
                  (update-in d [:document/alignments user-email sentence-index] disj amap)
                  #{})]
    [(gxe/put* new-doc)]))