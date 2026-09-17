(ns sepal.taxon.parentage-test
  "The cross a hybrid came from, which is not `taxon.parent_id`."
  (:require [clojure.string]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.taxon.interface :as taxon.i]
            [sepal.taxon.interface.name :as taxon.name]))

(use-fixtures :once default-system-fixture)

(defn- genus!
  "A genus with a name no other test shares.

  The suite runs against one database and nothing here deletes what it makes,
  so two files both creating a bare \"Cattleya\" leave two rows that a lookup
  by name cannot tell apart. The suffix keeps each test's taxa its own."
  [nm]
  (taxon.i/create! *db* {:name (str nm "-" (subs (str (random-uuid)) 0 8))
                         :rank :genus}))

(deftest test-a-cross-of-more-than-two-round-trips
  (testing "× Potinara is Brassavola × Cattleya × Laelia × Sophronitis. Four
            parents is why this is a table and not two columns, so four is
            what it has to hold."
    (tf/testing "a nothogenus of four"
      {}
      (fn [_]
        (let [parents (mapv genus! ["Brassavola" "Cattleya" "Laelia" "Sophronitis"])
              hybrid (genus! "Potinara")
              _ (taxon.i/set-parentage!
                  *db* (:taxon/id hybrid)
                  (mapv #(hash-map :parent-taxon-id (:taxon/id %)) parents))
              rows (taxon.i/list-parentage *db* (:taxon/id hybrid))]
          (is (= 4 (count rows)))
          (testing "in the order given, because position is the formula's order"
            (is (= ["Brassavola" "Cattleya" "Laelia" "Sophronitis"]
                   (mapv #(first (clojure.string/split (:parent/name %) #"-")) rows)))
            (is (= [0 1 2 3] (mapv :parentage/position rows))))
          (testing "and the direction defaults to unknown rather than a guess"
            (is (= [:unknown :unknown :unknown :unknown]
                   (mapv :parentage/role rows)))))))))

(deftest test-a-recorded-direction-is-kept
  (tf/testing "seed parent first, pollen second"
    {}
    (fn [_]
      (let [seed (genus! "Laelia")
            pollen (genus! "Cattleya")
            hybrid (genus! "Laeliocattleya")]
        (taxon.i/set-parentage! *db* (:taxon/id hybrid)
                                [{:parent-taxon-id (:taxon/id seed) :role :seed}
                                 {:parent-taxon-id (:taxon/id pollen) :role :pollen}])
        (is (= [:seed :pollen]
               (mapv :parentage/role
                     (taxon.i/list-parentage *db* (:taxon/id hybrid)))))))))

(deftest test-setting-parentage-replaces-rather-than-appends
  ;; The form posts the whole list the way vernacular names do, so a second
  ;; save of two parents must leave two rows, not four.
  (tf/testing "saved twice"
    {}
    (fn [_]
      (let [a (genus! "Acer") b (genus! "Quercus") c (genus! "Betula")
            hybrid (genus! "Notho")]
        (taxon.i/set-parentage! *db* (:taxon/id hybrid)
                                [{:parent-taxon-id (:taxon/id a)}
                                 {:parent-taxon-id (:taxon/id b)}])
        (taxon.i/set-parentage! *db* (:taxon/id hybrid)
                                [{:parent-taxon-id (:taxon/id c)}])
        (let [rows (taxon.i/list-parentage *db* (:taxon/id hybrid))]
          (is (= 1 (count rows)))
          (is (= ["Betula"]
                 (mapv #(first (clojure.string/split (:parent/name %) #"-")) rows))))))))

(deftest test-clearing-parentage-leaves-nothing
  (tf/testing "saved empty"
    {}
    (fn [_]
      (let [a (genus! "Alnus")
            hybrid (genus! "Emptied")]
        (taxon.i/set-parentage! *db* (:taxon/id hybrid)
                                [{:parent-taxon-id (:taxon/id a)}])
        (taxon.i/set-parentage! *db* (:taxon/id hybrid) [])
        (is (empty? (taxon.i/list-parentage *db* (:taxon/id hybrid))))))))

(deftest test-the-ancestry-question-is-answerable
  (testing "what do I hold with Cattleya in its parentage — the reason the
            relation is a table rather than a string in the name"
    (tf/testing "two crosses, one shared parent"
      {}
      (fn [_]
        (let [shared (genus! "Cattleya")
              other (genus! "Sophronitis")
              one (genus! "Sophrocattleya")
              two (genus! "Cattleytonia")]
          (taxon.i/set-parentage! *db* (:taxon/id one)
                                  [{:parent-taxon-id (:taxon/id shared)}
                                   {:parent-taxon-id (:taxon/id other)}])
          (taxon.i/set-parentage! *db* (:taxon/id two)
                                  [{:parent-taxon-id (:taxon/id shared)}])
          (let [found (taxon.i/list-parentage-children *db* (:taxon/id shared))]
            (is (= ["Cattleytonia" "Sophrocattleya"]
                   (sort (mapv #(first (clojure.string/split (:taxon/name %) #"-"))
                               found)))))
          (testing "and a taxon in no cross is found by nothing"
            (is (empty? (taxon.i/list-parentage-children
                          *db* (:taxon/id (genus! "Unrelated")))))))))))

(deftest test-a-taxon-cannot-be-its-own-parent
  ;; No cross means it, and it would loop a renderer that walks the chain.
  (tf/testing "self-reference"
    {}
    (fn [_]
      (let [t (genus! "Selfish")]
        (is (thrown? Exception
                     (taxon.i/set-parentage! *db* (:taxon/id t)
                                             [{:parent-taxon-id (:taxon/id t)}])))))))

(deftest test-the-formula-reads-back-the-conventional-way
  (testing "joined by the hybrid marker, and `\" × \"` is already an upright
            term, so segments italicises the parents and leaves the marker"
    (is (= "Cattleya × Laelia" (taxon.name/formula ["Cattleya" "Laelia"])))
    (is (= "Brassavola × Cattleya × Laelia"
           (taxon.name/formula ["Brassavola" "Cattleya" "Laelia"])))
    (testing "one parent reads as that parent, not as a dangling marker"
      (is (= "Cattleya" (taxon.name/formula ["Cattleya"])))
      (is (= "Cattleya" (taxon.name/formula ["Cattleya" "" nil]))))
    (is (= "" (taxon.name/formula [])))
    (testing "and it feeds segments without further work"
      (is (= [{:text "Cattleya" :role :scientific}
              {:text " × " :role :upright}
              {:text "Laelia" :role :scientific}]
             (taxon.name/segments (taxon.name/formula ["Cattleya" "Laelia"])))))))
