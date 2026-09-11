(ns sepal.app.cli.load-import
  "Load a converted Bauble backup into this garden.

  The converter writes one JSON file per Sepal table and stops there. This
  reads them and writes every record through its component interface, so an
  imported row passes the same validation an interactive write does.

  The whole load is one transaction. Failures are collected rather than thrown
  -- the operator needs the list, not the first one -- and the transaction
  rolls back if any record failed or if --dry-run was passed, so a dry run is a
  real load that is thrown away."
  (:require [babashka.fs :as fs]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [sepal.accession.interface :as accession.i]
            [sepal.collection.interface :as collection.i]
            [sepal.contact.interface :as contact.i]
            [sepal.database.interface :as db.i]
            [sepal.error.interface :as error.i]
            [sepal.location.interface :as location.i]
            [sepal.material.interface :as material.i]
            [sepal.note.interface :as note.i]
            [sepal.settings.interface :as settings.i]
            [sepal.settings.interface.activity :as settings.activity]
            [sepal.synonym.interface :as synonym.i]
            [sepal.tag.interface :as tag.i]
            [sepal.taxon.interface :as taxon.i]
            [sepal.user.interface :as user.i]))

;;; ---------------------------------------------------------------------------
;;; Reading
;;; ---------------------------------------------------------------------------

(def tables
  "Every file the converter can write. The load order is `passes`."
  ["taxon_create" "location" "contact" "tag" "settings" "accession" "material"
   "collection" "material_change" "note" "tag_link" "taxon_vernacular"
   "taxon_synonym" "taxon_distribution"])

(defn- kebab-key
  "`accession_bauble_id` -> `:accession-bauble-id`. The converter writes
  snake_case; Sepal's specs are kebab-case keywords."
  [k]
  (keyword (str/replace k "_" "-")))

(defn read-table
  "Rows of one converter file, or `[]` when it is absent.

  A file with no rows is omitted by the converter, and that is an empty table
  rather than an error. Malformed JSON is not: it throws."
  [dir table]
  (let [path (fs/path dir (str table ".json"))]
    (if-not (fs/exists? path)
      []
      (with-open [r (io/reader (fs/file path))]
        (json/read r :key-fn kebab-key)))))

