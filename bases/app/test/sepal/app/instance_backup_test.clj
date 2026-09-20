(ns sepal.app.instance-backup-test
  "The entry point the control plane drives. Called here exactly as it is called
  there: a started instance in, a stored backup out."
  (:require [babashka.fs :as fs]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [sepal.app.backup.protocols :as backup.p]
            [sepal.app.instance :as instance]
            [sepal.app.test.system :refer [*garden* *backup-dir* default-system-fixture]]))

(use-fixtures :once default-system-fixture)

(deftest test-backup-stores-through-the-instances-own-store
  (testing "one call, and the garden has a backup where its store puts them"
    (let [before (count (fs/list-dir *backup-dir*))
          result (instance/backup! *garden*)]
      (is (re-matches #"sepal-backup-\d{4}-\d{2}-\d{2}T\d{6}\.zip" (:filename result)))
      (is (pos? (:size-bytes result)))
      (is (some? (:created-at result)))
      (is (= (inc before) (count (fs/list-dir *backup-dir*)))
          "and it is on disk, because this instance's store is the local one")
      (is (some #(= (:filename result) (:filename %))
                (backup.p/list-backups
                  (get-in *garden* [:system :sepal.app.backup/job :backup-store])))
          "and the page would list it"))))
