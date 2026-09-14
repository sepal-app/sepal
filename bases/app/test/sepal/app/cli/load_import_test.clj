(ns sepal.app.cli.load-import-test
  "The resolvers pure, then the whole thing against a real database.

  The fixture garden references its taxon by `table`, so it needs no WFO
  taxonomy loaded -- a `wfo` reference only resolves against the release the
  input was made against, which is a property of the operator's garden rather
  than of this code."
  (:require [babashka.fs :as fs]
            [clojure.data.json :as json]
            [clojure.test :refer [deftest is testing]]
            [next.jdbc.sql :as jdbc.sql]
            [sepal.app.cli.load-import :as li]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.database.interface :as db.i]
            [sepal.taxon.interface :as taxon.i]
            [sepal.user.interface :as user.i]))

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
                     (rec (str "plant_note:50-" suffix)
                          {"body" "same id, other table"
                           "resource_type" "material"}
                          {"resource_id" (ref-to "material" material)})
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
                        (get-in m ["note" "plant_note:50-a"])))))

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
                               :where [:= :type "settings/import-completed"]})))))

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
      :taxon/wfo-taxon-id "wfo-0000000001-2025-06"}
     [::taxon.i/factory :key/twin-a]
     {:db *db* :name "Cattleya warneri" :rank "species"
      :taxon/wfo-taxon-id "wfo-0000000002-2025-06"}
     [::taxon.i/factory :key/twin-b]
     {:db *db* :name "Cattleya warscewiczii" :rank "species"
      :taxon/wfo-taxon-id "wfo-0000000002-2025-06"}}
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
