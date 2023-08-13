(ns wat.xtdb.project
  (:require [xtdb.api :as xt]
            [wat.xtdb.util :as xutil]
            [wat.xtdb.easy :as gxe]
            [wat.xtdb.access :as gca]
            [wat.xtdb.document :as doc])
  (:refer-clojure :exclude [get]))

(def attr-keys [:project/id
                :project/name
                :project/readers
                :project/writers
                :project/config])

(defn xt->pathom [doc]
  (when doc
    (-> doc
        (update :project/readers xutil/identize :user/id)
        (update :project/writers xutil/identize :user/id))))

(def base-config {})

(defn create [node {:project/keys [id] :as attrs}]
  (let [{:project/keys [id] :as record}
        (merge (xutil/new-record "project" id)
               {:project/readers [] :project/writers [] :project/config base-config}
               (select-keys attrs attr-keys))]
    {:success (gxe/put node record)
     :id      id}))

;; Queries --------------------------------------------------------------------------------
(defn get-document-ids [node id]
  (map first (xt/q (xt/db node)
                   '{:find  [?doc]
                     :where [[?doc :document/project ?prj]]
                     :in    [?prj]}
                   id)))

(defn get
  [node id]
  (xt->pathom (gxe/find-entity node {:project/id id})))

(defn reader-ids
  [node id]
  (:project/readers (gxe/entity node id)))

(defn writer-ids
  [node id]
  (:project/writers (gxe/entity node id)))

(defn get-all
  [node]
  (map xt->pathom (gxe/find-entities node {:project/id '_})))

(defn get-by-name
  [node name]
  (gxe/find-entity node {:project/name name}))

(defn get-accessible-ids [node user-id]
  (gca/get-accessible-ids node user-id :project/id))

(defn get-accessible-projects
  "Return a seq of full projects accessible for a user"
  [node user-id]
  (->> (get-accessible-ids node user-id)
       (map vector)
       (gxe/entities node)
       (map xt->pathom)))

;; Mutations --------------------------------------------------------------------------------
(gxe/deftx delete [node eid]
  (let [;; note: do NOT use doc/delete since the layer deletions will take care of annos
        documents (get-document-ids node eid)
        doc-txs (map #(gxe/delete* %) documents)
        project-txs [(gxe/delete* eid)]
        all-txs (reduce into [doc-txs project-txs])]
    all-txs))

(gxe/deftx add-text-layer [node project-id text-layer-id]
  (xutil/add-join** node project-id :project/text-layers text-layer-id))

(gxe/deftx remove-text-layer [node project-id text-layer-id]
  (xutil/remove-join** node project-id :project/text-layers text-layer-id))

(gxe/deftx add-reader [node project-id user-id]
  (xutil/add-join** node project-id :project/readers user-id))

(gxe/deftx remove-reader [node project-id user-id]
  (xutil/remove-from-multi-joins** node project-id [:project/readers :project/writers] user-id))

(gxe/deftx add-writer [node project-id user-id]
  (xutil/add-to-multi-joins** node project-id [:project/readers :project/writers] user-id))

(gxe/deftx remove-writer [node project-id user-id]
  (xutil/remove-join** node project-id :project/writers user-id))
