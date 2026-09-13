(ns sepal.app.cli.load-import
  "Load a directory of converted records into this garden.

  The input is one JSON file per table, each a list of records in the import
  envelope:

      {\"id\": \"271\",
       \"created_at\": \"2006-10-11 09:33:25\",
       \"refs\": {\"accession\": {\"table\": \"accession\", \"id\": \"272\"}},
       \"data\": {\"code\": \"1\", \"quantity\": 1}}

  `data` goes to the component's `create!` untouched, so every imported row
  passes the same validation an interactive write does, and nothing here knows
  a field name of whatever system produced the file. A reference resolves to
  the field named after it -- `accession` to `accession-id` -- which is the
  only rule any of the passes below needs.

  The whole load is one transaction. Failures are collected rather than thrown
  -- the operator needs the list, not the first one -- and the transaction
  rolls back if any record failed or if --dry-run was passed, so a dry run is a
  real load that is thrown away."
  (:require [babashka.fs :as fs]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [malli.core :as m]
            [sepal.accession.interface :as accession.i]
            [sepal.accession.interface.spec :as accession.spec]
            [sepal.collection.interface :as collection.i]
            [sepal.collection.interface.spec :as collection.spec]
            [sepal.contact.interface :as contact.i]
            [sepal.contact.interface.spec :as contact.spec]
            [sepal.database.interface :as db.i]
            [sepal.error.interface :as error.i]
            [sepal.location.interface :as location.i]
            [sepal.location.interface.spec :as location.spec]
            [sepal.material.interface :as material.i]
            [sepal.material.interface.spec :as material.spec]
            [sepal.note.interface :as note.i]
            [sepal.note.interface.spec :as note.spec]
            [sepal.settings.interface :as settings.i]
            [sepal.settings.interface.activity :as settings.activity]
            [sepal.synonym.interface :as synonym.i]
            [sepal.synonym.interface.spec :as synonym.spec]
            [sepal.tag.interface :as tag.i]
            [sepal.tag.interface.spec :as tag.spec]
            [sepal.taxon.interface :as taxon.i]
            [sepal.taxon.interface.spec :as taxon.spec]
            [sepal.user.interface :as user.i]))

;;; ---------------------------------------------------------------------------
;;; Reading
;;; ---------------------------------------------------------------------------

(def tables
  "Every file that can be loaded, in the order they are loaded.

  The order is the reference graph: a file may only point at one before it. It
  is the one thing here that the input does not say."
  ["taxon" "location" "contact" "tag" "settings" "accession" "material"
   "collection" "material_change" "note" "tag_link" "taxon_vernacular"
   "taxon_synonym" "taxon_distribution"])

(defn- kebab-key
  "`quantity_received` -> `:quantity-received`. The files are snake_case;
  Sepal's specs are kebab-case keywords."
  [k]
  (keyword (str/replace k "_" "-")))

(defn read-table
  "Records from one file, or `[]` when it is absent.

  A file with no records is omitted by the converter, and that is an empty
  table rather than an error. Malformed JSON is not: it throws."
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
;;; :ids      {table {source-id sepal-id}} -- every key an opaque string. Some
;;;           files use non-numeric ids and two carry none at all, so nothing
;;;           here parses a key.
;;; :failures records refused by a spec or by an unresolvable reference.
;;; :counts   what landed, per table.
;;; :warnings emitted once each, not per row.

(defn initial-state []
  {:ids {} :failures [] :duplicates {} :counts {} :warnings #{}})

(defn- record-id [state table source-id sepal-id]
  (assoc-in state [:ids table (str source-id)] sepal-id))

(defn- fail [state table source-id message]
  (update state :failures conj {:table table
                                :source-id source-id
                                :message message}))

(defn- counted [state table]
  (update-in state [:counts table] (fnil inc 0)))

(defn- warn-once [state message]
  (update state :warnings conj message))

;;; ---------------------------------------------------------------------------
;;; References
;;; ---------------------------------------------------------------------------

(defn resolve-ref
  "One reference to a Sepal id.

  Returns `{:id n}`, `{:error msg}`, or `{:warn msg :id n}`. Three shapes, none
  of which names a source system:

      {:table \"accession\" :id \"975\"}  a record this import created
      {:wfo \"wfo-0000283538-2025-12\"}   a taxon this garden already holds
      {:sepal-id 1234}                    a row in this garden, by id"
  [db ids {:keys [table id wfo sepal-id] :as ref}]
  (cond
    ;; The loud case. A garden built from a different WFO release than the one
    ;; the input was made against resolves to nothing, or to two taxa --
    ;; wfo_taxon_id is indexed but not unique. A wrong taxon is the one error
    ;; nobody would notice.
    wfo
    (let [matches (taxon.i/list-by-wfo-taxon-id db wfo)]
      (case (count matches)
        1 {:id (:taxon/id (first matches))}
        0 {:error (str "no taxon carries wfo_taxon_id " wfo
                       " -- this garden's taxonomy is not the one this input"
                       " was made against")}
        {:error (str (count matches) " taxa carry wfo_taxon_id " wfo
                     " -- cannot tell which one is meant")}))

    ;; A garden's own record legitimately has no portable id, so this is
    ;; accepted -- but it is only valid against the database the input was made
    ;; against.
    sepal-id
    {:id sepal-id
     :warn (str "a sepal_id reference was used; it is only valid against the"
                " database this input was made against")}

    table
    (if-let [loaded (get-in ids [table (str id)])]
      {:id loaded}
      {:error (str table " " id " was not loaded")})

    :else
    {:error (str "unrecognised reference " (pr-str ref))}))

(defn resolve-refs
  "Every reference on a record, as the fields they resolve to.

  Returns `{:fields {...} :warns [...]}`, or `{:error msg}` for the first
  reference that does not resolve. A ref named `K` becomes the field `K-id`:
  `taxon` becomes `:taxon-id`, `from-location` becomes `:from-location-id`."
  [db ids refs]
  (reduce (fn [acc [ref-name ref]]
            (let [{:keys [id error warn]} (resolve-ref db ids ref)]
              (if error
                (reduced {:error error})
                (cond-> (assoc-in acc
                                  [:fields (keyword (str (name ref-name)
                                                         "-id"))]
                                  id)
                  warn (update :warns conj warn)))))
          {:fields {} :warns []}
          refs))

;;; ---------------------------------------------------------------------------
;;; Writing
;;; ---------------------------------------------------------------------------

(defn- landed-id
  "The id a component's create returned, whatever it chose to call it.

  A create whose id is not in this list records no provenance, so a new one
  has to be added here. `material-change` was missing and its 173 rows went
  unrecorded, which is the half of the import a history load resolves against."
  [result]
  (when (map? result)
    (some result [:id :taxon/id :accession/id :material/id :location/id
                  :contact/id :collection/id :note/id :tag/id :synonym/id
                  :material-change/id :tag-link/id])))

(defn- write!
  "Call `f`, record the outcome, and keep going.

  `create!` returns the entity or an error map, and throws on a payload it
  cannot coerce. Both become a collected failure -- the operator needs the
  whole list, and the transaction rolls back regardless."
  [state table record f]
  (try
    (let [result (f)]
      (cond
        (error.i/error? result)
        (fail state table (:id record) (str (error.i/message result)))

        ;; `tag.i/tag!` returns a boolean: false means the link already
        ;; existed, which happens when two source records resolve to one Sepal
        ;; record and their tags collapse. That is a duplicate, not a load, and
        ;; counting it as one made the report claim a row the unique index had
        ;; refused.
        (false? result)
        (update-in state [:duplicates table] (fnil inc 0))

        :else
        (let [id (landed-id result)]
          (cond-> (counted state table)
            id (record-id table (:id record) id)))))
    (catch Exception ex
      (fail state table (:id record) (ex-message ex)))))

(defn- unknown-keys
  "Payload keys the spec has no entry for.

  `store.i/create!` coerces with `strip-extra-keys-transformer`, so an unknown
  key is removed before `{:closed true}` can refuse it. That is right for a
  form, where a browser submits fields no spec models, and wrong for a file: a
  field this garden has no column for would be dropped without a word. The
  import is the one caller whose input is not a form, so it is the one that
  should be strict."
  [spec payload]
  (when spec
    (seq (sort (remove (set (map first (m/children spec)))
                       (keys payload))))))

(defn- pass
  "Resolve each record's references, merge them into `data`, and write.

  Every table that creates a row of its own goes through here. `f` takes the
  database and one payload, which is `data` plus a field per reference and
  nothing else. `spec` is what that payload must not exceed."
  [db state table records f spec]
  (reduce
    (fn [st {:keys [refs data] :as record}]
      (let [{:keys [fields warns error]} (resolve-refs db (:ids st) refs)
            payload (merge data fields)]
        (cond
          error (fail st table (:id record) error)

          (unknown-keys spec payload)
          (fail st table (:id record)
                (str "no column for " (str/join ", " (map name (unknown-keys
                                                                 spec payload)))
                     " -- this garden cannot hold every field in the input"))

          :else
          (write! (reduce warn-once st warns) table record
                  #(f db payload)))))
    state records))

;;; ---------------------------------------------------------------------------
;;; The three tables that are not a plain create
;;; ---------------------------------------------------------------------------

(defn- pass-settings
  "Written as one map rather than a row at a time."
  [db state records]
  (if (empty? records)
    state
    (do (settings.i/set-values!
          db (into {} (map (juxt (comp :key :data) (comp :value :data)))
                   records))
        (update-in state [:counts "settings"] (fnil + 0) (count records)))))

(defn- pass-tag-link
  "`tag.i/tag!` takes positional arguments rather than a payload."
  [db state records]
  (pass db state "tag_link" records
        (fn [db {:keys [tag-id resource-id resource-type]}]
          (tag.i/tag! db tag-id resource-id (keyword resource-type)))
        tag.spec/CreateTagLink))

(defn- pass-taxon-update
  "taxon_vernacular and taxon_distribution are updates onto taxa that already
  exist, including taxa this import did not create. They carry no id of their
  own, so nothing is recorded for them."
  [db state table records field]
  (reduce
    (fn [st {:keys [refs data] :as record}]
      (let [{:keys [fields warns error]} (resolve-refs db (:ids st) refs)]
        (if error
          (fail st table (:id record) error)
          (let [st (reduce warn-once st warns)]
            (try
              (taxon.i/update! db (:taxon-id fields) {field (get data field)})
              (counted st table)
              (catch Exception ex
                (fail st table (:taxon-id fields) (ex-message ex))))))))
    state records))

;;; ---------------------------------------------------------------------------
;;; created_at
;;; ---------------------------------------------------------------------------

(def ^:private timestamped
  "Tables whose records carry `created_at`, and the table to restore it to."
  {"taxon" :taxon
   "location" :location
   "contact" :contact
   "tag" :tag
   "accession" :accession
   "material" :material
   "collection" :collection
   "note" :note})

(defn- restore-created-at!
  "Put the source's `created_at` back on the rows this run wrote.

  The one place this writes SQL rather than going through an interface: the
  create specs are {:closed true} and none accepts a timestamp, and widening
  eight of them for a field only an importer sets is the wrong trade.

  `updated_at` cannot be restored. The eleven trigger_*_updated_at triggers
  have no WHEN clause, so any update sets it to now -- including this one."
  [db state records-by-table]
  (doseq [[table sql-table] timestamped
          record (get records-by-table table)
          :let [created-at (:created-at record)
                sepal-id (get-in state [:ids table (str (:id record))])]
          :when (and created-at sepal-id)]
    (db.i/execute-one! db {:update sql-table
                           :set {:created_at created-at}
                           :where [:= :id sepal-id]}))
  state)

;;; ---------------------------------------------------------------------------
;;; Where each record came from
;;; ---------------------------------------------------------------------------

(def ^:private imported-resource
  "The Sepal resource a file's records become, for `import_record`.

  Only the files that create a row of their own appear. `settings` has no id of
  its own and the two taxon_* update files carry none.

  `tag_link` is absent for a different reason: `tag.i/tag!` returns a boolean
  rather than the link, so there is no id to record. A tag link is reachable
  from the records it joins, so nothing needs to resolve one by source id."
  {"taxon" "taxon"
   "location" "location"
   "contact" "contact"
   "tag" "tag"
   "accession" "accession"
   "material" "material"
   "collection" "collection"
   "material_change" "material_change"
   "note" "note"
   "taxon_synonym" "synonym"})

(defn- record-provenance!
  "One `import_record` row per loaded record.

  This is what a later import resolves against -- which Sepal row a source row
  became. `loaded.json` carries the same mapping, but a file beside the input
  is not somewhere a second import can rely on finding it.

  The unique index on (source_table, source_id) is what makes loading the same
  input twice a refusal rather than a silent duplicate. That is a failure like
  any other: collected and reported, not thrown out of the run."
  [db state]
  (let [rows (for [[table resource-type] (sort imported-resource)
                   [source-id sepal-id] (sort (get-in state [:ids table]))]
               [table source-id resource-type sepal-id])]
    (if-not (seq rows)
      state
      (try
        (db.i/execute-one! db {:insert-into :import_record
                               :columns [:source_table :source_id
                                         :resource_type :resource_id]
                               :values (vec rows)})
        state
        (catch Exception ex
          (fail state "import_record" nil
                (if (re-find #"(?i)unique" (or (ex-message ex) ""))
                  (str "some of these records have already been imported into"
                       " this garden. Rebuild it and load once, rather than"
                       " loading the same input twice.")
                  (ex-message ex))))))))

;;; ---------------------------------------------------------------------------
;;; The load
;;; ---------------------------------------------------------------------------

(defn load-records
  "Every pass, in order, against an open transaction. Returns the final state."
  [db records]
  (let [t (fn [name] (get records name []))]
    (-> (initial-state)
        (as-> st (pass db st "taxon" (t "taxon") taxon.i/create!
                       taxon.spec/CreateTaxon))
        (as-> st (pass db st "location" (t "location") location.i/create!
                       location.spec/CreateLocation))
        (as-> st (pass db st "contact" (t "contact") contact.i/create!
                       contact.spec/CreateContact))
        (as-> st (pass db st "tag" (t "tag") tag.i/create!
                       tag.spec/CreateTag))
        (as-> st (pass-settings db st (t "settings")))
        (as-> st (pass db st "accession" (t "accession") accession.i/create!
                       accession.spec/CreateAccession))
        (as-> st (pass db st "material" (t "material") material.i/create!
                       material.spec/CreateMaterial))
        (as-> st (pass db st "collection" (t "collection")
                       collection.i/create! collection.spec/CreateCollection))
        (as-> st (pass db st "material_change" (t "material_change")
                       material.i/create-change!
                       material.spec/CreateMaterialChange))
        (as-> st (pass db st "note" (t "note") note.i/create!
                       note.spec/CreateNote))
        (as-> st (pass-tag-link db st (t "tag_link")))
        (as-> st (pass-taxon-update db st "taxon_vernacular"
                                    (t "taxon_vernacular") :vernacular-names))
        (as-> st (pass db st "taxon_synonym" (t "taxon_synonym")
                       synonym.i/add-synonym! synonym.spec/CreateSynonym))
        (as-> st (pass-taxon-update db st "taxon_distribution"
                                    (t "taxon_distribution") :distribution))
        (as-> st (restore-created-at! db st records))
        (as-> st (record-provenance! db st)))))

(defn- report [state {:keys [dry-run]}]
  (let [{:keys [counts failures warnings]} state]
    (doseq [w (sort warnings)]
      (println "warning:" w))
    (doseq [[table n] (sort counts)]
      (println (format "  %-22s %6d" table n)))
    (doseq [[table n] (sort (:duplicates state))]
      (println (format "  %-22s %6d already linked -- two source rows resolved"
                       table n)
               "to one Sepal record"))
    (when (seq failures)
      (println)
      (println (format "%d records failed:" (count failures)))
      (doseq [{:keys [table source-id message]} (take 50 failures)]
        (println (format "  %s %s: %s" table source-id message)))
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
pass --allow-nonempty. Loading twice would duplicate every row: a source id is
only unique within its own system, so nothing here can be idempotent.")
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
