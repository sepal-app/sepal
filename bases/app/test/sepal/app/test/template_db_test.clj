(ns sepal.app.test.template-db-test
  "The fixture no longer builds each namespace's database from scratch. It
  builds one template per JVM and copies it, which took the per-namespace cost
  from 110 ms to 2 ms across 75 namespaces.

  A copy is only safe if it is indistinguishable from the thing it replaced, so
  that is what these assert — against whichever builder this leg would have
  used, so the floor leg checks the floor snapshot and the latest leg checks
  provision!."
  (:require [babashka.fs :as fs]
            [clojure.test :refer [deftest is testing]]
            [next.jdbc :as jdbc]
            [sepal.app.instance :as instance]
            [sepal.app.test.system :as test.system]))

(defn- schema-rows
  "Every object SQLite records for the database, as text."
  [db-path]
  (->> (jdbc/execute! (jdbc/get-datasource {:jdbcUrl (str "jdbc:sqlite:" db-path)})
                      ["select type, name, sql from sqlite_master order by type, name"])
       (mapv (juxt :sqlite_master/type :sqlite_master/name :sqlite_master/sql))))

(defn- built-the-old-way
  "A database built the way this leg built one before the template existed."
  [db-path]
  (if (test.system/floor-leg?)
    (test.system/load-floor-schema! {:db-path db-path})
    (instance/provision! {:db-path db-path})))

(defn- with-temp-dir [f]
  (let [dir (fs/create-temp-dir {:prefix "sepal-template-test"})]
    (try (f dir)
         (finally (fs/delete-tree dir)))))

(deftest test-a-copied-database-matches-a-freshly-built-one
  (testing "same tables, indexes and triggers, with the same DDL"
    (with-temp-dir
      (fn [dir]
        (let [built (str (fs/path dir "built.db"))
              copied (str (fs/path dir "copied.db"))]
          (built-the-old-way built)
          (test.system/fresh-database! {:db-path copied})
          (is (= (schema-rows built) (schema-rows copied)))
          (is (some #{"taxon"} (map second (schema-rows copied)))
              "a real schema, not an empty file"))))))

(deftest test-a-copied-database-is-at-the-schema-this-leg-tests
  (testing "the floor leg gets the floor, the latest leg gets latest"
    (with-temp-dir
      (fn [dir]
        (let [db-path (str (fs/path dir "sepal.db"))]
          (test.system/fresh-database! {:db-path db-path})
          (is (= (if (test.system/floor-leg?)
                   (instance/minimum-schema-version)
                   (instance/latest-schema-version))
                 (instance/schema-version {:db-path db-path}))))))))

(deftest test-each-call-returns-an-independent-database
  (testing "a write to one is invisible to the other — 75 namespaces share the
            template and must not share its contents"
    (with-temp-dir
      (fn [dir]
        (let [a (str (fs/path dir "a.db"))
              b (str (fs/path dir "b.db"))]
          (test.system/fresh-database! {:db-path a})
          (test.system/fresh-database! {:db-path b})
          (jdbc/execute! (jdbc/get-datasource {:jdbcUrl (str "jdbc:sqlite:" a)})
                         ["insert into settings (key, value) values ('probe', 'a')"])
          (is (empty? (jdbc/execute! (jdbc/get-datasource {:jdbcUrl (str "jdbc:sqlite:" b)})
                                     ["select 1 from settings where key = 'probe'"]))))))))
