(ns sepal.observation.interface.search-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [integrant.core :as ig]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.database.interface :as db.i]
            [sepal.location.interface :as location.i]
            [sepal.observation.interface :as observation.i]
            [sepal.observation.interface.search]
            [sepal.search.interface :as search.i]))

(use-fixtures :once default-system-fixture)

(def ^:private base-stmt
  "The location join the index and export statements carry."
  {:select [:o.id]
   :from [[:observation :o]]
   :left-join [[:location :l] [:and [:= :o.resource_type "location"] [:= :l.id :o.resource_id]]]})

(defn- observe! [location]
  (observation.i/create! *db* {:resource-type "location"
                               :resource-id (:location/id location)
                               :type "general"
                               :observed-on "2026-01-01"}))

(deftest test-location-filter-covers-sub-locations
  (tf/testing "a filter on the orchard finds observations on its rows"
    {[::location.i/factory :key/orchard] {:db *db* :data {:code "OSRCH" :name "OSRCH orchard"}}
     [::location.i/factory :key/row] {:db *db* :parent (ig/ref :key/orchard)
                                      :data {:code "OSRCH-R1" :name "OSRCH row"}}
     [::location.i/factory :key/other] {:db *db* :data {:code "OSRCHX" :name "OSRCH other"}}}
    (fn [{:keys [orchard row other]}]
      (let [on-row (observe! row)
            on-other (observe! other)
            found (fn [q]
                    (->> (search.i/compile-query :observation (search.i/parse q) base-stmt)
                         (db.i/execute! *db*)
                         (mapv :observation/id)))
            in-row (:observation/id on-row)
            elsewhere (:observation/id on-other)]
        (try
          (is (some #{in-row} (found "location:\"OSRCH orchard\"")) "parent name")
          (is (some #{in-row} (found "location:\"OSRCH row\"")) "leaf name")
          (is (= 1 (count (filter #{in-row} (found "location:OSRCH"))))
              "matching the orchard and the row returns it once")
          (let [negated (found "-location:\"OSRCH orchard\"")]
            (is (not-any? #{in-row} negated) "negated excludes the subtree")
            (is (some #{elsewhere} negated) "and keeps the rest"))
          (finally
            (observation.i/delete! *db* in-row)
            (observation.i/delete! *db* elsewhere)))))))
