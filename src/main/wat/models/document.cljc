(ns wat.models.document
  (:require [clojure.set :refer [rename-keys]]
            [clojure.string :as cstr]
            [com.wsscode.pathom.connect :as pc]
            [com.fulcrologic.fulcro.algorithms.form-state :as fs]
            [taoensso.timbre :as log]
            #?(:clj [wat.xtdb.project :as prj])
            #?(:clj [wat.xtdb.document :as doc])
            #?(:clj [wat.models.auth :as ma])
            #?(:clj [wat.xtdb.easy :as gxe])
            #?(:clj [wat.models.common :as mc :refer [server-error server-message]])))

(def document-keys
  [:document/name
   :document/project
   :document/combined-target-sentences
   :document/combined-transl-sentences])

(defn postprocess-sentences [v]
  (if (= "" (cstr/trim v))
    []
    (->> v
         cstr/trim
         cstr/split-lines
         (mapv #(cstr/split % #"\s+")))))

(defn postprocess-glosses [v]
  (if (= "" (cstr/trim v))
    []
    (mapv postprocess-sentences
          (-> v
              cstr/trim
              (cstr/split #"\n\n|\r\n\r\n")))))

(defn combine-glosses [{:document/keys [target-sentences transl-sentences target-glosses transl-glosses] :as doc}]
  (let [combine-fn (fn [acc x]
                     (mapv (comp
                             vec
                             (partial map vec)
                             (partial map flatten)
                             (partial partition 2)
                             interleave)
                           acc x))
        target-sentences (postprocess-sentences target-sentences)
        transl-sentences (postprocess-sentences transl-sentences)
        target-glosses (postprocess-glosses target-glosses)
        transl-glosses (postprocess-glosses transl-glosses)
        combined-target-sentences (if (= 0 (count target-glosses))
                                    (map #(map vector %) target-sentences)
                                    (reduce combine-fn target-sentences target-glosses))
        combined-transl-sentences (if (= 0 (count transl-glosses))
                                    (map #(map vector %) transl-sentences)
                                    (reduce combine-fn transl-sentences transl-glosses))]
    (-> doc
        (dissoc :document/target-sentences)
        (dissoc :document/transl-sentences)
        (dissoc :document/target-glosses)
        (dissoc :document/transl-glosses)
        (assoc :document/combined-target-sentences combined-target-sentences)
        (assoc :document/combined-transl-sentences combined-transl-sentences))))

(defn valid-sentences-and-glosses
  "Checks whether the textual info associated is valid. Conceptually, note that we are
  assuming that :document/source-sentences and :document/target-sentences contain
  tokenized target and translation text. Additionally, each item in
  :document/target-glosses and :document/transl-glosses represents a sentence- and
  word-aligned set of additional information for each token, e.g. interlinear glosses."
  [{:document/keys [target-sentences transl-sentences target-glosses transl-glosses]}]
  (let [target-sentences (postprocess-sentences target-sentences)
        transl-sentences (postprocess-sentences transl-sentences)
        target-glosses (postprocess-glosses target-glosses)
        transl-glosses (postprocess-glosses transl-glosses)
        num-tar-sents (count target-sentences)
        num-tra-sents (count transl-sentences)
        num-tar-gloss-sents (map count target-glosses)
        num-tra-gloss-sents (map count transl-glosses)
        tar-token-counts (map count target-sentences)
        tra-token-counts (map count transl-sentences)
        tar-gloss-item-counts (map #(map count %) target-glosses)
        tra-gloss-item-counts (map #(map count %) transl-glosses)]

    (and
      ;; Must be at least one sentence
      (> num-tar-sents 0)
      (> num-tra-sents 0)

      ;; Target and translation sentence counts must match
      (= num-tar-sents num-tra-sents)

      ;; No sentence may be empty
      (log/spy (every? #(> (count %) 0) target-sentences))
      (every? #(> (count %) 0) transl-sentences)

      ;; All sentences must be strings
      (every? (fn [sentence] (every? #(string? %) sentence)) target-sentences)
      (every? (fn [sentence] (every? #(string? %) sentence)) transl-sentences)

      ;; All glosses must be strings
      (every? (fn [gloss-set]
                (every? (fn [sentence]
                          (every? #(string? %) sentence)) gloss-set))
              target-glosses)
      (every? (fn [gloss-set]
                (every? (fn [sentence]
                          (every? #(string? %) sentence)) gloss-set)) transl-glosses)

      ;; Number of sentences in the glosses must match number of sentences in the text
      (every? #(= % num-tar-sents) num-tar-gloss-sents)
      (every? #(= % num-tra-sents) num-tra-gloss-sents)

      ;; For each gloss set, number of tokens in each sentence must match number of tokens
      ;; in corresponding sentence
      (every? (fn [gloss-set]
                (let [counts (partition 2 (interleave gloss-set tar-token-counts))]
                  (every? (fn [[c1 c2]] (= c1 c2)) counts)))
              tar-gloss-item-counts)
      (every? (fn [gloss-set]
                (let [counts (partition 2 (interleave gloss-set tra-token-counts))]
                  (every? (fn [[c1 c2]] (= c1 c2)) counts)))
              tra-gloss-item-counts))))

(defn valid-name [name] (and (string? name) (<= 1 (count name) 80)))
(defn- field-valid [record field v]
  (case field
    :document/name (valid-name v)
    :document/target-sentences (valid-sentences-and-glosses record)
    :document/transl-sentences (valid-sentences-and-glosses record)
    :document/target-glosses (valid-sentences-and-glosses record)
    :document/transl-glosses (valid-sentences-and-glosses record)))

(defn document-valid [form field]
  (let [v (get form field)]
    (field-valid form field v)))

(defn record-valid? [record]
  (every? (fn [[k v]]
            (field-valid record k v)) (log/spy record)))

(def validator (fs/make-validator document-valid))

;; user --------------------------------------------------------------------------------
#?(:clj
   (pc/defresolver get-document [{:keys [node]} {:document/keys [id]}]
     {::pc/input     #{:document/id}
      ::pc/output    [:document/id :document/name
                      :document/combined-target-sentences :document/combined-transl-sentences]
      ::pc/transform (ma/readable-required :document/id)}
     (doc/get node id)))

#?(:clj
   (pc/defmutation create-document [{:keys [node]} {delta :delta [_ temp-id] :ident [_ parent-id] :parent-ident :as params}]
     {::pc/transform (ma/writeable-required :project/id :parent-id)
      ::pc/output    [:server/error? :server/message]}
     (let [new-document (-> {}
                            (mc/apply-delta delta)
                            (select-keys [:document/name :document/target-sentences :document/transl-sentences
                                          :document/target-glosses :document/transl-glosses])
                            (assoc :document/project parent-id))]
       (cond
         (not (every? #(contains? new-document %) #{:document/target-sentences :document/transl-sentences
                                                    :document/target-glosses :document/transl-glosses :document/name}))
         (server-error (str "Document is missing keys."))

         (not (mc/validate-delta record-valid? delta))
         (server-error (str "Not valid."))

         :else
         (let [combined-new-document (combine-glosses new-document)
               {:keys [id success]} (doc/create node combined-new-document)]
           (if-not success
             (server-error (str "Failed to create document, please refresh and try again"))
             {:tempids {temp-id id}}))))))

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

