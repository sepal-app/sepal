(ns sepal.app.cli.load-import-test
  "The resolvers pure, then the whole thing against a real database.

  The fixture garden uses `created` taxon references so it needs no WFO
  taxonomy loaded -- a `wfo` reference only resolves against the release the
  review file was made against, which is a property of the operator's garden
  rather than of this code."
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
        ;; It omits a file with no rows, and that is not an error.
        (is (= [] (li/read-table dir "note"))))

      (testing "snake_case keys arrive kebab-cased"
        (spit (fs/file (fs/path dir "note.json"))
              (json/write-str [{"bauble_id" "accession_note:1"
                                "resource_type" "accession"
                                "accession_bauble_id" "975"}]))
        (is (= {:bauble-id "accession_note:1"
                :resource-type "accession"
                :accession-bauble-id "975"}
               (first (li/read-table dir "note")))))

      (testing "malformed JSON throws rather than loading nothing"
        (spit (fs/file (fs/path dir "tag.json")) "{not json")
        (is (thrown? Exception (li/read-table dir "tag"))))
      (finally
        (fs/delete-tree dir)))))

(deftest test-resolve-taxon-ref
  (let [ids {"taxon_create" {"1254" 99}}]
    (testing "a created reference finds the taxon this run made"
      (is (= {:id 99} (li/resolve-taxon-ref nil ids {:kind "created"
                                                     :value "1254"}))))

    (testing "a created reference to a taxon that was not made fails"
      (is (:error (li/resolve-taxon-ref nil ids {:kind "created"
                                                 :value "9999"}))))

    (testing "a local reference is accepted, with the risk named"
      (let [got (li/resolve-taxon-ref nil ids {:kind "local" :value 5})]
        (is (= 5 (:id got)))
        (is (:warn got))))

    (testing "a kind this loader does not know fails rather than guessing"
      (is (:error (li/resolve-taxon-ref nil ids {:kind "orcid" :value 1}))))))

