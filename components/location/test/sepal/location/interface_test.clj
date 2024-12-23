(ns sepal.location.interface-test
  (:require [clojure.test :as test :refer :all]
            [malli.core :as m]
            [malli.generator :as mg]
            [matcher-combinators.test :refer [match?]]
            [next.jdbc.sql :as jdbc.sql]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db*
                                           default-system-fixture]]
            [sepal.error.interface :as err.i]
            [sepal.location.interface :as loc.i]
            [sepal.location.interface.spec :as loc.spec]))

(use-fixtures :once default-system-fixture)

(deftest test-create
  (tf/testing "loc.i/create!"
    (let [db *db*
          data (mg/generate loc.spec/CreateLocation)
          result (loc.i/create! db data)]
      (is (not (err.i/error? result)) (err.i/data result))
      (is (m/validate loc.spec/Location result))
      #_(is (match? {:location/organization-id (:organization/id org)}
                    result))
      (jdbc.sql/delete! db :location {:id (:location/id result)}))))

(deftest test-update
  (let [db *db*]
    (tf/testing "loc.i/update!"
      {[::loc.i/factory :key/loc] {:db db}}
      (fn [{:keys [loc]}]
        (let [code (mg/generate loc.spec/code)
              result (loc.i/update! db
                                    (:location/id loc)
                                    {:code code})]
          (is (not (err.i/error? result)) (err.i/data result))
          (is (m/validate loc.spec/Location result))
          (is (match? {:location/code code}
                      result)))))))
