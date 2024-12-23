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
            [sepal.taxon.interface :as taxon.i]))

(use-fixtures :once default-system-fixture)

(deftest test-create
  (let [db *db*]
    (tf/testing "accession.i/create!"
      {[::taxon.i/factory :key/taxon] {:db db}}
      (fn [{:keys [taxon]}]
        (let [db *db*
              data (-> (mg/generate acc.spec/CreateAccession)
                       (assoc :taxon-id (:taxon/id taxon)))
              result (acc.i/create! db data)]
          (is (not (err.i/error? result)) (err.i/data result))
          (is (m/validate acc.spec/Accession result))
          (is (match? {:accession/taxon-id (:taxon/id taxon)}
                      result))
          (jdbc.sql/delete! db :accession {:id (:accession/id result)}))))))

(deftest test-update
  (let [db *db*]
    (tf/testing "update!"
      {[::taxon.i/factory :key/taxon] {:db db}
       [::acc.i/factory :key/acc] {:db db
                                   :taxon (ig/ref :key/taxon)}}
      (fn [{:keys [acc taxon]}]
        (let [acc-code (mg/generate acc.spec/code)
              result (acc.i/update! db
                                    (:accession/id acc)
                                    {:code acc-code})]
          (is (not (err.i/error? result)) (err.i/data result))
          (is (m/validate acc.spec/Accession result))
          (is (match? {:accession/taxon-id (:taxon/id taxon)}
                      result)))))))
