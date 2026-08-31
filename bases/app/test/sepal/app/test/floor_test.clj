(ns sepal.app.test.floor-test
  "The N-1 path: a database built at the schema floor, migrated up to latest.

  022 set the floor and wired the CI matrix but left the floor leg building the
  same database as the latest leg, because provision! loads the current schema.
  It carried an assertion that the floor still equalled latest. These tests
  cover what replaced that assertion."
  (:require [babashka.fs :as fs]
            [clojure.test :refer [deftest is testing]]
            [next.jdbc :as jdbc]
            [sepal.app.instance :as instance]
            [sepal.app.test.system :as test.system]))

(defn- table-names [db-path]
  (->> (jdbc/execute! (jdbc/get-datasource {:jdbcUrl (str "jdbc:sqlite:" db-path)})
                      ["select name from sqlite_master where type='table'"])
       (map :sqlite_master/name)
       set))

(deftest test-floor-snapshot-builds-a-floor-database
  (testing "the snapshot lands at the floor version, not at latest"
    (let [dir (fs/create-temp-dir {:prefix "sepal-floor-test"})
          db-path (str (fs/path dir "sepal.db"))]
      (try
        (test.system/load-floor-schema! {:db-path db-path})
        (is (= (instance/minimum-schema-version)
               (instance/schema-version {:db-path db-path})))
        (is (contains? (table-names db-path) "taxon")
            "the snapshot is a real schema, not an empty file")
        (finally
          (fs/delete-tree dir)))))

  (testing "migrate! carries a floor database up to latest"
    (let [dir (fs/create-temp-dir {:prefix "sepal-floor-migrate"})
          db-path (str (fs/path dir "sepal.db"))]
      (try
        (test.system/load-floor-schema! {:db-path db-path})
        (instance/migrate! {:db-path db-path})
        (is (= (instance/latest-schema-version)
               (instance/schema-version {:db-path db-path})))
        (finally
          (fs/delete-tree dir))))))
