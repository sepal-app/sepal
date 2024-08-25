(ns sepal.accession.interface-test
  (:require [clojure.test :as test :refer :all]
            [integrant.core :as ig]
            [malli.core :as m]
            [malli.generator :as mg]
            [matcher-combinators.test :refer [match?]]
            [next.jdbc.sql :as jdbc.sql]
            [sepal.accession.interface :as acc.i]
            [sepal.accession.interface.spec :as acc.spec]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db*
                                           default-system-fixture]]
            [sepal.error.interface :as err.i]
            [sepal.organization.interface :as org.i]
            [sepal.taxon.interface :as taxon.i]))

(use-fixtures :once default-system-fixture)

(deftest test-create
  (let [db *db*]
    (tf/testing "create!"
      {[::org.i/factory :key/org] {:db db}
       [::taxon.i/factory :key/taxon] {:db db
                                       :organization (ig/ref :key/org)}}
      (fn [{:keys [org taxon]}]
        (let [db *db*
              data (-> (mg/generate acc.spec/CreateAccession)
                       (assoc :taxon-id (:taxon/id taxon))
                       (assoc :organization-id (:organization/id org)))
              result (acc.i/create! db data)]
          (is (not (err.i/error? result)) (err.i/data result))
          (is (m/validate acc.spec/Accession result))
          (is (match? {:accession/organization-id (:organization/id org)
                       :accession/taxon-id (:taxon/id taxon)}
                      result))
          (jdbc.sql/delete! db :accession {:id (:accession/id result)}))))))

(deftest test-update
  (let [db *db*]
    (tf/testing "update!"
      {[::org.i/factory :key/org] {:db db}
       [::taxon.i/factory :key/taxon] {:db db
                                       :organization (ig/ref :key/org)}
       [::acc.i/factory :key/acc] {:db db
                                   :organization (ig/ref :key/org)
                                   :taxon (ig/ref :key/taxon)}}
      (fn [{:keys [acc org taxon]}]
        (let [acc-code (mg/generate acc.spec/code)
              result (acc.i/update! db
                                    (:accession/id acc)
                                    {:code acc-code})]
          (is (not (err.i/error? result)) (err.i/data result))
          (is (m/validate acc.spec/Accession result))
          (is (match? {:accession/organization-id (:organization/id org)
                       :accession/taxon-id (:taxon/id taxon)}
                      result)))))))
