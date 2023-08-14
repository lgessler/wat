(ns wat.xtdb.document
  (:require [xtdb.api :as xt]
            [wat.xtdb.util :as xutil]
            [wat.xtdb.easy :as gxe])
  (:refer-clojure :exclude [get merge]))

(def attr-keys [:document/id
                :document/name
                :document/project
                :document/combined-target-sentences
                :document/combined-transl-sentences])

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
  (gxe/delete* eid))

