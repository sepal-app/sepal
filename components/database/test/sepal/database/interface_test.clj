(ns sepal.database.interface-test
  (:require [clojure.test :as test :refer :all]
            [sepal.app.test.system :refer [*db*
                                           default-system-fixture]]
            [sepal.database.interface :as db.i]))

(use-fixtures :once default-system-fixture)

(deftest decoder-test
  (is (= [{:x 1}] (db.i/execute! *db* ["select 1 as x"])))
  (is (= [{:x "{}"}] (db.i/execute! *db* ["select json('{}')  as x"]))))
