(ns sepal.app.instance-backup-unreachable-test
  "backup!'s stated contract is that it throws rather than swallow a store
  failure. This is the test that would fail if a future change caught that
  exception and returned something instead."
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [sepal.app.instance :as instance]
            [sepal.app.test.system :refer [*garden* unreachable-backup-store-system-fixture]]))

(use-fixtures :once unreachable-backup-store-system-fixture)

(deftest test-backup-throws-rather-than-swallow-a-store-failure
  (testing "the caller finds out, instead of getting a result it cannot trust"
    (is (thrown? clojure.lang.ExceptionInfo (instance/backup! *garden*)))))
