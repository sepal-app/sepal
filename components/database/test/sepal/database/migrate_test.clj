(ns sepal.database.migrate-test
  (:require [babashka.fs :as fs]
            [clojure.java.io :as io]
            [clojure.java.shell :as shell]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [next.jdbc :as jdbc]
            [sepal.database.interface :as db.i])
  (:import [java.io File]))

(defn- fresh-db
  "A database with the current schema loaded."
  [dir]
  (let [db-path (str (fs/path dir "sepal.db"))]
    (db.i/load-schema! {:db-path db-path})
    db-path))

(defn- floor-db
  "A database built from the floor snapshot, at minimum-supported-version --
  before the taxon_rank migration, so migrate! has it to apply. schema.sql
  now bakes that migration in, so load-schema! is at the latest version
  already and cannot exercise it; this is what does."
  [dir]
  (let [version (db.i/minimum-supported-version)
        resource-name (str "test/schema-" version ".sql")
        resource (io/resource resource-name)]
    (when-not resource
      (throw (ex-info (str resource-name " is not on the test classpath. The floor moved; "
                           "snapshot schema.sql as it stands at the new floor into "
                           "components/test/resources/" resource-name ".")
                      {:reason :floor-snapshot-missing :version version})))
    (let [db-path (str (fs/path dir "sepal.db"))
          file (File/createTempFile "sepal-floor-schema" ".sql")]
      (try
        (with-open [in (io/input-stream resource)]
          (io/copy in file))
        (let [{:keys [exit err]} (shell/sh "sqlite3" "-bail" "-init" (.getAbsolutePath file) db-path "")]
          (when-not (zero? exit)
            (throw (ex-info (str "Loading " resource-name " into " db-path " failed")
                            {:reason :floor-schema-load-failed
                             :resource resource-name
                             :db-path db-path
                             :err err}))))
        (finally
          (.delete file)))
      db-path)))

(defn- query [db-path sql]
  (let [ds (jdbc/get-datasource {:jdbcUrl (str "jdbc:sqlite:" db-path)})]
    (mapv (comp first vals) (jdbc/execute! ds [sql]))))

