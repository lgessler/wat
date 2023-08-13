(ns wat.models.document
  (:require [clojure.set :refer [rename-keys]]
            [com.wsscode.pathom.connect :as pc]
            [com.fulcrologic.fulcro.algorithms.form-state :as fs]
            [taoensso.timbre :as log]
            #?(:clj [wat.xtdb.project :as prj])
            #?(:clj [wat.xtdb.document :as doc])
            #?(:clj [wat.models.auth :as ma])
            #?(:clj [wat.xtdb.easy :as gxe])
            #?(:clj [wat.models.common :as mc :refer [server-error server-message]])))

(def document-keys [:document/name :document/project])

(defn valid-name [name] (and (string? name) (<= 1 (count name) 80)))
(defn- field-valid [field v]
  (case field
    :document/name (valid-name v)))

(defn document-valid [form field]
  (let [v (get form field)]
    (field-valid field v)))

(defn record-valid? [record]
  (every? (fn [[k v]]
            (field-valid k v)) (log/spy record)))

(def validator (fs/make-validator document-valid))

;; user --------------------------------------------------------------------------------
#?(:clj
   (pc/defresolver get-document [{:keys [node]} {:document/keys [id]}]
     {::pc/input     #{:document/id}
      ::pc/output    [:document/id :document/name]
      ::pc/transform (ma/readable-required :document/id)}
     (doc/get node id)))

#?(:clj
   (pc/defmutation create-document [{:keys [node]} {delta :delta [_ temp-id] :ident [_ parent-id] :parent-ident :as params}]
     {::pc/transform (ma/writeable-required :project/id :parent-id)
      ::pc/output    [:server/error? :server/message]}
     (let [new-document (-> {}
                            (mc/apply-delta delta)
                            (select-keys [:document/name])
                            (assoc :document/project parent-id))]
       (let [{:keys [id success]} (doc/create node new-document)]
         (if-not success
           (server-error (str "Failed to create document, please refresh and try again"))
           {:tempids {temp-id id}})))))

#?(:clj
   (pc/defmutation save-document [{:keys [node]} {delta :delta [_ id] :ident :as params}]
     {::pc/transform (ma/writeable-required :document/id (comp second :ident))}
     (let [valid? (mc/validate-delta record-valid? delta)]
       (cond
         (nil? (gxe/entity node id))
         (server-error (str "Document doesn't exist with ID: " id))

         (not valid?)
         (server-error (str "Document is not valid, refusing to save: " delta))

         :else
         (if (doc/merge node id (mc/apply-delta {} delta))
           (server-message "Document saved")
           (server-error "Document failed to save"))))))

#?(:clj
   (pc/defmutation delete-document [{:keys [node]} {[_ id] :ident :as params}]
     {::pc/transform (ma/writeable-required :document/id (comp second :ident))}
     (let [{:document/keys [name] :as record} (gxe/entity node id)]
       (cond
         (nil? record)
         (server-error (str "Document not found with ID: " id))

         :else
         (if-not (doc/delete node id)
           (server-error (str "Failed to delete document " name ". Please refresh and try again"))
           (server-message (str "Document " name " deleted")))))))

;; admin --------------------------------------------------------------------------------

#?(:clj
   (def document-resolvers [get-document create-document save-document delete-document]))

