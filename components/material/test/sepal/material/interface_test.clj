(ns sepal.material.interface-test
  (:require [clojure.test :as test :refer :all]
            [integrant.core :as ig]
            [malli.core :as m]
            [malli.generator :as mg]
            [matcher-combinators.test :refer [match?]]
            [next.jdbc.sql :as jdbc.sql]
            [sepal.accession.interface :as acc.i]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db*
                                           default-system-fixture]]
            [sepal.error.interface :as err.i]
            [sepal.location.interface :as loc.i]
            [sepal.material.interface :as mat.i]
            [sepal.material.interface.spec :as mat.spec]
            [sepal.organization.interface :as org.i]
            [sepal.taxon.interface :as taxon.i]))

(use-fixtures :once default-system-fixture)

(deftest test-get-by-id
  (let [db *db*]
    (tf/testing "material.i/get-by-id"
      {[::org.i/factory :key/org] {:db db}
       [::taxon.i/factory :key/taxon] {:db db
                                       :organization (ig/ref :key/org)}
       [::acc.i/factory :key/acc] {:db db
                                   :organization (ig/ref :key/org)
                                   :taxon (ig/ref :key/taxon)}
       [::loc.i/factory :key/loc] {:db db
                                   :organization (ig/ref :key/org)}
       [::mat.i/factory :key/mat] {:db db
                                   :organization (ig/ref :key/org)
                                   :accession (ig/ref :key/acc)
                                   :location (ig/ref :key/loc)}}

      (fn [{:keys [mat]}]
        (is (match? mat (mat.i/get-by-id db (:material/id mat))))))))

(deftest test-create
  (let [db *db*]
    (tf/testing "material.i/create!"
      {[::org.i/factory :key/org] {:db db}
       [::taxon.i/factory :key/taxon] {:db db
                                       :organization (ig/ref :key/org)}
       [::acc.i/factory :key/acc] {:db db
                                   :organization (ig/ref :key/org)
                                   :taxon (ig/ref :key/taxon)}
       [::loc.i/factory :key/loc] {:db db
                                   :organization (ig/ref :key/org)}}
      (fn [{:keys [org acc loc]}]
        (let [db *db*
              data (-> (mg/generate mat.spec/CreateMaterial)
                       (assoc :accession-id (:accession/id acc))
                       (assoc :location-id (:location/id loc))
                       (assoc :organization-id (:organization/id org)))
              result (mat.i/create! db data)]
          (is (not (err.i/error? result)) (err.i/data result))
          (is (m/validate mat.spec/Material result))
          (is (match? {:material/organization-id (:organization/id org)
                       :material/accession-id (:accession/id acc)
                       :material/location-id (:location/id loc)}
                      result))
          (jdbc.sql/delete! db :material {:id (:material/id result)}))))))
