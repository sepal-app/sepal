(ns sepal.database.connection-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [sepal.database.interface :as db.i]))

(deftest test-hikari-spec-sets-every-pragma-as-a-query-parameter
  (testing "the pragmas Sepal depends on all reach SQLite through the JDBC URL"
    (let [{:keys [jdbcUrl]} (db.i/hikari-spec {:db-path "/tmp/sepal.db"})]
      (is (str/starts-with? jdbcUrl "jdbc:sqlite:/tmp/sepal.db?"))
      (doseq [pragma ["journal_mode=WAL"
                      "foreign_keys=ON"
                      "enable_load_extension=true"
                      "busy_timeout=5000"]]
        (is (str/includes? jdbcUrl pragma)
            (str pragma " missing from " jdbcUrl))))))

(deftest test-busy-timeout-is-set-for-every-caller
  (testing "a pooled datasource can have several connections writing to one
            database; without busy_timeout the loser gets SQLITE_BUSY at once
            rather than waiting"
    (is (str/includes? (:jdbcUrl (db.i/hikari-spec {:db-path "/tmp/sepal.db"}))
                       "busy_timeout=5000"))))

(deftest test-connection-init-sql-loads-spatialite
  (testing "with a library path, the extension is loaded from it"
    (let [{:keys [connectionInitSql]}
          (db.i/hikari-spec {:db-path "/tmp/sepal.db"
                             :extension-library-path "/nix/store/spatialite/lib"})]
      (is (= "SELECT load_extension('/nix/store/spatialite/lib/mod_spatialite')"
             connectionInitSql))))

  (testing "without one, the bare name is used so the loader searches its own path"
    (let [{:keys [connectionInitSql]} (db.i/hikari-spec {:db-path "/tmp/sepal.db"})]
      (is (= "SELECT load_extension('mod_spatialite')" connectionInitSql)))))
