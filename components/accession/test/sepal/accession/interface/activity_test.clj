(ns sepal.accession.interface.activity-test
  (:require [clojure.test :as test :refer :all]
            [malli.generator :as mg]
            [matcher-combinators.test :refer [match?]]
            [sepal.accession.interface.activity :as accession.activity]
            [sepal.accession.interface.spec :as acc.spec]
            [sepal.app.test.system :refer [*db*
                                           default-system-fixture]]))

(use-fixtures :once default-system-fixture)

(deftest accession-activity
  (let [db *db*]
    (testing "activity - accession created"
      (let [accession (mg/generate acc.spec/Accession)
            activity (accession.activity/create! db
                                                 accession.activity/created
                                                 1
                                                 accession)]
        (is (match? {:activity/id int?
                     :activity/type accession.activity/created
                     :activity/data {:accession-id (:accession/id accession)
                                     :accession-code (:accession/code accession)
                                     :taxon-id (:accession/taxon-id accession)}
                     :activity/created-by 1
                     :activity/created-at inst?}
                    activity))))

    (testing "activity - accession updated"
      (let [accession (mg/generate acc.spec/Accession)
            activity (accession.activity/create! db
                                                 accession.activity/updated
                                                 1
                                                 accession)]
        (is (match? {:activity/id int?
                     :activity/type accession.activity/updated
                     :activity/data {:accession-id (:accession/id accession)
                                     :accession-code (:accession/code accession)
                                     :taxon-id (:accession/taxon-id accession)}
                     :activity/created-by 1
                     :activity/created-at inst?}
                    activity))))

    (testing "activity - accession deleted"
      (let [accession (mg/generate acc.spec/Accession)
            activity (accession.activity/create! db
                                                 accession.activity/deleted
                                                 1
                                                 accession)]
        (is (match? {:activity/id int?
                     :activity/type accession.activity/deleted
                     :activity/data {:accession-id (:accession/id accession)
                                     :accession-code (:accession/code accession)
                                     :taxon-id (:accession/taxon-id accession)}
                     :activity/created-by 1
                     :activity/created-at inst?}
                    activity))))))
