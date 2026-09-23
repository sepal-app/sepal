(ns sepal.app.cli.load-import-test
  "The resolvers pure, then the whole thing against a real database.

  The fixture garden references its taxon by `table`, so it needs no WFO
  taxonomy loaded -- a `wfo` reference only resolves against the release the
  input was made against, which is a property of the operator's garden rather
  than of this code."
  (:require [babashka.fs :as fs]
            [clojure.data.json :as json]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [next.jdbc.sql :as jdbc.sql]
            [sepal.activity.interface :as activity.i]
            [sepal.app.cli.load-import :as li]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.database.interface :as db.i]
            [sepal.taxon.interface :as taxon.i]
            [sepal.user.interface :as user.i])
  (:import [java.time Instant]))

(clojure.test/use-fixtures :once default-system-fixture)

(deftest test-read-table
  (let [dir (fs/create-temp-dir {:prefix "load-import"})]
    (try
      (testing "a file the converter did not write is an empty table"
        ;; It omits a file with no records, and that is not an error.
        (is (= [] (li/read-table dir "note"))))

      (testing "snake_case keys arrive kebab-cased, at every depth"
        (spit (fs/file (fs/path dir "note.json"))
              (json/write-str [{"id" "accession_note:1"
                                "refs" {"resource_id"
                                        {"table" "accession" "id" "975"}}
                                "data" {"resource_type" "accession"
                                        "body" "a note"}}]))
        (is (= {:id "accession_note:1"
                :refs {:resource-id {:table "accession" :id "975"}}
                :data {:resource-type "accession" :body "a note"}}
               (first (li/read-table dir "note")))))

      (testing "malformed JSON throws rather than loading nothing"
        (spit (fs/file (fs/path dir "tag.json")) "{not json")
        (is (thrown? Exception (li/read-table dir "tag"))))
      (finally
        (fs/delete-tree dir)))))

