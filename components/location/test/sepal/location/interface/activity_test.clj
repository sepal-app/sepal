(ns sepal.location.interface.activity-test
  (:require [clojure.test :as test :refer :all]
            [malli.generator :as mg]
            [matcher-combinators.test :refer [match?]]
            [sepal.app.test.system :refer [*db*
                                           default-system-fixture]]
            [sepal.location.interface.activity :as location.activity]
            [sepal.location.interface.spec :as acc.spec]))

(use-fixtures :once default-system-fixture)

(deftest location-activity
  (let [db *db*]
    (testing "activity - location created"
      (let [location (mg/generate acc.spec/Location)
            activity (location.activity/create! db
                                                location.activity/created
                                                1
                                                location)]
        (is (match? {:activity/id int?
                     :activity/type location.activity/created
                     :activity/data {:location-id (:location/id location)
                                     :location-name (:location/name location)
                                     :location-code (:location/code location)}
                     :activity/created-by 1
                     :activity/created-at inst?}
                    activity))))

    (testing "activity - location updated"
      (let [location (mg/generate acc.spec/Location)
            activity (location.activity/create! db
                                                location.activity/updated
                                                1
                                                location)]
        (is (match? {:activity/id int?
                     :activity/type location.activity/updated
                     :activity/data {:location-id (:location/id location)
                                     :location-name (:location/name location)
                                     :location-code (:location/code location)}
                     :activity/created-by 1
                     :activity/created-at inst?}
                    activity))))

    (testing "activity - location deleted"
      (let [location (mg/generate acc.spec/Location)
            activity (location.activity/create! db
                                                location.activity/deleted
                                                1
                                                location)]
        (is (match? {:activity/id int?
                     :activity/type location.activity/deleted
                     :activity/data {:location-id (:location/id location)
                                     :location-name (:location/name location)
                                     :location-code (:location/code location)}
                     :activity/created-by 1
                     :activity/created-at inst?}
                    activity))))))
