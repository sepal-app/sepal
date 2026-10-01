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
      ;; Fixed names and codes: the bare-word searches below look for one
      ;; record's word, and a generated one can be short enough to match the
      ;; other record too.
      {[::taxon.i/factory :key/taxon-a] {:db db :name "Swietenia macrophylla"}
       [::taxon.i/factory :key/taxon-b] {:db db :name "Cedrela odorata"}
       [::accession.i/factory :key/acc-a] {:db db
                                           :taxon (ig/ref :key/taxon-a)
                                           :data {:code "2026.0101"}}
       [::accession.i/factory :key/acc-b] {:db db
                                           :taxon (ig/ref :key/taxon-b)
                                           :data {:code "2026.0202"}}
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

(deftest test-location-filters-cover-sub-locations
  (tf/testing "a filter on the orchard finds batches on its rows"
    {[::location.i/factory :key/orchard] {:db *db* :data {:code "PSRCH" :name "PSRCH orchard"}}
     [::location.i/factory :key/row] {:db *db* :parent (ig/ref :key/orchard)
                                      :data {:code "PSRCH-R1" :name "PSRCH row"}}
     [::location.i/factory :key/other] {:db *db* :data {:code "PSRCHX" :name "PSRCH other"}}
     [::taxon.i/factory :key/taxon] {:db *db*}
     [::accession.i/factory :key/accession] {:db *db* :taxon (ig/ref :key/taxon)}
     [::propagation.i/factory :key/p-row] {:db *db* :accession (ig/ref :key/accession)}
     [::propagation.i/factory :key/p-other] {:db *db* :accession (ig/ref :key/accession)}}
    (fn [{:keys [orchard row other p-row p-other]}]
      (propagation.i/update! *db* (:propagation/id p-row) {:location-id (:location/id row)})
      (propagation.i/update! *db* (:propagation/id p-other) {:location-id (:location/id other)})
      (let [found (fn [q]
                    (->> (search.i/compile-query :propagation (search.i/parse q)
                                                 {:select [:p.id] :from [[:propagation :p]]})
                         (db.i/execute! *db*)
                         (mapv :propagation/id)))
            in-row (:propagation/id p-row)
            elsewhere (:propagation/id p-other)]
        (try
          (is (some #{in-row} (found (str "location.id:" (:location/id orchard)))) "parent id")
          (is (some #{in-row} (found (str "location.id:" (:location/id row)))) "leaf id")
          (is (= 1 (count (filter #{in-row} (found "location:PSRCH"))))
              "matching the orchard and the row returns it once")
          (let [negated (found (str "-location.id:" (:location/id orchard)))]
            (is (not-any? #{in-row} negated) "negated excludes the subtree")
            (is (some #{elsewhere} negated) "and keeps the rest"))
          (is (some #{in-row} (found "location:\"PSRCH orchard\"")) "parent name")
          (finally
            ;; The locations' halts don't wait for these, so let go of them.
            (doseq [p [p-row p-other]]
              (propagation.i/update! *db* (:propagation/id p) {:location-id nil}))))))))
