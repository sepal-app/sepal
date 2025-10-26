(ns sepal.taxon.interface.activity-test
  (:require [clojure.test :as test :refer :all]
            [malli.generator :as mg]
            [matcher-combinators.test :refer [match?]]
            [sepal.app.test.system :refer [*db*
                                           default-system-fixture]]
            [sepal.taxon.interface.activity :as taxon.activity]
            [sepal.taxon.interface.spec :as acc.spec]))

(use-fixtures :once default-system-fixture)

(deftest taxon-activity
  (let [db *db*]
    (testing "activity - taxon created"
      (let [taxon (mg/generate acc.spec/Taxon)
            activity (taxon.activity/create! db
                                             taxon.activity/created
                                             1
                                             taxon)]
        (is (match? {:activity/id int?
                     :activity/type taxon.activity/created
                     :activity/data {:taxon-id (:taxon/id taxon)
                                     :taxon-name (:taxon/name taxon)
                                     :taxon-author (:taxon/author taxon)
                                     :taxon-rank (:taxon/rank taxon)}
                     :activity/created-by 1
                     :activity/created-at inst?}
                    activity))))

    (testing "activity - taxon updated"
      (let [taxon (mg/generate acc.spec/Taxon)
            activity (taxon.activity/create! db
                                             taxon.activity/updated
                                             1
                                             taxon)]
        (is (match? {:activity/id int?
                     :activity/type taxon.activity/updated
                     :activity/data {:taxon-id (:taxon/id taxon)
                                     :taxon-name (:taxon/name taxon)
                                     :taxon-author (:taxon/author taxon)
                                     :taxon-rank (:taxon/rank taxon)}
                     :activity/created-by 1
                     :activity/created-at inst?}
                    activity))))

    (testing "activity - taxon deleted"
      (let [taxon (mg/generate acc.spec/Taxon)
            activity (taxon.activity/create! db
                                             taxon.activity/deleted
                                             1
                                             taxon)]
        (is (match? {:activity/id int?
                     :activity/type taxon.activity/deleted
                     :activity/data {:taxon-id (:taxon/id taxon)
                                     :taxon-name (:taxon/name taxon)
                                     :taxon-author (:taxon/author taxon)
                                     :taxon-rank (:taxon/rank taxon)}
                     :activity/created-by 1
                     :activity/created-at inst?}
                    activity))))))
