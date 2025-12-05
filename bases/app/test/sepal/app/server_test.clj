(ns sepal.app.server-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.database.interface :as db.i]))

(use-fixtures :once default-system-fixture)

(deftest sqlite-pragma-test
  (testing "journal_mode is WAL"
    (is (= [{:journal-mode "wal"}]
           (db.i/execute! *db* ["PRAGMA journal_mode"]))))

  (testing "foreign_keys is enabled"
    (is (= [{:foreign-keys 1}]
           (db.i/execute! *db* ["PRAGMA foreign_keys"])))))