(defn read-all [dir]
  (into {} (map (juxt identity #(read-table dir %))) tables))

;;; ---------------------------------------------------------------------------
;;; State
;;; ---------------------------------------------------------------------------
;;;
;;; :ids      {table {bauble-id sepal-id}} -- every key an opaque string. Three
;;;           files use non-numeric ids (note, taxon_synonym, settings) and two
;;;           carry none at all, so nothing here parses a key.
;;; :failures records refused by a spec or by an unresolvable reference.
;;; :skipped  records the converter already knew it could not place.
;;; :counts   what landed, per table.
;;; :warnings emitted once each, not per row.

(defn initial-state []
  {:ids {} :failures [] :skipped {} :duplicates {} :counts {} :warnings #{}})

(defn- record-id [state table bauble-id sepal-id]
  (assoc-in state [:ids table (str bauble-id)] sepal-id))

(defn- fail [state table bauble-id message]
  (update state :failures conj {:table table
                                :bauble-id bauble-id
                                :message message}))

(defn- skip [state table]
  (update-in state [:skipped table] (fnil inc 0)))

(defn- counted [state table]
  (update-in state [:counts table] (fnil inc 0)))

(defn- warn-once [state message]
  (update state :warnings conj message))

;;; ---------------------------------------------------------------------------
;;; Reference resolution
;;; ---------------------------------------------------------------------------

(defn resolve-taxon-ref
  "A `{:kind :value}` reference to a Sepal taxon id.

  Returns `{:id n}`, `{:error msg}`, or `{:warn msg :id n}`. The converter
  writes a reference rather than a number because `taxon.id` is autoincrement
  and means nothing in a rebuilt database."
  [db ids {:keys [kind value]}]
  (case kind
    ;; The loud case. A garden built from a different WFO release than the
    ;; review file was made against resolves to nothing, or to two taxa --
    ;; wfo_taxon_id is indexed but not unique. A wrong taxon is the one error
    ;; nobody would notice.
    "wfo"
    (let [matches (taxon.i/list-by-wfo-taxon-id db value)]
      (case (count matches)
        1 {:id (:taxon/id (first matches))}
        0 {:error (str "no taxon carries wfo_taxon_id " value
                       " -- this garden's taxonomy is not the one the review"
                       " file was made against")}
        {:error (str (count matches) " taxa carry wfo_taxon_id " value
                     " -- cannot tell which one is meant")}))

    "created"
    (if-let [id (get-in ids ["taxon_create" (str value)])]
      {:id id}
      {:error (str "no taxon was created for Bauble species " value)})

    ;; A garden's own taxon legitimately has no WFO id, so this is accepted --
    ;; but it is only valid against the database `analyze` ran on.
    "local"
    {:id value
     :warn (str "a local taxon reference was used; it is only valid against"
                " the database the review file was made against")}

    {:error (str "unknown taxon reference kind " (pr-str kind))}))

(def ^:private parent-key
  {"accession" [:accession-bauble-id "accession"]
   "material" [:material-bauble-id "material"]})

(defn resolve-parent
  "The Sepal id a polymorphic row hangs on.

  `note` and `tag_link` carry `resource_type` plus one of three keys, so the
  key to read is chosen by the row rather than fixed per file."
  [db ids {:keys [resource-type] :as record}]
  (if (= "taxon" resource-type)
    (if-let [ref (:taxon-ref record)]
      (resolve-taxon-ref db ids ref)
      {:error "resource_type is taxon but the row carries no taxon_ref"})
    (if-let [[k table] (parent-key resource-type)]
      (if-let [bauble-id (get record k)]
        (if-let [id (get-in ids [table (str bauble-id)])]
          {:id id}
          {:error (str table " " bauble-id " was not loaded")})
        {:error (str "resource_type is " resource-type
                     " but the row carries no " (name k))})
      {:error (str "unknown resource_type " (pr-str resource-type))})))

(defn- resolve-bauble-ref
  "A `*_bauble_id` on a record to the Sepal id it became."
  [ids table bauble-id]
  (when (some? bauble-id)
    (get-in ids [table (str bauble-id)])))

;;; ---------------------------------------------------------------------------
;;; Passes
;;; ---------------------------------------------------------------------------
;;;
;;; Order is fixed by the references. Everything inside a pass is independent
;;; of everything else in it.

(def ^:private converter-keys
  "Keys the converter adds that no Sepal spec accepts. The create specs are
  {:closed true}, so a stray one is a validation failure rather than a
  silently ignored field."
  #{:bauble-id :_losses :resource-type :taxon-ref})

(defn- payload
  "A record with the converter's own keys and any resolved references removed."
  [record & extra]
  (apply dissoc record (concat converter-keys extra)))

(defn- write!
  "Call `f` on the payload, record the outcome, and keep going.

  `create!` returns the entity or an error map, and throws on a spec it cannot
  coerce. Both become a collected failure -- the operator needs the whole list,
  and the transaction rolls back regardless."
  [state table record f]
  (try
    (let [result (f)]
      (if (error.i/error? result)
        (fail state table (:bauble-id record) (str (error.i/message result)))
        ;; Most interfaces return the entity. `tag.i/tag!` returns a boolean:
        ;; false means the link already existed, which happens when two Bauble
        ;; species resolve to one Sepal taxon and their tags collapse. That is
        ;; a duplicate, not a load, and counting it as one made the report
        ;; claim a row the unique index had refused.
        (if (false? result)
          (update-in state [:duplicates table] (fnil inc 0))
          (let [id (when (map? result)
                     (some result [:id :taxon/id :accession/id :material/id
                                   :location/id :contact/id :collection/id
                                   :note/id :tag/id :synonym/id]))]
            (cond-> (counted state table)
              id (record-id table (:bauble-id record) id))))))
    (catch Exception ex
      (fail state table (:bauble-id record) (ex-message ex)))))

(defn- resolved
  "Thread a reference resolution into the state, or record why it failed.

  `f` receives the resolved id and returns the new state."
  [state table record resolution f]
  (cond
    (:error resolution) (fail state table (:bauble-id record)
                              (:error resolution))
    (:warn resolution) (f (warn-once state (:warn resolution))
                          (:id resolution))
    :else (f state (:id resolution))))

(defn- pass-taxon-create
  "34 new taxa. A cultivar's parent may be created in the same file, so this
  resolves within itself: parents first, then the rest."
  [db state records]
  (let [by-id (into {} (map (juxt (comp str :bauble-id) identity)) records)
        parents (set (keep (comp str :parent-id) records))
        ordered (concat (filter #(parents (str (:bauble-id %))) records)
                        (remove #(parents (str (:bauble-id %))) records))]
    (reduce
      (fn [st record]
        (let [parent-bauble (:parent-id record)
              parent-id (when parent-bauble
                          (get-in st [:ids "taxon_create" (str parent-bauble)]))]
          (if (and parent-bauble (nil? parent-id) (by-id (str parent-bauble)))
            (fail st "taxon_create" (:bauble-id record)
                  (str "parent " parent-bauble " was not created first"))
            (write! st "taxon_create" record
                    #(taxon.i/create! db (cond-> (payload record :parent-id :note)
                                           parent-id (assoc :parent-id parent-id)))))))
      state ordered)))

(defn- pass-simple
  "A table with no references: location, contact, tag."
  [db state table records f]
  (reduce (fn [st record] (write! st table record #(f db (payload record))))
          state records))

(defn- pass-settings
  "Four keys, written as one map rather than a row at a time."
  [db state records]
  (if (empty? records)
    state
    (do (settings.i/set-values! db (into {} (map (juxt :key :value)) records))
        (update-in state [:counts "settings"] (fnil + 0) (count records)))))

(defn- pass-accession [db state records]
  (reduce
    (fn [st record]
      (resolved st "accession" record
                (resolve-taxon-ref db (:ids st) (:taxon-ref record))
                (fn [st taxon-id]
                  (write! st "accession" record
                          #(accession.i/create!
                             db (-> (payload record :supplier-contact-bauble-id
                                             :intended-location-bauble-id
                                             :created-at :updated-at)
                                    (assoc :taxon-id taxon-id)
                                    (cond->
                                      (:supplier-contact-bauble-id record)
                                      (assoc :supplier-contact-id
                                             (resolve-bauble-ref (:ids st) "contact"
                                                                 (:supplier-contact-bauble-id record)))
                                      (:intended-location-bauble-id record)
                                      (assoc :intended-location-id
                                             (resolve-bauble-ref (:ids st) "location"
                                                                 (:intended-location-bauble-id record))))))))))
    state records))

(defn- pass-material [db state records]
  (reduce
    (fn [st record]
      (let [accession-id (resolve-bauble-ref (:ids st) "accession"
                                             (:accession-bauble-id record))
            location-id (resolve-bauble-ref (:ids st) "location"
                                            (:location-bauble-id record))]
        (if (nil? accession-id)
          (fail st "material" (:bauble-id record)
                (str "accession " (:accession-bauble-id record)
                     " was not loaded"))
          (write! st "material" record
                  #(material.i/create!
                     db (-> (payload record :accession-bauble-id
                                     :location-bauble-id :created-at :updated-at)
                            (assoc :accession-id accession-id)
                            (cond-> location-id (assoc :location-id location-id))))))))
    state records))

(defn- pass-collection [db state records]
  (reduce
    (fn [st record]
      (let [accession-id (resolve-bauble-ref (:ids st) "accession"
                                             (:accession-bauble-id record))]
        (if (nil? accession-id)
          (fail st "collection" (:bauble-id record)
                (str "accession " (:accession-bauble-id record)
                     " was not loaded"))
          (write! st "collection" record
                  #(collection.i/create!
                     db (-> (payload record :accession-bauble-id
                                     :created-at :updated-at)
                            (assoc :accession-id accession-id)))))))
    state records))

(defn- pass-material-change
  "created_by loads null: it is nullable, and the history import backfills it
  from Bauble's log, which is the only record of who logged each change."
  [db state records]
  (reduce
    (fn [st record]
      (let [material-id (resolve-bauble-ref (:ids st) "material"
                                            (:material-bauble-id record))]
        (if (nil? material-id)
          (fail st "material_change" (:bauble-id record)
                (str "material " (:material-bauble-id record)
                     " was not loaded"))
          (write! st "material_change" record
                  #(material.i/create-change!
                     db (-> (payload record :material-bauble-id
                                     :from-location-bauble-id
                                     :to-location-bauble-id)
                            (assoc :material-id material-id)
                            (cond->
                              (:from-location-bauble-id record)
                              (assoc :from-location-id
                                     (resolve-bauble-ref (:ids st) "location"
                                                         (:from-location-bauble-id record)))
                              (:to-location-bauble-id record)
                              (assoc :to-location-id
                                     (resolve-bauble-ref (:ids st) "location"
                                                         (:to-location-bauble-id record))))))))))
    state records))

(defn- pass-note [db state records]
  (reduce
    (fn [st record]
      (resolved st "note" record (resolve-parent db (:ids st) record)
                (fn [st parent-id]
                  (write! st "note" record
                          #(note.i/create!
                             db (-> (payload record :accession-bauble-id
                                             :material-bauble-id
                                             :created-at :updated-at)
                                    (assoc :resource-type (keyword (:resource-type record))
                                           :resource-id parent-id)))))))
    state records))

(defn- pass-tag-link
  "tag.i/tag! takes positional arguments and returns the link, so this does not
  go through `payload`."
  [db state records]
  (reduce
    (fn [st record]
      (let [tag-id (resolve-bauble-ref (:ids st) "tag" (:tag-bauble-id record))]
        (if (nil? tag-id)
          (fail st "tag_link" (:bauble-id record)
                (str "tag " (:tag-bauble-id record) " was not loaded"))
          (resolved st "tag_link" record (resolve-parent db (:ids st) record)
                    (fn [st parent-id]
                      (write! st "tag_link" record
                              #(tag.i/tag! db tag-id parent-id
                                           (keyword (:resource-type record)))))))))
    state records))

(defn- pass-taxon-update
  "taxon_vernacular and taxon_distribution are updates onto taxa that already
  exist, including WFO taxa this import did not create."
  [db state table records field]
  (reduce
    (fn [st record]
      (resolved st table record
                (resolve-taxon-ref db (:ids st) (:taxon-ref record))
                (fn [st taxon-id]
                  (try
                    (taxon.i/update! db taxon-id {field (get record field)})
                    (counted st table)
                    (catch Exception ex
                      (fail st table taxon-id (ex-message ex)))))))
    state records))

(defn- pass-taxon-synonym [db state records]
  (reduce
    (fn [st record]
      ;; Three of BBG's 143 rows carry _losses and no taxon_ref: the converter
      ;; already knew it could not resolve them. A missing reference is a skip;
      ;; one that is present and unresolvable is a failure.
      (if-not (:taxon-ref record)
        (skip st "taxon_synonym")
        (resolved st "taxon_synonym" record
                  (resolve-taxon-ref db (:ids st) (:taxon-ref record))
                  (fn [st taxon-id]
                    (write! st "taxon_synonym" record
                            #(synonym.i/add-synonym!
                               db (-> (payload record)
                                      (assoc :taxon-id taxon-id))))))))
    state records))

;;; ---------------------------------------------------------------------------
;;; created_at
;;; ---------------------------------------------------------------------------

(def ^:private timestamped
  "Tables whose rows carry Bauble's `_created`, and the column to restore it
  to. The rest either have no timestamp in the source or are not rows of their
  own."
  {"taxon_create" :taxon
   "location" :location
   "contact" :contact
   "tag" :tag
   "accession" :accession
   "material" :material
   "collection" :collection
   "note" :note})

(defn- restore-created-at!
  "Put Bauble's `_created` back on the rows this run wrote.

  The one place this writes SQL rather than going through an interface: the
  create specs are {:closed true} and none accepts a timestamp, and widening
  eight of them for a field only an importer sets is the wrong trade.

  `updated_at` cannot be restored. The eleven trigger_*_updated_at triggers
  have no WHEN clause, so any update sets it to now -- including this one."
  [db state records-by-table]
  (doseq [[table sql-table] timestamped
          record (get records-by-table table)
          :let [created-at (:created-at record)
                sepal-id (get-in state [:ids table (str (:bauble-id record))])]
          :when (and created-at sepal-id)]
    (db.i/execute-one! db {:update sql-table
                           :set {:created_at created-at}
                           :where [:= :id sepal-id]}))
  state)

;;; ---------------------------------------------------------------------------
;;; The load
;;; ---------------------------------------------------------------------------

(defn load-records
  "Every pass, in order, against an open transaction. Returns the final state."
  [db records]
  (let [t (fn [name] (get records name []))]
    (-> (initial-state)
        (as-> st (pass-taxon-create db st (t "taxon_create")))
        (as-> st (pass-simple db st "location" (t "location") location.i/create!))
        (as-> st (pass-simple db st "contact" (t "contact") contact.i/create!))
        (as-> st (pass-simple db st "tag" (t "tag") tag.i/create!))
        (as-> st (pass-settings db st (t "settings")))
        (as-> st (pass-accession db st (t "accession")))
        (as-> st (pass-material db st (t "material")))
        (as-> st (pass-collection db st (t "collection")))
        (as-> st (pass-material-change db st (t "material_change")))
        (as-> st (pass-note db st (t "note")))
        (as-> st (pass-tag-link db st (t "tag_link")))
        (as-> st (pass-taxon-update db st "taxon_vernacular"
                                    (t "taxon_vernacular") :vernacular-names))
        (as-> st (pass-taxon-synonym db st (t "taxon_synonym")))
        (as-> st (pass-taxon-update db st "taxon_distribution"
                                    (t "taxon_distribution") :distribution))
        (as-> st (restore-created-at! db st records)))))

(defn- report [state {:keys [dry-run]}]
  (let [{:keys [counts skipped failures warnings]} state]
    (doseq [w (sort warnings)]
      (println "warning:" w))
    (doseq [[table n] (sort counts)]
      (println (format "  %-22s %6d" table n)))
    (doseq [[table n] (sort skipped)]
      (println (format "  %-22s %6d skipped -- no reference to resolve"
                       table n)))
    (doseq [[table n] (sort (:duplicates state))]
      (println (format "  %-22s %6d already linked -- two Bauble rows resolved"
                       table n)
               "to one Sepal record"))
    (when (seq failures)
      (println)
      (println (format "%d records failed:" (count failures)))
      (doseq [{:keys [table bauble-id message]} (take 50 failures)]
        (println (format "  %s %s: %s" table bauble-id message)))
      (when (> (count failures) 50)
        (println (format "  ... and %d more" (- (count failures) 50)))))
    (println)
    (println (cond
               (seq failures) "Rolled back: nothing was written."
               dry-run "Dry run: rolled back, nothing was written."
               :else "Committed."))))

(defn- write-loaded-map! [dir state]
  (let [path (str (fs/path dir "loaded.json"))]
    (with-open [w (io/writer path)]
      (json/write (:ids state) w))
    (println "Wrote" path)))

(defn- garden-has-accessions? [db]
  (pos? (or (:count (db.i/execute-one! db {:select [[[:count :*] :count]]
                                           :from [:accession]}))
            0)))

(defn load-import!
  "Load `dir` into `db`. Returns an exit code.

  Everything happens inside one transaction: a dry run and a failed run both
  roll it back, so a dry run is a real load that is thrown away rather than a
  validation pass that cannot prove an insert succeeds."
  [db {:keys [dir actor dry-run allow-nonempty] :as opts}]
  (cond
    (not (fs/directory? dir))
    (do (println (format "Error: %s is not a directory" dir)) 1)

    (nil? (user.i/get-by-email db actor))
    (do (println (format "Error: no user with email '%s'. The completion event
needs an actor and this command will not create one." actor))
        1)

    (and (garden-has-accessions? db) (not allow-nonempty))
    (do (println "Error: this garden already holds accessions. Rebuild it, or
pass --allow-nonempty. Loading twice would duplicate every row: Bauble's ids
are only unique within Bauble, so nothing here can be idempotent.")
        1)

    :else
    (let [records (read-all dir)
          viewer (user.i/get-by-email db actor)
          state (atom nil)]
      (try
        ;; One rollback path for both cases: a dry run and a failed run are
        ;; the same thing -- a load that happened and was thrown away.
        (db.i/with-transaction [tx db]
          (let [st (load-records tx records)]
            (reset! state st)
            (when (or dry-run (seq (:failures st)))
              (throw (ex-info "rolled back" {::rollback true})))
            (settings.activity/create! tx settings.activity/import-completed
                                       (:user/id viewer)
                                       {:counts (:counts st)})))
        (catch clojure.lang.ExceptionInfo ex
          (when-not (::rollback (ex-data ex))
            (throw ex))))
      (report @state opts)
      (if (seq (:failures @state))
        1
        (do (when-not dry-run (write-loaded-map! dir @state))
            0)))))
