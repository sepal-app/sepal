(ns sepal.propagation.interface.search-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is use-fixtures]]
            [integrant.core :as ig]
            [next.jdbc.sql :as jdbc.sql]
            [sepal.accession.interface :as accession.i]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.database.interface :as db.i]
            [sepal.location.interface :as location.i]
            [sepal.propagation.interface :as propagation.i]
            [sepal.propagation.interface.search]
            [sepal.search.interface :as search.i]
            [sepal.taxon.interface :as taxon.i]))

(use-fixtures :once default-system-fixture)

(deftest test-propagation-filters
  (let [db *db*]
    (tf/testing "filters narrow the nursery list"
      {[::taxon.i/factory :key/taxon-a] {:db db}
       [::taxon.i/factory :key/taxon-b] {:db db}
       [::accession.i/factory :key/acc-a] {:db db
                                           :taxon (ig/ref :key/taxon-a)}
       [::accession.i/factory :key/acc-b] {:db db
                                           :taxon (ig/ref :key/taxon-b)}
       [::location.i/factory :key/loc-a] {:db db}
       [::location.i/factory :key/loc-b] {:db db}}
      (fn [{:keys [taxon-a acc-a acc-b loc-a loc-b]}]
        (let [p1 (propagation.i/create!
                   db {:type :cutting
                       :parent-accession-id (:accession/id acc-a)
                       :location-id (:location/id loc-a)
                       :propagated-on "2026-03-01"})
              p2 (propagation.i/create!
                   db {:type :seed
                       :parent-accession-id (:accession/id acc-b)
                       :location-id (:location/id loc-b)
                       :propagated-on "2026-05-01"
                       :status :complete})
              p3 (propagation.i/create!
                   db {:type :cutting
                       :parent-accession-id (:accession/id acc-b)
                       :location-id (:location/id loc-a)
                       :propagated-on "2026-04-15"})]
          (try
            (let [found (fn [q]
                          (->> (search.i/compile-query
                                 :propagation
                                 (search.i/parse q)
                                 {:select [:p.*] :from [[:propagation :p]]})
                               (db.i/execute! db)
                               (map :propagation/id)
                               set))
                  id1 (:propagation/id p1)
                  id2 (:propagation/id p2)
                  id3 (:propagation/id p3)]
              (is (= #{id1 id3} (found "status:active"))
                  "the nursery list")
              (is (= #{id1 id3} (found "type:cutting"))
                  "one method")
              (is (= #{id1 id3} (found (str "location.id:" (:location/id loc-a))))
                  "one bench")
              (is (= #{id2 id3} (found "propagated:>2026-04-01"))
                  "a date range")
              (is (= #{id2 id3} (found (:accession/code acc-b)))
                  "a bare word finds the parent accession")
              (is (= #{id1}
                     (found (first (str/split (:taxon/name taxon-a) #"\s+"))))
                  "a bare word finds the parent's taxon")
              (is (empty? (found "status:failed")))
              (is (empty? (found "nothingmatchesthis"))))
            (finally
              (doseq [p [p1 p2 p3]]
                (jdbc.sql/delete! db :propagation {:id (:propagation/id p)})))))))))
