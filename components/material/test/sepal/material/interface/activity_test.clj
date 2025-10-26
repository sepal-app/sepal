(ns sepal.material.interface.activity-test
  (:require [clojure.test :as test :refer :all]
            [malli.generator :as mg]
            [matcher-combinators.test :refer [match?]]
            [sepal.app.test.system :refer [*db*
                                           default-system-fixture]]
            [sepal.material.interface.activity :as material.activity]
            [sepal.material.interface.spec :as acc.spec]))

(use-fixtures :once default-system-fixture)

(deftest material-activity
  (let [db *db*]
    (testing "activity - material created"
      (let [material (mg/generate acc.spec/Material)
            activity (material.activity/create! db
                                                material.activity/created
                                                1
                                                material)]
        (is (match? {:activity/id int?
                     :activity/type material.activity/created
                     :activity/data {:material-id (:material/id material)
                                     :material-code (:material/code material)
                                     :accession-id (:material/accession-id material)
                                     :location-id (:material/location-id material)}
                     :activity/created-by 1
                     :activity/created-at inst?}
                    activity))))

    (testing "activity - material updated"
      (let [material (mg/generate acc.spec/Material)
            activity (material.activity/create! db
                                                material.activity/updated
                                                1
                                                material)]
        (is (match? {:activity/id int?
                     :activity/type material.activity/updated
                     :activity/data {:material-id (:material/id material)
                                     :material-code (:material/code material)
                                     :accession-id (:material/accession-id material)
                                     :location-id (:material/location-id material)}
                     :activity/created-by 1
                     :activity/created-at inst?}
                    activity))))

    (testing "activity - material deleted"
      (let [material (mg/generate acc.spec/Material)
            activity (material.activity/create! db
                                                material.activity/deleted
                                                1
                                                material)]
        (is (match? {:activity/id int?
                     :activity/type material.activity/deleted
                     :activity/data {:material-id (:material/id material)
                                     :material-code (:material/code material)
                                     :accession-id (:material/accession-id material)
                                     :location-id (:material/location-id material)}
                     :activity/created-by 1
                     :activity/created-at inst?}
                    activity))))))