(deftest test-resolve-taxon-ref-against-wfo
  ;; The `wfo` branch is the one BBG's import uses, and it needs a database: it
  ;; asks what the garden's taxonomy already holds.
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
      (let [resolve #(li/resolve-taxon-ref *db* {} {:kind "wfo" :value %})]
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

(deftest test-resolve-parent
  (let [ids {"accession" {"975" 7} "material" {"271" 12}}]
    (testing "each resource_type reads the key it implies"
      (is (= {:id 7} (li/resolve-parent nil ids
                                        {:resource-type "accession"
                                         :accession-bauble-id "975"})))
      (is (= {:id 12} (li/resolve-parent nil ids
                                         {:resource-type "material"
                                          :material-bauble-id "271"}))))

    (testing "a parent that was not loaded fails, naming it"
      (let [got (li/resolve-parent nil ids {:resource-type "accession"
                                            :accession-bauble-id "404"})]
        (is (re-find #"accession 404" (:error got)))))

    ;; A row whose resource_type names a key it does not carry is a converter
    ;; bug, and has to be seen rather than silently skipped.
    (testing "a resource_type with no matching key fails"
      (is (:error (li/resolve-parent nil ids {:resource-type "material"})))
      (is (:error (li/resolve-parent nil ids {:resource-type "taxon"}))))

    (testing "a resource_type Sepal has no room for fails"
      (is (:error (li/resolve-parent nil ids {:resource-type "location"
                                              :accession-bauble-id "975"}))))))

;;; ---------------------------------------------------------------------------
;;; End to end, against a real database
;;; ---------------------------------------------------------------------------

(defn- write-fixture!
  "A garden small enough to assert on, using `created` taxon references so it
  needs no WFO taxonomy loaded."
  [dir & {:keys [suffix] :or {suffix "a"}}]
  (doseq [[table rows]
          {"taxon_create" [{"bauble_id" "900" "name" "Cattleya" "rank" "genus"}]
           "location" [{"bauble_id" "10" "code" "BL1" "name" "Block 1"}]
           ;; tag.name is `unique collate nocase` and the suite shares one
           ;; database, so each test needs its own.
           "tag" [{"bauble_id" "20" "name" (str "Fixture tag " suffix)}]
           "accession" [{"bauble_id" "30" "code" (str "2024.000" suffix)
                         "created_at" "2006-10-11 09:33:25"
                         "taxon_ref" {"kind" "created" "value" "900"}}]
           "material" [{"bauble_id" "40" "code" "1" "quantity" 1
                        "type" "plant" "status" "alive"
                        "accession_bauble_id" "30"
                        "location_bauble_id" "10"}]
           "note" [{"bauble_id" "accession_note:50" "body" "an imported note"
                    "resource_type" "accession" "accession_bauble_id" "30"}
                   {"bauble_id" "plant_note:50" "body" "same id, other table"
                    "resource_type" "material" "material_bauble_id" "40"}]
           "tag_link" [{"bauble_id" "60" "tag_bauble_id" "20"
                        "resource_type" "accession"
                        "accession_bauble_id" "30"}]}]
    (spit (fs/file (fs/path dir (str table ".json"))) (json/write-str rows))))

(defn- counts [db]
  (into {} (for [t [:accession :material :note :tag_link :location :tag]]
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
              ;; Two notes with the same Bauble id from different tables.
              (is (= 2 (- (:note after) (:note before))))))

          (testing "the namespaced note ids stay distinct"
            (let [m (json/read-str (slurp (fs/file (fs/path dir "loaded.json"))))]
              (is (not= (get-in m ["note" "accession_note:50"])
                        (get-in m ["note" "plant_note:50"])))))

          (testing "Bauble's created_at survives, and updated_at cannot"
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
          (write-fixture! dir)
          ;; A material whose accession was never emitted. Everything before it
          ;; in the order is valid, so this proves the rollback reaches back
          ;; through the passes that already succeeded.
          (spit (fs/file (fs/path dir "material.json"))
                (json/write-str [{"bauble_id" "41" "code" "1" "quantity" 1
                                  "type" "plant" "status" "alive"
                                  "accession_bauble_id" "does-not-exist"
                                  "location_bauble_id" "10"}]))
          (is (= 1 (li/load-import! db {:dir (str dir)
                                        :actor (:user/email user)
                                        :allow-nonempty true}))
              "a failed load exits non-zero")
          (is (= before (counts db))
              "and leaves no accession, location or tag behind")
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
          (write-fixture! dir)
          (testing "a directory that is not there"
            (is (= 1 (li/load-import! db {:dir "/tmp/nope-not-here"
                                          :actor (:user/email user)}))))
          (testing "an actor with no user row -- it will not create one"
            (is (= 1 (li/load-import! db {:dir (str dir)
                                          :actor "nobody@example.invalid"
                                          :allow-nonempty true}))))
          (finally
            (fs/delete-tree dir)))))))

(deftest test-validation-reaches-the-component-specs
  ;; The property that justifies writing through the interfaces at all: a row
  ;; the application would refuse cannot be imported either. material.code is
  ;; [:string {:min 1}], so a blank one is refused rather than inserted.
  ;;
  ;; Not quantity 0, which the spec for this once named: plan 033 relaxed it to
  ;; nat-int? so a dead plant is representable, and 0 is now valid.
  (tf/testing "a record the app would refuse is refused here"
    {[::user.i/factory :key/user] {:db *db* :role :admin}}
    (fn [{:keys [user]}]
      (let [db *db*
            dir (fs/create-temp-dir {:prefix "load-import-spec"})
            before (counts db)]
        (try
          (write-fixture! dir :suffix "b")
          (spit (fs/file (fs/path dir "material.json"))
                (json/write-str [{"bauble_id" "40" "code" "" "quantity" 1
                                  "type" "plant" "status" "alive"
                                  "accession_bauble_id" "30"
                                  "location_bauble_id" "10"}]))
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
          (write-fixture! dir :suffix "c")
          ;; One unresolvable material, one unresolvable note: different
          ;; passes, so stopping at the first would hide the second.
          (spit (fs/file (fs/path dir "material.json"))
                (json/write-str [{"bauble_id" "41" "code" "1" "quantity" 1
                                  "type" "plant" "status" "alive"
                                  "accession_bauble_id" "nope"
                                  "location_bauble_id" "10"}]))
          (spit (fs/file (fs/path dir "note.json"))
                (json/write-str [{"bauble_id" "accession_note:51"
                                  "body" "orphan"
                                  "resource_type" "accession"
                                  "accession_bauble_id" "also-nope"}]))
          (let [out (with-out-str
                      (li/load-import! db {:dir (str dir)
                                           :actor (:user/email user)
                                           :allow-nonempty true}))]
            (is (re-find #"material 41" out))
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