(defn- schema-shape
  "The non-schema_version rows of sqlite_master, as an ordered vector of
  [type name tbl_name sql] tuples -- the shape of a database's schema,
  independent of how it was built."
  [db-path]
  (let [ds (jdbc/get-datasource {:jdbcUrl (str "jdbc:sqlite:" db-path)})]
    (->> (jdbc/execute! ds ["select type, name, tbl_name, sql from sqlite_master
                             where name <> 'schema_version'
                             order by type, name"])
         (mapv (juxt :sqlite_master/type :sqlite_master/name
                     :sqlite_master/tbl_name :sqlite_master/sql)))))

(deftest test-migration-files-enumerated-in-order
  (testing "the shipped migrations are found on the classpath, timestamp-ordered"
    (let [dir (io/file "components/database/resources/database/migrations")
          on-disk (->> (.listFiles dir)
                       (map #(.getName %))
                       (filter #(str/ends-with? % ".sql"))
                       sort
                       vec)]
      (is (= on-disk (db.i/migration-files)))
      (is (seq (db.i/migration-files)) "enumeration must never come back empty")
      (is (apply <= (map (comp parse-long #(first (str/split % #"_"))) (db.i/migration-files)))
          "filenames must sort by ascending timestamp"))))

(deftest test-schema-version-and-latest
  (testing "a freshly loaded schema is already at the latest version"
    (let [dir (fs/create-temp-dir {:prefix "sepal-migrate"})]
      (try
        (let [db-path (fresh-db dir)]
          (is (= (db.i/latest-version) (db.i/schema-version {:db-path db-path})))
          (is (empty? (db.i/pending {:db-path db-path}))))
        (finally (fs/delete-tree dir))))))

(deftest test-migrate-applies-pending
  ;; A real database behind the code lacks the objects a pending migration
  ;; creates, so migrate! runs its DDL. Using :migrations-dir with a synthetic
  ;; migration models that: the schema dump already contains the shipped
  ;; migrations' tables, so replaying those against it would fail.
  (testing "a pending migration is applied and recorded"
    (let [dir (fs/create-temp-dir {:prefix "sepal-migrate"})
          migrations (fs/create-dirs (fs/path dir "migrations"))
          version "29990101000000"]
      (try
        (let [db-path (fresh-db dir)]
          (spit (str (fs/path migrations (str version "_add_probe.sql")))
                "create table probe (id integer primary key);\n")
          (is (= [version] (db.i/pending {:db-path db-path :migrations-dir (str migrations)})))
          (is (= {:applied [version]}
                 (db.i/migrate! {:db-path db-path :migrations-dir (str migrations)})))
          (is (contains? (set (query db-path "select version from schema_version")) version))
          (is (seq (query db-path "select name from sqlite_master where name = 'probe'")))
          (is (empty? (db.i/pending {:db-path db-path :migrations-dir (str migrations)}))))
        (finally (fs/delete-tree dir))))))

(deftest test-a-failing-migration-leaves-no-trace
  ;; The regression test for the -bail bug: a migration whose third statement
  ;; fails must not apply its earlier statements and must not record a version.
  (testing "a failing migration is atomic"
    (let [dir (fs/create-temp-dir {:prefix "sepal-migrate"})
          migrations (fs/create-dirs (fs/path dir "migrations"))
          version "29990101000000"]
      (try
        (let [db-path (fresh-db dir)]
          (spit (str (fs/path migrations (str version "_bad.sql")))
                (str "create table probe_one (id integer primary key);\n"
                     "insert into probe_one (id) values (1);\n"
                     "insert into probe_one (id) values (1);\n"   ; primary key conflict
                     "insert into probe_one (id) values (2);\n"))
          (let [thrown (try
                         (db.i/migrate! {:db-path db-path
                                         :migrations-dir (str migrations)})
                         nil
                         (catch clojure.lang.ExceptionInfo e e))]
            (is (some? thrown) "migrate! should have thrown")
            (is (= :migration-failed (:reason (ex-data thrown))))
            (is (= version (:version (ex-data thrown)))))
          (is (empty? (query db-path "select name from sqlite_master where name = 'probe_one'"))
              "the failed migration's table should not exist")
          (is (not (contains? (set (query db-path "select version from schema_version")) version))
              "the failed migration should not be recorded"))
        (finally (fs/delete-tree dir))))))

(deftest test-preflight-leaves-the-live-database-untouched
  (testing "preflight! reports success without writing to the live database"
    (let [dir (fs/create-temp-dir {:prefix "sepal-migrate"})]
      (try
        (let [db-path (fresh-db dir)
              before (fs/size db-path)
              bytes-before (vec (fs/read-all-bytes db-path))
              result (db.i/preflight! {:db-path db-path})]
          (is (:ok? result))
          (is (= before (fs/size db-path)))
          (is (= bytes-before (vec (fs/read-all-bytes db-path)))))
        (finally (fs/delete-tree dir))))))

(deftest test-migrate-up-to-stops-at-the-named-version
  (testing ":up-to applies migrations at or below it and leaves the rest pending"
    (let [dir (fs/create-temp-dir {:prefix "sepal-migrate"})
          migrations (fs/create-dirs (fs/path dir "migrations"))]
      (try
        (let [db-path (fresh-db dir)]
          (spit (str (fs/path migrations "29990101000000_probe_one.sql"))
                "create table probe_one (id integer primary key);\n")
          (spit (str (fs/path migrations "29990102000000_probe_two.sql"))
                "create table probe_two (id integer primary key);\n")
          (is (= {:applied ["29990101000000"]}
                 (db.i/migrate! {:db-path db-path
                                 :migrations-dir (str migrations)
                                 :up-to "29990101000000"})))
          (is (seq (query db-path "select name from sqlite_master where name = 'probe_one'")))
          (is (empty? (query db-path "select name from sqlite_master where name = 'probe_two'")))
          (is (= ["29990102000000"]
                 (db.i/pending {:db-path db-path
                                :migrations-dir (str migrations)})))
          (is (contains? (set (query db-path "select version from schema_version"))
                         "29990101000000")
              "the applied migration is recorded")
          (is (not (contains? (set (query db-path "select version from schema_version"))
                              "29990102000000"))
              "the capped-out migration is not"))
        (finally (fs/delete-tree dir))))))

(deftest test-migrate-up-to-latest-is-the-same-as-no-up-to
  (testing "an :up-to at or above the newest migration applies everything"
    (let [dir (fs/create-temp-dir {:prefix "sepal-migrate"})
          migrations (fs/create-dirs (fs/path dir "migrations"))]
      (try
        (let [db-path (fresh-db dir)]
          (spit (str (fs/path migrations "29990101000000_probe_one.sql"))
                "create table probe_one (id integer primary key);\n")
          (is (= {:applied ["29990101000000"]}
                 (db.i/migrate! {:db-path db-path
                                 :migrations-dir (str migrations)
                                 :up-to "29991231000000"})))
          (is (empty? (db.i/pending {:db-path db-path
                                     :migrations-dir (str migrations)}))))
        (finally (fs/delete-tree dir))))))

(deftest test-migrate-up-to-below-everything-applies-nothing
  (testing "an :up-to below the oldest pending migration is a no-op"
    (let [dir (fs/create-temp-dir {:prefix "sepal-migrate"})
          migrations (fs/create-dirs (fs/path dir "migrations"))]
      (try
        (let [db-path (fresh-db dir)]
          (spit (str (fs/path migrations "29990101000000_probe_one.sql"))
                "create table probe_one (id integer primary key);\n")
          (is (= {:applied []}
                 (db.i/migrate! {:db-path db-path
                                 :migrations-dir (str migrations)
                                 :up-to "20000101000000"})))
          (is (empty? (query db-path "select name from sqlite_master where name = 'probe_one'"))))
        (finally (fs/delete-tree dir))))))

(deftest test-taxon-rank-lookup-migration
  (testing "the rebuild keeps every taxon row, rebuilds FTS, and enforces the rank FK"
    (let [dir (fs/create-temp-dir {:prefix "sepal-rank-migration"})]
      (try
        (let [db-path (floor-db dir)
              ds (jdbc/get-datasource {:jdbcUrl (str "jdbc:sqlite:" db-path)})]
          (jdbc/execute! ds ["insert into taxon (name, rank) values ('Acer palmatum', 'species')"])
          (is (= ["20260831120000" "20260901153000" "20260902120000"]
                 (:applied (db.i/migrate! {:db-path db-path :up-to "20260902120000"})))
              "the migrations actually ran, not a no-op against an already-current schema")
          (is (= 36 (-> (jdbc/execute-one! ds ["select count(*) c from taxon_rank"]) :c))
              "36 seeded ranks")
          (is (= 1 (-> (jdbc/execute-one! ds ["select count(*) c from taxon"]) :c))
              "the existing row survived the rebuild")
          (is (= 1 (-> (jdbc/execute-one! ds ["select count(*) c from taxon_fts where taxon_fts match 'palmatum'"]) :c))
              "FTS was rebuilt and still matches")
          (jdbc/execute! ds ["insert into taxon (name, rank) values ('Acer palmatum ''Sango-kaku''', 'cultivar')"])
          (is (= 2 (-> (jdbc/execute-one! ds ["select count(*) c from taxon"]) :c))
              "a cultivar-rank taxon inserts")
          (is (empty? (jdbc/execute! ds ["pragma foreign_key_check"]))
              "no dangling references")
          (let [fk-ds (jdbc/get-datasource {:jdbcUrl (str "jdbc:sqlite:" db-path "?foreign_keys=on")})]
            (is (thrown? org.sqlite.SQLiteException
                         (jdbc/execute! fk-ds ["insert into taxon (name, rank) values ('Bogus', 'notarank')"]))
                "the foreign key refuses a rank absent from taxon_rank")))
        (finally
          (fs/delete-tree dir))))))

(deftest test-material-history-migration
  (testing "the rebuild keeps material rows, normalises dead quantities, and the new tables enforce their rules"
    (let [dir (fs/create-temp-dir {:prefix "sepal-material-history-migration"})]
      (try
        (let [db-path (floor-db dir)
              ds (jdbc/get-datasource {:jdbcUrl (str "jdbc:sqlite:" db-path)})]
          (jdbc/execute! ds ["insert into taxon (name, rank) values ('Acer palmatum', 'species')"])
          (jdbc/execute! ds ["insert into accession (code, taxon_id) values ('X-1', 1)"])
          (jdbc/execute! ds ["insert into location (code, name) values ('L1', 'Loc one')"])
          (jdbc/execute! ds ["insert into material (code, accession_id, location_id, status, quantity)
                              values ('M1', 1, 1, 'alive', 2)"])
          (jdbc/execute! ds ["insert into material (code, accession_id, location_id, status, quantity)
                              values ('M2', 1, 1, 'dead', 2)"])
          (is (= ["20260831120000" "20260901153000" "20260902120000"]
                 (:applied (db.i/migrate! {:db-path db-path :up-to "20260902120000"})))
              "the migrations actually ran, not a no-op against an already-current schema")
          (is (= 15 (first (query db-path "select count(*) from material_change_reason")))
              "15 seeded reasons")
          (is (= 2 (first (query db-path "select count(*) from material")))
              "the existing rows survived the rebuild")
          (is (= 0 (first (query db-path "select quantity from material where code = 'M2'")))
              "a dead plant with a positive quantity is normalised to 0, the only legal form")
          (jdbc/execute! ds ["insert into material (code, accession_id, location_id, status, quantity)
                              values ('M3', 1, 1, 'dead', 0)"])
          (is (= 3 (first (query db-path "select count(*) from material")))
              "a dead plant with quantity 0 inserts")
          (let [fk-ds (jdbc/get-datasource {:jdbcUrl (str "jdbc:sqlite:" db-path "?foreign_keys=on")})]
            (is (thrown? org.sqlite.SQLiteException
                         (jdbc/execute! fk-ds ["insert into material (code, accession_id, location_id, quantity)
                                                values ('M4', 1, 1, -1)"]))
                "quantity must be >= 0")
            (is (thrown? org.sqlite.SQLiteException
                         (jdbc/execute! fk-ds ["insert into material (code, accession_id, location_id, status, quantity)
                                                values ('M5', 1, 1, 'dead', 1)"]))
                "a dead plant cannot have a positive quantity")
            (is (thrown? org.sqlite.SQLiteException
                         (jdbc/execute! fk-ds ["insert into material (code, accession_id, location_id, status, quantity)
                                                values ('M5b', 1, 1, 'transferred', 1)"]))
                "a transferred plant cannot have a positive quantity")
            (jdbc/execute! fk-ds ["insert into material (code, accession_id, location_id, status, quantity)
                                   values ('M6', 1, 1, 'dormant', 5)"])
            (is (= 5 (first (query db-path "select quantity from material where code = 'M6'")))
                "a dormant lot can carry a positive quantity")
            (is (thrown? org.sqlite.SQLiteException
                         (jdbc/execute! fk-ds ["insert into material (code, accession_id, location_id, status, quantity)
                                                values ('M7', 1, 1, 'notastatus', 1)"]))
                "the foreign key refuses a status absent from material_status")
            (jdbc/execute! fk-ds ["insert into material_change (material_id, from_location_id, to_location_id, quantity, reason, changed_at)
                                   values (1, 1, 1, -1, 'dead', '2026-09-01')"])
            (is (= 1 (-> (jdbc/execute-one! fk-ds ["select count(*) c from material_change"]) :c))
                "a change row with a known reason inserts")
            (is (thrown? org.sqlite.SQLiteException
                         (jdbc/execute! fk-ds ["insert into material_change (material_id, quantity, reason, changed_at)
                                                values (1, 1, 'notareason', '2026-09-01')"]))
                "the foreign key refuses a reason absent from material_change_reason"))
          (is (empty? (jdbc/execute! ds ["pragma foreign_key_check"]))
              "no dangling references"))
        (finally
          (fs/delete-tree dir))))))

(deftest test-provisioned-and-migrated-schemas-match
  (testing "a database provisioned from schema.sql matches one built from the floor snapshot and migrated"
    (let [provisioned-dir (fs/create-temp-dir {:prefix "sepal-schema-parity-provisioned"})
          migrated-dir (fs/create-temp-dir {:prefix "sepal-schema-parity-migrated"})]
      (try
        (let [provisioned-path (fresh-db provisioned-dir)
              migrated-path (floor-db migrated-dir)]
          (db.i/migrate! {:db-path migrated-path})
          (is (= (schema-shape provisioned-path) (schema-shape migrated-path))
              "a provisioned garden and a migrated one must have the same schema")
          (is (= (query provisioned-path "select name from taxon_rank order by name")
                 (query migrated-path "select name from taxon_rank order by name"))
              "the taxon_rank seed in schema.sql must match the one in the migration")
          (is (= (query provisioned-path "select code || '|' || label from material_change_reason order by code")
                 (query migrated-path "select code || '|' || label from material_change_reason order by code"))
              "the material_change_reason seed in schema.sql must match the one in the migration")
          (is (= (query provisioned-path "select name from material_status order by name")
                 (query migrated-path "select name from material_status order by name"))
              "the material_status seed in schema.sql must match the one in the migration"))
        (finally
          (fs/delete-tree provisioned-dir)
          (fs/delete-tree migrated-dir))))))
