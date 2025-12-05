(ns sepal.accession.interface.activity-test
  (:require [clojure.test :as test :refer :all]
            [malli.generator :as mg]
            [matcher-combinators.test :refer [match?]]
            [next.jdbc.sql :as jdbc.sql]
            [sepal.accession.interface.activity :as accession.activity]
            [sepal.accession.interface.spec :as acc.spec]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db*
                                           default-system-fixture]]
            [sepal.user.interface :as user.i]))

(use-fixtures :once default-system-fixture)

(deftest accession-activity
  (tf/testing "accession activity tests"
    {[::user.i/factory :key/user] {:db *db*}}
    (fn [{:keys [user]}]
      (let [db *db*
            user-id (:user/id user)]
        (try
          (testing "activity - accession created"
            (let [accession (mg/generate acc.spec/Accession)
                  activity (accession.activity/create! db
                                                       accession.activity/created
                                                       user-id
                                                       accession)]
              (is (match? {:activity/id int?
                           :activity/type accession.activity/created
                           :activity/data {:accession-id (:accession/id accession)
                                           :accession-code (:accession/code accession)
                                           :taxon-id (:accession/taxon-id accession)}
                           :activity/created-by user-id
                           :activity/created-at inst?}
                          activity))))

          (testing "activity - accession updated"
            (let [accession (mg/generate acc.spec/Accession)
                  activity (accession.activity/create! db
                                                       accession.activity/updated
                                                       user-id
                                                       accession)]
              (is (match? {:activity/id int?
                           :activity/type accession.activity/updated
                           :activity/data {:accession-id (:accession/id accession)
                                           :accession-code (:accession/code accession)
                                           :taxon-id (:accession/taxon-id accession)}
                           :activity/created-by user-id
                           :activity/created-at inst?}
                          activity))))

          (testing "activity - accession deleted"
            (let [accession (mg/generate acc.spec/Accession)
                  activity (accession.activity/create! db
                                                       accession.activity/deleted
                                                       user-id
                                                       accession)]
              (is (match? {:activity/id int?
                           :activity/type accession.activity/deleted
                           :activity/data {:accession-id (:accession/id accession)
                                           :accession-code (:accession/code accession)
                                           :taxon-id (:accession/taxon-id accession)}
                           :activity/created-by user-id
                           :activity/created-at inst?}
                          activity))))
          (finally
            ;; Clean up activity records before user fixture cleanup
            (jdbc.sql/delete! db :activity {:created_by user-id})))))))
