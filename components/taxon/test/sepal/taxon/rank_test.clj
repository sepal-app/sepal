(ns sepal.taxon.rank-test
  (:require [clojure.set]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [next.jdbc :as jdbc]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.error.interface :as err.i]
            [sepal.taxon.interface :as taxon.i]
            [sepal.taxon.interface.spec :as taxon.spec]))

(use-fixtures :once default-system-fixture)

(defn- table-ranks []
  (->> (jdbc/execute! *db* ["select name from taxon_rank order by name"])
       (map :taxon-rank/name)
       set))

(defn- enum-ranks []
  (->> taxon.spec/rank rest (map name) set))

(deftest test-enum-and-table-agree
  (testing "every rank in taxon_rank is in the Malli enum"
    ;; Drift this way is worse than a missing dropdown option: store/core.clj:26
    ;; coerces the result of get-by-id through spec/Taxon, so a row carrying a
    ;; rank the enum lacks cannot be read at all.
    (is (empty? (clojure.set/difference (table-ranks) (enum-ranks)))))

  (testing "every rank in the Malli enum is in taxon_rank"
    ;; Drift this way makes the form offer a rank the database will refuse.
    (is (empty? (clojure.set/difference (enum-ranks) (table-ranks)))))

  (testing "there are 36 of them"
    (is (= 36 (count (enum-ranks))))))

(deftest test-cultivar-and-grex-round-trip
  (testing "a cultivar-rank taxon saves and reads back"
    (let [result (taxon.i/create! *db* {:name "Acer palmatum 'Sango-kaku'"
                                        :rank :cultivar})]
      (is (not (err.i/error? result)) (err.i/data result))
      (is (= :cultivar (:taxon/rank (taxon.i/get-by-id *db* (:taxon/id result)))))))

  (testing "a grex-rank taxon saves and reads back"
    (let [result (taxon.i/create! *db* {:name "Cattleya Chocolate Drop"
                                        :rank :grex})]
      (is (not (err.i/error? result)) (err.i/data result))
      (is (= :grex (:taxon/rank (taxon.i/get-by-id *db* (:taxon/id result))))))))

(deftest test-a-cultivar-hangs-off-a-species-or-a-genus
  (testing "parent_id needs no change: a cultivar can hang off either rank"
    ;; The plan claims the existing parent_id handles ICNCP parentage without
    ;; change. Assert it rather than assume it.
    (let [species (taxon.i/create! *db* {:name "Acer palmatum" :rank :species})
          genus (taxon.i/create! *db* {:name "Hosta" :rank :genus})
          under-species (taxon.i/create! *db* {:name "Acer palmatum 'Sango-kaku'"
                                               :rank :cultivar
                                               :parent-id (:taxon/id species)})
          under-genus (taxon.i/create! *db* {:name "Hosta 'Sum and Substance'"
                                             :rank :cultivar
                                             :parent-id (:taxon/id genus)})]
      (is (not (err.i/error? under-species)) (err.i/data under-species))
      (is (not (err.i/error? under-genus)) (err.i/data under-genus))
      (is (= (:taxon/id species) (:taxon/parent-id under-species)))
      (is (= (:taxon/id genus) (:taxon/parent-id under-genus))))))

(deftest test-an-unknown-rank-is-a-field-error-not-a-500
  (testing "the Malli enum, not the foreign key, is what reports a bad rank"
    ;; form.clj:39 types :rank as [:string {:min 1}], so the enum is checked at
    ;; store/core.clj:14 by m/coerce. That throw carries :explain, which
    ;; error.i/humanize turns into a per-field message. A foreign-key violation
    ;; would not: SQLiteException has no ex-data, so humanize returns nil.
    ;;
    ;; store/core.clj throws rather than returning an error map (see commit
    ;; 8869acd); only the route layer (routes/taxon/create.clj) catches and
    ;; converts via err.i/ex->error, so this catches the same way to observe
    ;; what that route sees.
    (let [result (try
                   (taxon.i/create! *db* {:name "Bogus" :rank :notarank})
                   (catch Exception ex
                     (err.i/ex->error ex)))]
      (is (err.i/error? result))
      (is (contains? (err.i/humanize result) :rank)))))
