(ns sepal.database.migrate-test
  (:require [babashka.fs :as fs]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [next.jdbc :as jdbc]
            [sepal.database.interface :as db.i]))

(defn- fresh-db
  "A database with the current schema loaded."
  [dir]
  (let [db-path (str (fs/path dir "sepal.db"))]
    (db.i/load-schema! {:db-path db-path})
    db-path))

(defn- query [db-path sql]
  (let [ds (jdbc/get-datasource {:jdbcUrl (str "jdbc:sqlite:" db-path)})]
    (mapv (comp first vals) (jdbc/execute! ds [sql]))))

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