(deftest test-resolve-ref
  (let [ids {"accession" {"975" 7} "taxon" {"1254" 99}}]
    (testing "a table reference finds the record this run made"
      (is (= {:id 7} (li/resolve-ref nil ids {:table "accession" :id "975"})))
      (is (= {:id 99} (li/resolve-ref nil ids {:table "taxon" :id "1254"}))))

    (testing "a table reference to a record that was not loaded fails"
      (let [got (li/resolve-ref nil ids {:table "accession" :id "404"})]
        (is (re-find #"accession 404" (:error got)))))

    (testing "a file this run never read fails rather than resolving to nil"
      (is (:error (li/resolve-ref nil ids {:table "location" :id "1"}))))

    (testing "a sepal_id is accepted, with the risk named"
      (let [got (li/resolve-ref nil ids {:sepal-id 5})]
        (is (= 5 (:id got)))
        (is (:warn got))))

    (testing "a reference shape this loader does not know fails"
      (is (:error (li/resolve-ref nil ids {:orcid "0000-0002"}))))))

(deftest test-resolve-ref-by-user-email
  ;; The other half of `wfo`: a natural key the target garden already carries,
  ;; for a record this import did not create. An import that attributes rows to
  ;; a real person has nothing else stable to name the account by.
  (tf/testing "a user reference resolves by address, and refuses to invent one"
    {[::user.i/factory :key/user] {:db *db* :role :admin}}
    (fn [{:keys [user]}]
      (testing "an address the garden holds"
        (is (= {:id (:user/id user)}
               (li/resolve-ref *db* {} {:user-email (:user/email user)}))))

      (testing "one it does not, named in the error"
        (let [got (li/resolve-ref *db* {} {:user-email "nobody@example.invalid"})]
          (is (re-find #"nobody@example.invalid" (:error got)))
          (is (re-find #"will not create it" (:error got))))))))

(deftest test-field-path
  (testing "a plain field is its own path"
    (is (= [:taxon-id] (li/field-path :taxon-id)))
    (is (= [:created-by] (li/field-path :created-by))))

  (testing "a dot descends into a nested map"
    ;; An activity's `data` carries the subject's own ids as context, and
    ;; AccessionActivityData will not validate without a real :taxon-id.
    (is (= [:data :taxon-id] (li/field-path :data.taxon-id)))))

(deftest test-apply-refs
  (testing "a resolved reference is written onto the path that named it"
    (is (= {:code "1" :accession-id 7}
           (li/apply-refs {:code "1"} {:accession-id 7}))))

  (testing "a nested reference adds to the map rather than replacing it"
    ;; Merging the two maps would drop :accession-code, which is the whole
    ;; payload the activity carries.
    (is (= {:type "accession/updated"
            :data {:accession-code "N0046" :taxon-id 12}}
           (li/apply-refs {:type "accession/updated"
                           :data {:accession-code "N0046"}}
                          {:data.taxon-id 12})))))

(deftest test-resolve-refs
  ;; The whole rule: a reference is keyed by the field it lands on.
  ;; material_change carries three, so this exercises the rule rather than a
  ;; single case.
  (let [ids {"material" {"271" 12} "location" {"1" 3 "4" 8}}]
    (testing "every reference lands on the field that named it"
      (is (= {:material-id 12 :from-location-id 3 :to-location-id 8}
             (:fields (li/resolve-refs
                        nil ids
                        {:material-id {:table "material" :id "271"}
                         :from-location-id {:table "location" :id "1"}
                         :to-location-id {:table "location" :id "4"}})))))

    (testing "a foreign key whose column does not end in _id needs no rule"
      ;; activity.created_by is the case that made this the rule: there is no
      ;; suffix to add or strip, because the key is already the field.
      (is (= {:created-by 12}
             (:fields (li/resolve-refs
                        nil {"user" {"3" 12}}
                        {:created-by {:table "user" :id "3"}})))))

    (testing "a record with no references resolves to no fields"
      (is (= {} (:fields (li/resolve-refs nil ids nil)))))

    (testing "one bad reference fails the record, naming it"
      (let [got (li/resolve-refs nil ids {:material-id {:table "material"
                                                        :id "999"}})]
        (is (re-find #"material 999" (:error got)))))

    (testing "a warning is carried up rather than swallowed"
      (is (seq (:warns (li/resolve-refs nil ids
                                        {:parent-id {:sepal-id 4}})))))))

;;; ---------------------------------------------------------------------------
;;; End to end, against a real database
;;; ---------------------------------------------------------------------------

(defn- rec
  ([source-id data] (rec source-id data nil))
  ([source-id data refs]
   (cond-> {"data" data}
     source-id (assoc "id" source-id)
     refs (assoc "refs" refs))))

(defn- ref-to [table source-id]
  {"table" table "id" source-id})

(defn- write-fixture!
  "A garden small enough to assert on, referencing its own taxon by table so it
  needs no WFO taxonomy loaded.

  `suffix` varies the source ids as well as the names. The suite shares one
  database, and `import_record` is unique on (source_table, source_id) -- two
  tests loading `accession 30` is the same collision a second import of one
  input would be."
  [dir & {:keys [suffix] :or {suffix "a"}}]
  (let [sid #(str % "-" suffix)
        taxon (sid "900")
        location (sid "10")
        tag (sid "20")
        accession (sid "30")
        material (sid "40")]
    (doseq [[table records]
            {"taxon" [(rec taxon {"name" (str "Cattleya " suffix)
                                  "rank" "genus"})]
             "location" [(rec location {"code" (str "BL" suffix)
                                        "name" "Block 1"})]
             ;; tag.name is `unique collate nocase`, so each test needs its own.
             "tag" [(rec tag {"name" (str "Fixture tag " suffix)})]
             "accession" [(-> (rec accession {"code" (str "2024.000" suffix)}
                                   {"taxon_id" (ref-to "taxon" taxon)})
                              (assoc "created_at" "2006-10-11 09:33:25"))]
             "material" [(rec material {"code" "1" "quantity" 1
                                        "type" "plant" "status" "alive"}
                              {"accession_id" (ref-to "accession" accession)
                               "location_id" (ref-to "location" location)})]
             "note" [(rec (str "accession_note:50-" suffix)
                          {"body" "an imported note"
                           "resource_type" "accession"}
                          {"resource_id" (ref-to "accession" accession)})
                     ;; Same numeric id as the note above, and the same
                     ;; accession -- the id prefix is the only thing telling
                     ;; the loader these are two different rows.
                     (rec (str "second_accession_note:50-" suffix)
                          {"body" "same id, other prefix"
                           "resource_type" "accession"}
                          {"resource_id" (ref-to "accession" accession)})
                     ;; A note on the taxon this run created. The converter
                     ;; used to hang this on the taxon record as a field, and
                     ;; the loader dropped it.
                     (rec (str "taxon_create:900-" suffix)
                          {"body" "why this taxon was created"
                           "resource_type" "taxon"}
                          {"resource_id" (ref-to "taxon" taxon)})]
             "tag_link" [(rec (sid "60") {"resource_type" "accession"}
                              {"tag_id" (ref-to "tag" tag)
                               "resource_id" (ref-to "accession" accession)})]}]
      (spit (fs/file (fs/path dir (str table ".json")))
            (json/write-str records)))))

(defn- counts [db]
  (into {} (for [t [:accession :material :note :tag_link :location :tag
                    :import_record]]
             [t (:c (db.i/execute-one! db {:select [[[:count :*] :c]]
                                           :from [t]}))])))

(deftest test-loading-a-fixture-garden
  (tf/testing "a small garden loads end to end"
    {[::user.i/factory :key/user] {:db *db* :role :admin}}
    (fn [{:keys [user]}]
      (let [db *db*
            dir (fs/create-temp-dir {:prefix "load-import-e2e"})
            before (counts db)]
        (try
          (write-fixture! dir)

          (testing "a dry run performs the load and writes nothing"
            (is (zero? (li/load-import! db {:dir (str dir)
                                            :actor (:user/email user)
                                            :dry-run true
                                            :allow-nonempty true})))
            (is (= before (counts db))))

          (testing "a real run lands every table"
            (is (zero? (li/load-import! db {:dir (str dir)
                                            :actor (:user/email user)
                                            :allow-nonempty true})))
            (let [after (counts db)]
              (is (= 1 (- (:accession after) (:accession before))))
              (is (= 1 (- (:material after) (:material before))))
              (is (= 1 (- (:tag_link after) (:tag_link before))))
              (is (= 3 (- (:note after) (:note before))))))

          (testing "a note on a created taxon lands as a note on that taxon"
            ;; Not as a field on the taxon record, which has no column for it.
            (let [taxon-id (-> (db.i/execute-one!
                                 db {:select [:id] :from [:taxon]
                                     :where [:= :name "Cattleya a"]})
                               :taxon/id)]
              (is (= "why this taxon was created"
                     (:note/body
                       (db.i/execute-one!
                         db {:select [:body] :from [:note]
                             :where [:and [:= :resource_type "taxon"]
                                     [:= :resource_id taxon-id]]}))))))

          (testing "the namespaced note ids stay distinct"
            (let [m (json/read-str (slurp (fs/file (fs/path dir
                                                            "loaded.json"))))]
              (is (not= (get-in m ["note" "accession_note:50-a"])
                        (get-in m ["note" "second_accession_note:50-a"])))))

          (testing "every file that creates rows records where they came from"
            ;; A create whose id key `landed-id` does not know records nothing,
            ;; silently. material_change was missing exactly that way.
            (is (= #{"accession" "location" "material" "note" "tag" "taxon"}
                   (set (map :import-record/source-table
                             (db.i/execute! db {:select-distinct [:source_table]
                                                :from [:import_record]}))))))

          (testing "every loaded record says where it came from"
            (let [row (db.i/execute-one!
                        db {:select [:resource_type :resource_id]
                            :from [:import_record]
                            :where [:and [:= :source_table "accession"]
                                    [:= :source_id "30-a"]]})]
              (is (= "accession" (:import-record/resource-type row)))
              (is (= (:accession/id
                       (db.i/execute-one! db {:select [:id] :from [:accession]
                                              :where [:= :code "2024.000a"]}))
                     (:import-record/resource-id row)))))

          (testing "the source's created_at survives, and updated_at cannot"
            ;; The eleven trigger_*_updated_at triggers have no WHEN clause, so
            ;; the fix-up that restores created_at sets updated_at to now.
            (let [row (db.i/execute-one! db {:select [:created_at :updated_at]
                                             :from [:accession]
                                             :where [:= :code "2024.000a"]})]
              (is (= "2006-10-11 09:33:25" (:accession/created-at row)))
              (is (not= "2006-10-11 09:33:25" (:accession/updated-at row)))))

          (testing "one completion event, not one per row"
            (is (= 1 (:c (db.i/execute-one!
                           db {:select [[[:count :*] :c]]
                               :from [:activity]
                               :where [:= :type "import/completed"]})))))

          (finally
            (fs/delete-tree dir)
            (jdbc.sql/delete! db :activity {:created_by (:user/id user)})))))))

(deftest test-a-failed-record-rolls-the-whole-load-back
  (tf/testing "one bad record leaves nothing behind"
    {[::user.i/factory :key/user] {:db *db* :role :admin}}
    (fn [{:keys [user]}]
      (let [db *db*
            dir (fs/create-temp-dir {:prefix "load-import-fail"})
            before (counts db)]
        (try
          (write-fixture! dir :suffix "b")
          ;; A material whose accession was never emitted. Everything before it
          ;; in the order is valid, so this proves the rollback reaches back
          ;; through the passes that already succeeded.
          (spit (fs/file (fs/path dir "material.json"))
                (json/write-str
                  [(rec "41-b" {"code" "1" "quantity" 1
                                "type" "plant" "status" "alive"}
                        {"accession_id" (ref-to "accession" "does-not-exist")
                         "location_id" (ref-to "location" "10-b")})]))
          (is (= 1 (li/load-import! db {:dir (str dir)
                                        :actor (:user/email user)
                                        :allow-nonempty true}))
              "a failed load exits non-zero")
          (is (= before (counts db))
              "and leaves no accession, location, tag or provenance behind")
          (finally
            (fs/delete-tree dir)
            (jdbc.sql/delete! db :activity {:created_by (:user/id user)})))))))

(deftest test-refusals
  (tf/testing "it refuses before writing anything"
    {[::user.i/factory :key/user] {:db *db* :role :admin}}
    (fn [{:keys [user]}]
      (let [db *db*
            dir (fs/create-temp-dir {:prefix "load-import-refuse"})]
        (try
          (write-fixture! dir :suffix "c")
          (testing "a directory that is not there"
            (is (= 1 (li/load-import! db {:dir "/tmp/nope-not-here"
                                          :actor (:user/email user)}))))
          (testing "an actor with no user row -- it will not create one"
            (is (= 1 (li/load-import! db {:dir (str dir)
                                          :actor "nobody@example.invalid"
                                          :allow-nonempty true}))))
          (finally
            (fs/delete-tree dir)))))))

(deftest test-data-reaches-the-component-specs-untouched
  ;; The property that justifies writing through the interfaces at all, and the
  ;; one that says `data` is passed through rather than filtered: a key no spec
  ;; accepts is a failure, not a silently dropped field.
  (tf/testing "a payload the app would refuse is refused here"
    {[::user.i/factory :key/user] {:db *db* :role :admin}}
    (fn [{:keys [user]}]
      (let [db *db*
            dir (fs/create-temp-dir {:prefix "load-import-spec"})
            before (counts db)]
        (try
          (write-fixture! dir :suffix "e")
          (spit (fs/file (fs/path dir "material.json"))
                (json/write-str
                  [(rec "40-e" {"code" "1" "quantity" 1 "type" "plant"
                                "status" "alive" "not_a_material_field" "x"}
                        {"accession_id" (ref-to "accession" "30-e")
                         "location_id" (ref-to "location" "10-e")})]))
          (is (= 1 (li/load-import! db {:dir (str dir)
                                        :actor (:user/email user)
                                        :allow-nonempty true})))
          (is (= before (counts db)))
          (finally
            (fs/delete-tree dir)
            (jdbc.sql/delete! db :activity {:created_by (:user/id user)})))))))

(deftest test-every-failure-is-reported-not-just-the-first
  (tf/testing "two bad records in different passes both appear"
    {[::user.i/factory :key/user] {:db *db* :role :admin}}
    (fn [{:keys [user]}]
      (let [db *db*
            dir (fs/create-temp-dir {:prefix "load-import-many"})]
        (try
          (write-fixture! dir :suffix "f")
          ;; One unresolvable material, one unresolvable note: different
          ;; passes, so stopping at the first would hide the second.
          (spit (fs/file (fs/path dir "material.json"))
                (json/write-str
                  [(rec "41-f" {"code" "1" "quantity" 1
                                "type" "plant" "status" "alive"}
                        {"accession_id" (ref-to "accession" "nope")
                         "location_id" (ref-to "location" "10-f")})]))
          (spit (fs/file (fs/path dir "note.json"))
                (json/write-str
                  [(rec "accession_note:51"
                        {"body" "orphan" "resource_type" "accession"}
                        {"resource_id" (ref-to "accession" "also-nope")})]))
          (let [out (with-out-str
                      (li/load-import! db {:dir (str dir)
                                           :actor (:user/email user)
                                           :allow-nonempty true}))]
            (is (re-find #"material 41-f" out))
            (is (re-find #"note accession_note:51" out))
            (is (re-find #"2 records failed" out)))
          (finally
            (fs/delete-tree dir)
            (jdbc.sql/delete! db :activity {:created_by (:user/id user)})))))))

(deftest test-a-nonempty-garden-is-refused-unless-allowed
  (tf/testing "loading twice would duplicate every row, so it refuses"
    {[::user.i/factory :key/user] {:db *db* :role :admin}}
    (fn [{:keys [user]}]
      (let [db *db*
            dir (fs/create-temp-dir {:prefix "load-import-nonempty"})]
        (try
          (write-fixture! dir :suffix "d")
          ;; Load once so the garden certainly holds an accession -- the suite
          ;; shares a database and test order is randomised, so this cannot
          ;; assume another test has already filled it.
          (is (zero? (li/load-import! db {:dir (str dir)
                                          :actor (:user/email user)
                                          :allow-nonempty true}))
              "the first load proceeds")
          (is (= 1 (li/load-import! db {:dir (str dir)
                                        :actor (:user/email user)}))
              "the second is refused without --allow-nonempty")
          (finally
            (fs/delete-tree dir)
            (jdbc.sql/delete! db :activity {:created_by (:user/id user)})))))))

(deftest test-a-second-load-of-the-same-input-is-refused-by-the-database
  ;; --allow-nonempty is the operator saying "I know". It is not a licence to
  ;; load the same input twice: import_record is unique on (source_table,
  ;; source_id), so the second load fails rather than duplicating silently.
  (tf/testing "the same source id cannot be imported twice"
    {[::user.i/factory :key/user] {:db *db* :role :admin}}
    (fn [{:keys [user]}]
      (let [db *db*
            dir (fs/create-temp-dir {:prefix "load-import-twice"})]
        (try
          (write-fixture! dir :suffix "g")
          (is (zero? (li/load-import! db {:dir (str dir)
                                          :actor (:user/email user)
                                          :allow-nonempty true})))
          (let [before (counts db)]
            (is (= 1 (li/load-import! db {:dir (str dir)
                                          :actor (:user/email user)
                                          :allow-nonempty true}))
                "the second load fails")
            (is (= before (counts db))
                "and rolls back, leaving the first load's rows alone"))
          (finally
            (fs/delete-tree dir)
            (jdbc.sql/delete! db :activity {:created_by (:user/id user)})))))))

(deftest test-resolve-ref-against-wfo
  ;; The `wfo` branch is the one a real import uses, and it needs a database:
  ;; it asks what the garden's taxonomy already holds.
  (tf/testing "a wfo reference resolves only when exactly one taxon carries it"
    {[::taxon.i/factory :key/one]
     {:db *db* :name "Cattleya labiata" :rank "species"
      :wfo-taxon-id "wfo-0000000001-2025-06"}
     [::taxon.i/factory :key/twin-a]
     {:db *db* :name "Cattleya warneri" :rank "species"
      :wfo-taxon-id "wfo-0000000002-2025-06"}
     [::taxon.i/factory :key/twin-b]
     {:db *db* :name "Cattleya warscewiczii" :rank "species"
      :wfo-taxon-id "wfo-0000000002-2025-06"}}
    (fn [{:keys [one]}]
      (let [resolve #(li/resolve-ref *db* {} {:wfo %})]
        (testing "one match is the taxon"
          (is (= {:id (:taxon/id one)} (resolve "wfo-0000000001-2025-06"))))

        (testing "no match fails, naming the id the operator has to go find"
          (is (re-find #"wfo-9999999999-2025-06"
                       (:error (resolve "wfo-9999999999-2025-06")))))

        ;; wfo_taxon_id has a plain index, not a unique one, so a garden can
        ;; hold two taxa under one WFO id. Picking either would be a guess.
        (testing "two matches fail rather than guessing"
          (is (re-find #"^2 taxa"
                       (:error (resolve "wfo-0000000002-2025-06")))))))))

(deftest test-a-contacts-split-address-round-trips-through-a-load
  ;; `data` goes to `contact.i/create!` verbatim and has to match
  ;; `CreateContact` exactly, so a field the spec does not know is a reported
  ;; failure rather than a dropped one. That makes the load the check on
  ;; whether the three address fields really reached the spec.
  (tf/testing "address1, address2 and city survive an import"
    {[::user.i/factory :key/user] {:db *db* :role :admin}}
    (fn [{:keys [user]}]
      (let [db *db*
            dir (fs/create-temp-dir {:prefix "load-import-contact"})]
        (try
          (spit (fs/file (fs/path dir "contact.json"))
                (json/write-str
                  [(rec "contact:1-addr"
                        {"name" "Fairchild Tropical Garden"
                         "address1" "10901 Old Cutler Road"
                         "address2" "Attn: Herbarium"
                         "city" "Coral Gables"
                         "country" "USA"})]))
          (is (zero? (li/load-import! db {:dir (str dir)
                                          :actor (:user/email user)
                                          :allow-nonempty true})))
          (let [row (db.i/execute-one!
                      db {:select [:address :address1 :address2 :city]
                          :from [:contact]
                          :where [:= :name "Fairchild Tropical Garden"]})]
            (is (= "10901 Old Cutler Road" (:contact/address1 row)))
            (is (= "Attn: Herbarium" (:contact/address2 row)))
            (is (= "Coral Gables" (:contact/city row)))
            (testing "and the field they replaced stays empty"
              (is (nil? (:contact/address row)))))
          (finally
            (fs/delete-tree dir)
            ;; The suite shares one database, and the fixture-garden test
            ;; asserts on `select distinct source_table from import_record`
            ;; across the whole table. A contact left behind here shows up
            ;; there, so this clears its own rows.
            (jdbc.sql/delete! db :import_record {:source_table "contact"})
            (jdbc.sql/delete! db :contact {:name "Fairchild Tropical Garden"})
            (jdbc.sql/delete! db :activity {:created_by (:user/id user)})))))))

(deftest test-an-observation-imports-with-its-created-at-restored
  ;; A location is the resource: it needs no accession/taxon chain to exist
  ;; first, and observation.spec/resource-type accepts it directly.
  (tf/testing "an import file carrying an observation creates the row"
    {[::user.i/factory :key/user] {:db *db* :role :admin}}
    (fn [{:keys [user]}]
      (let [db *db*
            dir (fs/create-temp-dir {:prefix "load-import-observation"})]
        (try
          (spit (fs/file (fs/path dir "location.json"))
                (json/write-str
                  [(rec "obsloc" {"code" "OBSLOC"
                                  "name" "Observation testing block"})]))
          (spit (fs/file (fs/path dir "observation.json"))
                (json/write-str
                  [(-> (rec "obs-1" {"resource_type" "location"
                                     "type" "general"
                                     "observed_on" "2020-05-01"
                                     "note" "load-import-observation-fixture"}
                            {"resource_id" (ref-to "location" "obsloc")})
                       (assoc "created_at" "2019-01-01 08:00:00"))]))
          (is (zero? (li/load-import! db {:dir (str dir)
                                          :actor (:user/email user)
                                          :allow-nonempty true})))
          (let [row (db.i/execute-one!
                      db {:select [:id :created_at]
                          :from [:observation]
                          :where [:= :note "load-import-observation-fixture"]})]
            (is (= "2019-01-01 08:00:00" (:observation/created-at row)))

            (testing "and it records where the row came from"
              ;; Mirrors "every loaded record says where it came from" in
              ;; test-loading-a-fixture-garden, for the accession table.
              (let [provenance
                    (db.i/execute-one!
                      db {:select [:resource_type :resource_id]
                          :from [:import_record]
                          :where [:and [:= :source_table "observation"]
                                  [:= :source_id "obs-1"]]})]
                (is (= "observation" (:import-record/resource-type provenance)))
                (is (= (:observation/id row)
                       (:import-record/resource-id provenance))))))
          (finally
            (fs/delete-tree dir)
            (jdbc.sql/delete! db :observation
                              {:note "load-import-observation-fixture"})
            (jdbc.sql/delete! db :import_record {:source_table "location"
                                                 :source_id "obsloc"})
            (jdbc.sql/delete! db :import_record {:source_table "observation"
                                                 :source_id "obs-1"})
            (jdbc.sql/delete! db :location {:code "OBSLOC"})
            (jdbc.sql/delete! db :activity {:created_by (:user/id user)})))))))

(deftest test-a-note-on-material-is-refused
  ;; note.spec/resource-type was narrowed to [:accession :taxon] -- material
  ;; records observations instead. No new code makes this fail; the test pins
  ;; the behaviour so `:material` being added back to the enum is caught.
  (tf/testing "a note whose resource_type is material fails, naming the row"
    {[::user.i/factory :key/user] {:db *db* :role :admin}}
    (fn [{:keys [user]}]
      (let [db *db*
            dir (fs/create-temp-dir {:prefix "load-import-note-material"})]
        (try
          (write-fixture! dir :suffix "h")
          (spit (fs/file (fs/path dir "note.json"))
                (json/write-str
                  [(rec "material_note:1" {"body" "a note on a plant"
                                           "resource_type" "material"}
                        {"resource_id" (ref-to "material" "40-h")})]))
          (let [out (with-out-str
                      (is (= 1 (li/load-import! db {:dir (str dir)
                                                    :actor (:user/email user)
                                                    :allow-nonempty true}))))]
            (is (re-find #"note material_note:1" out)))
          (finally
            (fs/delete-tree dir)
            (jdbc.sql/delete! db :activity {:created_by (:user/id user)})))))))

(deftest test-pass-activity
  ;; No test loaded an activity.json before this one -- the only activity
  ;; assertion elsewhere is `import/completed`, which is dispatched by
  ;; `load-import!` itself rather than through the activity pass.
  (tf/testing "an activity record loads and dispatches to the right schema"
    {[::user.i/factory :key/user] {:db *db* :role :admin}}
    (fn [{:keys [user]}]
      (let [db *db*
            dir (fs/create-temp-dir {:prefix "load-import-activity"})]
        (try
          (write-fixture! dir :suffix "h")
          (spit (fs/file (fs/path dir "activity.json"))
                (json/write-str
                  [(rec "activity:1"
                        {"type" "accession/created"
                         "created_at" "2020-06-15T12:00:00Z"
                         "resource_type" "accession"
                         "data" {"accession_code" "2024.000h"
                                 "taxon_id" 0}}
                        {"resource_id" (ref-to "accession" "30-h")
                         "created_by" {"user_email" (:user/email user)}
                         "data.taxon_id" (ref-to "taxon" "900-h")})]))

          (is (zero? (li/load-import! db {:dir (str dir)
                                          :actor (:user/email user)
                                          :allow-nonempty true})))

          (let [taxon-id (-> (db.i/execute-one!
                               db {:select [:id] :from [:taxon]
                                   :where [:= :name "Cattleya h"]})
                             :taxon/id)
                row (db.i/execute-one!
                      db {:select [:type :resource_type :created_by :data]
                          :from [:activity]
                          :where [:= :type "accession/created"]})]
            (is (= "accession/created" (:activity/type row)))
            (is (= "accession" (:activity/resource-type row)))
            (is (= (:user/id user) (:activity/created-by row)))
            (is (= {:accession-code "2024.000h" :taxon-id taxon-id}
                   (-> row :activity/data
                       (json/read-str :key-fn #(keyword (str/replace % "_" "-")))))))
          (finally
            (fs/delete-tree dir)
            (jdbc.sql/delete! db :import_record {:source_table "activity"})
            (jdbc.sql/delete! db :activity {:created_by (:user/id user)})))))))

(deftest test-activity-dispatch-needs-a-keyword-type
  ;; Pins the reason `pass-activity` converts `:type` to a keyword before
  ;; calling `activity.i/create!`: the multi-schema dispatches on `:type`
  ;; before decoding, so a string value matches no registered branch and a
  ;; keyword is the only value that reaches the right schema.
  (tf/testing "a string :type fails to dispatch; a keyword succeeds"
    {[::user.i/factory :key/user] {:db *db* :role :admin}}
    (fn [{:keys [user]}]
      (let [db *db*
            data {:accession-code "2024.0keyword" :taxon-id 1}]
        (try
          (testing "a string :type matches no branch of the multi-schema"
            (is (thrown? Exception
                         (activity.i/create!
                           db {:type "accession/created"
                               :created-at (Instant/now)
                               :created-by (:user/id user)
                               :resource-type :accession
                               :resource-id 1
                               :data data}))))

          (testing "a keyword :type dispatches to AccessionActivityData"
            (let [created (activity.i/create!
                            db {:type :accession/created
                                :created-at (Instant/now)
                                :created-by (:user/id user)
                                :resource-type :accession
                                :resource-id 1
                                :data data})]
              (is (= :accession/created (:activity/type created)))))
          (finally
            (jdbc.sql/delete! db :activity {:created_by (:user/id user)})))))))
