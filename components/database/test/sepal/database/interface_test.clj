(ns sepal.database.interface-test
  (:require [babashka.fs :as fs]
            [clojure.test :as test :refer :all]
            [next.jdbc :as jdbc]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.database.interface :as db.i]))

(use-fixtures :once default-system-fixture)

;;; Basic query tests

(deftest execute-returns-vector-of-maps
  (is (= [{:x 1}] (db.i/execute! *db* ["select 1 as x"]))))

(deftest execute-one-returns-single-map
  (is (= {:x 1} (db.i/execute-one! *db* ["select 1 as x"]))))

(deftest json-returned-as-string
  (is (= [{:x "{}"}] (db.i/execute! *db* ["select json('{}') as x"]))))

;;; Column naming: snake_case -> kebab-case

(deftest snake-case-column-converted-to-kebab-case
  (is (= {:my-column 1}
         (db.i/execute-one! *db* ["select 1 as my_column"]))))

(deftest multiple-underscores-converted-to-kebab-case
  (is (= {:my-long-column-name 1}
         (db.i/execute-one! *db* ["select 1 as my_long_column_name"]))))

;; Special case: columns with numbers should not split on digits
(deftest s3-bucket-column-not-split-on-digit
  (testing "s3_bucket should become :s3-bucket, not :s-3-bucket"
    (is (= {:s3-bucket "test"}
           (db.i/execute-one! *db* ["select 'test' as s3_bucket"])))))

(deftest column-with-numbers-preserved
  (testing "column2_name should become :column2-name"
    (is (= {:column2-name 1}
           (db.i/execute-one! *db* ["select 1 as column2_name"])))))

;;; Namespaced keywords via __ separator

(deftest double-underscore-creates-namespaced-keyword
  (testing "table__column creates :table/column keyword"
    (is (= {:my-table/my-column 1}
           (db.i/execute-one! *db* ["select 1 as my_table__my_column"])))))

(deftest double-underscore-with-kebab-conversion
  (testing "snake_table__snake_column creates :snake-table/snake-column"
    (is (= {:snake-table/snake-column 1}
           (db.i/execute-one! *db* ["select 1 as snake_table__snake_column"])))))

(deftest double-underscore-with-numbers
  (testing "s3__bucket creates :s3/bucket, not :s-3/bucket"
    (is (= {:s3/bucket "test"}
           (db.i/execute-one! *db* ["select 'test' as s3__bucket"])))))

(deftest multiple-double-underscores-uses-first-split
  (testing "only first __ is used as separator, rest becomes part of column name"
    (is (= {:ns/col-with-more 1}
           (db.i/execute-one! *db* ["select 1 as ns__col_with__more"])))))

;;; Table name as namespace (when selecting from actual table)

(deftest table-provides-namespace-for-columns
  (testing "columns from a table query get table name as namespace"
    ;; Insert a test record, select it, then clean up
    (db.i/execute-one! *db* ["insert into taxon (name, rank) values ('Test', 'genus')"])
    (let [result (db.i/execute-one! *db* ["select id, name from taxon limit 1"])]
      (db.i/execute-one! *db* ["delete from taxon"])
      (is (some? result) "Expected a result from taxon table")
      (is (contains? result :taxon/id))
      (is (contains? result :taxon/name)))))

;;; Boolean/Integer handling
;; Note: SQLite stores booleans as integers (0/1). The JDBC driver reports them
;; as INTEGER type, not BOOLEAN. Boolean conversion is handled at the application
;; layer via Malli specs with :decode/store transformers.

(deftest sqlite-booleans-returned-as-integers
  (testing "SQLite boolean columns are returned as integers (0/1)"
    ;; Insert a test record with boolean field, select it, then clean up
    (db.i/execute-one! *db* ["insert into taxon (name, rank) values ('Test', 'genus')"])
    (let [taxon (db.i/execute-one! *db* ["select id from taxon limit 1"])
          _ (db.i/execute-one! *db* ["insert into accession (code, taxon_id, private) values ('test', ?, 0)"
                                     (:taxon/id taxon)])
          result (db.i/execute-one! *db* ["select private from accession limit 1"])]
      (db.i/execute-one! *db* ["delete from accession"])
      (db.i/execute-one! *db* ["delete from taxon"])
      (is (some? result) "Expected a result from accession table")
      ;; SQLite returns integers for boolean columns
      (is (integer? (:accession/private result))))))

;;; Edge cases

(deftest empty-result-returns-empty-vector
  (is (= [] (db.i/execute! *db* ["select 1 where 0 = 1"]))))

(deftest empty-result-execute-one-returns-nil
  (is (nil? (db.i/execute-one! *db* ["select 1 where 0 = 1"]))))

(deftest null-values-preserved
  (is (= {:x nil}
         (db.i/execute-one! *db* ["select null as x"]))))

;;; Schema loading from the classpath

(deftest test-load-schema-from-classpath
  (testing "loads the schema into a fresh database with no path argument"
    (let [dir (fs/create-temp-dir {:prefix "sepal-schema"})
          db-path (str (fs/path dir "sepal.db"))]
      (try
        (db.i/load-schema! {:db-path db-path})
        (let [ds (jdbc/get-datasource {:jdbcUrl (str "jdbc:sqlite:" db-path)})
              tables (->> (jdbc/execute! ds ["select name from sqlite_master where type = 'table'"])
                          (map (comp first vals))
                          set)]
          (is (contains? tables "user"))
          (is (contains? tables "accession"))
          (is (contains? tables "schema_version")))
        (finally
          (fs/delete-tree dir))))))

;;; Schema version gate

(deftest test-at-least-version?
  (testing "a database at or above the version has the feature"
    (is (true? (db.i/at-least-version? {:schema-version "20260902160000"}
                                       "20260902160000")))
    (is (true? (db.i/at-least-version? {:schema-version "20261001000000"}
                                       "20260902160000"))))
  (testing "a database below it does not"
    (is (false? (db.i/at-least-version? {:schema-version "20260113120000"}
                                        "20260902160000"))))
  (testing "an unknown version is treated as below"
    ;; A context built without a version must degrade, never throw: the
    ;; alternative is a 500 on the taxon picker.
    (is (false? (db.i/at-least-version? {} "20260902160000")))
    (is (false? (db.i/at-least-version? {:schema-version nil}
                                        "20260902160000"))))
  (testing "a malformed version is treated as below, not thrown on"
    ;; Every real producer is a 14-digit column, so this is cheap insurance,
    ;; not a live bug — but parse-long returns nil on a non-numeric string,
    ;; and comparing nil with >= throws rather than degrading.
    (is (false? (db.i/at-least-version? {:schema-version "not-a-version"}
                                        "20260902160000")))))
