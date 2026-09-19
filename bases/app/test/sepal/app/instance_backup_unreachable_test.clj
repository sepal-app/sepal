(ns sepal.app.instance-backup-unreachable-test
  "backup!'s stated contract is that it throws rather than swallow a store
  failure. This is the test that would fail if a future change caught that
  exception and returned something instead.

  The fake store here refuses up front, in put-backup, before the creation fn
  ever runs — a different failure from one where the backup was created but
  the store could not accept it. Only the first is exercised here; ex-data is
  checked so this cannot pass for an unrelated ExceptionInfo."
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [sepal.app.backup.fake-store :as fake-store]
            [sepal.app.instance :as instance]
            [sepal.app.test.system :refer [*garden* unreachable-backup-store-system-fixture]]))

(use-fixtures :once unreachable-backup-store-system-fixture)

(deftest test-backup-throws-rather-than-swallow-a-store-refusal
  (testing "the caller finds out the store refused up front, instead of getting a result it cannot trust"
    (is (= ::fake-store/unreachable
           (:type (ex-data (try
                             (instance/backup! *garden*)
                             (catch Exception e e))))))))
