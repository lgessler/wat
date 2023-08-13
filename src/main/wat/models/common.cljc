(ns wat.models.common
  (:require [com.wsscode.pathom.connect :as pc]
            [com.wsscode.pathom.parser :as pp]
            [taoensso.timbre :as log]))

(defn server-message [msg]
  {:server/message msg
   :server/error?  false})

(defn server-error [msg]
  {:server/message msg
   :server/error?  true})

(defn apply-delta [entity delta]
  (let [new-vals (into {} (for [[k {:keys [after]}] delta]
                            [k after]))
        new-entity (merge entity new-vals)]
    new-entity))

(defn validate-delta [record-valid-fn delta]
  (let [new-vals (into {} (for [[k {:keys [after]}] delta]
                            [k after]))]
    (println new-vals)
    (record-valid-fn new-vals)))

(defn try-get-document-ident
  "Use within a Pathom resolver to attempt to extract a document ID from the query.
  Used to facilitate implementation of all document-level resolvers. Assumes that
  the query will have a join on the document ident in the root of the first item in
  the transaction, something like `[{[:document/id :doc1] ...}]`.

  Maybe this should have been expressed on the ::pc/input of every resolver that needs
  it somehow, but this is a more ergonomic solution, and if it's OK for a resolver to
  fail for pretty much any reason, perhaps a query not looking right ought to be one."
  [env]
  (let [ident (->> env
                   :com.wsscode.pathom.core/root-query
                   first
                   keys
                   (filter #(and (= 2 (count %))
                                 (vector? %)
                                 (= :document/id (first %))))
                   first)]
    (if (nil? ident)
      (log/warn (str "try-get-document-ident couldn't find a document ident! This isn't necessarily a problem unless "
                     "you were trying to query for document-level structures and not just layer information. ")
                "query: " (:com.wsscode.pathom.core/root-query env))
      ident)))
