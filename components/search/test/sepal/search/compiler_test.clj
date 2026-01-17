(ns sepal.search.compiler-test
  (:require [clojure.test :refer [deftest is testing]]
            [matcher-combinators.test :refer [match?]]
            [sepal.search.compiler :as compiler]))

;; =============================================================================
;; Helper function tests
;; =============================================================================

(deftest column->id-column-test
  (testing "derives ID column from qualified column"
    (is (= :t.id (#'compiler/column->id-column :t.name)))
    (is (= :a.id (#'compiler/column->id-column :a.code)))
    (is (= :location.id (#'compiler/column->id-column :location.description))))

  (testing "unqualified column returns :id"
    (is (= :id (#'compiler/column->id-column :name)))))

;; Test field definitions (simulating what resources would provide)
(def test-fields
  {:code     {:column :m.code :type :text :label "Code"}
   :type     {:column :m.type :type :enum :values [:seed :plant] :label "Type"}
   :status   {:column :m.status :type :enum :values [:alive :dead] :label "Status"}
   :id       {:column :m.id :type :id :label "ID"}
   :private  {:column :m.private :type :boolean :label "Private"}
   :created  {:column :m.created_at :type :date :label "Created"}
   :quantity {:column :m.quantity :type :number :label "Quantity"}
   :taxon    {:column :t.name
              :type :fts
              :fts-table :taxon_fts
              :label "Taxon"
              :joins [[:accession :a] [:= :a.id :m.accession_id]
                      [:taxon :t] [:= :t.id :a.taxon_id]]}
   :location {:column :l.code
              :type :text
              :label "Location"
              :joins [[:location :l] [:= :l.id :m.location_id]]}
   ;; Count type for counting related records
   :orders {:column [:= :order.material_id :m.id]
            :type :count
            :fts-table :order  ; table to count
            :label "Orders"}})

(def base-stmt {:select [:*] :from [[:material :m]]})

(deftest compile-empty-test
  (testing "empty AST returns base statement"
    (is (= base-stmt
           (compiler/compile-query test-fields {:terms [] :filters []} base-stmt)))))

(deftest compile-text-filter-test
  (testing "text field uses LIKE by default"
    (is (match? {:select [:*]
                 :from [[:material :m]]
                 :where [:like :m.code "%test%"]}
                (compiler/compile-query
                  test-fields
                  {:terms [] :filters [{:field "code" :value "test" :negated false}]}
                  base-stmt))))

  (testing "text field with = operator uses exact match"
    (is (match? {:where [:= :m.code "GH"]}
                (compiler/compile-query
                  test-fields
                  {:terms [] :filters [{:field "code" :op "=" :value "GH" :negated false}]}
                  base-stmt))))

  (testing "exact match with negation"
    (is (match? {:where [:not [:= :m.code "GH"]]}
                (compiler/compile-query
                  test-fields
                  {:terms [] :filters [{:field "code" :op "=" :value "GH" :negated true}]}
                  base-stmt)))))

(deftest compile-enum-filter-test
  (testing "enum field uses exact match with string"
    (is (match? {:where [:= :m.type "seed"]}
                (compiler/compile-query
                  test-fields
                  {:terms [] :filters [{:field "type" :value "seed" :negated false}]}
                  base-stmt))))

  (testing "enum multi-value uses IN with strings"
    (is (match? {:where [:in :m.type ["seed" "plant"]]}
                (compiler/compile-query
                  test-fields
                  {:terms [] :filters [{:field "type" :values ["seed" "plant"] :negated false}]}
                  base-stmt)))))

(deftest compile-id-filter-test
  (testing "id field uses exact match with integer"
    (is (match? {:where [:= :m.id 123]}
                (compiler/compile-query
                  test-fields
                  {:terms [] :filters [{:field "id" :value "123" :negated false}]}
                  base-stmt)))))

(deftest compile-fts-filter-test
  (testing "fts field uses subquery with MATCH on ID column"
    (is (match? {:where [:in :t.id {:select [:rowid]
                                    :from [:taxon_fts]
                                    :where [:match :taxon_fts "Quercus*"]}]}
                (compiler/compile-query
                  test-fields
                  {:terms [] :filters [{:field "taxon" :value "Quercus" :negated false}]}
                  base-stmt))))

  (testing "fts subquery correlates with joined table via ID column"
    ;; This tests the fix for the FTS correlation bug:
    ;; The subquery must use the ID column (t.id) not the text column (t.name)
    ;; so that FTS results are properly correlated with the joined taxon table
    (let [result (compiler/compile-query
                   test-fields
                   {:terms [] :filters [{:field "taxon" :value "Quercus" :negated false}]}
                   base-stmt)]
      ;; Should have joins to reach the taxon table
      (is (match? {:join [[:accession :a] [:= :a.id :m.accession_id]
                          [:taxon :t] [:= :t.id :a.taxon_id]]}
                  result))
      ;; The WHERE clause should use t.id (not t.name) to correlate with the join
      (is (match? {:where [:in :t.id map?]}
                  result))))

  (testing "fts with negation wraps subquery in NOT"
    (is (match? {:where [:not [:in :t.id {:select [:rowid]
                                          :from [:taxon_fts]
                                          :where [:match :taxon_fts "Quercus*"]}]]}
                (compiler/compile-query
                  test-fields
                  {:terms [] :filters [{:field "taxon" :value "Quercus" :negated true}]}
                  base-stmt)))))

(deftest compile-boolean-filter-test
  (testing "boolean field with no value"
    (is (match? {:where [:= :m.private true]}
                (compiler/compile-query
                  test-fields
                  {:terms [] :filters [{:field "private" :negated false}]}
                  base-stmt)))))

(deftest compile-count-filter-test
  (testing "count >0 optimized to EXISTS"
    (is (match? {:where [:exists {:select [1]
                                  :from [:order]
                                  :where [:= :order.material_id :m.id]
                                  :limit 1}]}
                (compiler/compile-query
                  test-fields
                  {:terms [] :filters [{:field "orders" :op ">" :value "0" :negated false}]}
                  base-stmt))))

  (testing "count =0 optimized to NOT EXISTS"
    (is (match? {:where [:not [:exists {:select [1]
                                        :from [:order]
                                        :where [:= :order.material_id :m.id]
                                        :limit 1}]]}
                (compiler/compile-query
                  test-fields
                  {:terms [] :filters [{:field "orders" :value "0" :negated false}]}
                  base-stmt))))

  (testing "count >=1 optimized to EXISTS"
    (is (match? {:where [:exists {:select [1]
                                  :from [:order]
                                  :where [:= :order.material_id :m.id]
                                  :limit 1}]}
                (compiler/compile-query
                  test-fields
                  {:terms [] :filters [{:field "orders" :op ">=" :value "1" :negated false}]}
                  base-stmt))))

  (testing "count >5 uses COUNT subquery"
    (is (match? {:where [:> {:select [[[:count :*]]]
                             :from [:order]
                             :where [:= :order.material_id :m.id]}
                         5]}
                (compiler/compile-query
                  test-fields
                  {:terms [] :filters [{:field "orders" :op ">" :value "5" :negated false}]}
                  base-stmt))))

  (testing "negated count wraps in NOT"
    (is (match? {:where [:not [:exists {:select [1]
                                        :from [:order]
                                        :where [:= :order.material_id :m.id]
                                        :limit 1}]]}
                (compiler/compile-query
                  test-fields
                  {:terms [] :filters [{:field "orders" :op ">" :value "0" :negated true}]}
                  base-stmt)))))

(deftest compile-date-filter-test
  (testing "date with greater than operator"
    (is (match? {:where [:> :m.created_at "2024-01-01"]}
                (compiler/compile-query
                  test-fields
                  {:terms [] :filters [{:field "created" :op ">" :value "2024-01-01" :negated false}]}
                  base-stmt))))

  (testing "date with less than operator"
    (is (match? {:where [:< :m.created_at "2024-06-01"]}
                (compiler/compile-query
                  test-fields
                  {:terms [] :filters [{:field "created" :op "<" :value "2024-06-01" :negated false}]}
                  base-stmt))))

  (testing "date with greater than or equal operator"
    (is (match? {:where [:>= :m.created_at "2024-01-01"]}
                (compiler/compile-query
                  test-fields
                  {:terms [] :filters [{:field "created" :op ">=" :value "2024-01-01" :negated false}]}
                  base-stmt))))

  (testing "date with less than or equal operator"
    (is (match? {:where [:<= :m.created_at "2024-12-31"]}
                (compiler/compile-query
                  test-fields
                  {:terms [] :filters [{:field "created" :op "<=" :value "2024-12-31" :negated false}]}
                  base-stmt))))

  (testing "date without operator uses exact match"
    (is (match? {:where [:= :m.created_at "2024-01-01"]}
                (compiler/compile-query
                  test-fields
                  {:terms [] :filters [{:field "created" :value "2024-01-01" :negated false}]}
                  base-stmt))))

  (testing "date comparison with negation"
    (is (match? {:where [:not [:> :m.created_at "2024-01-01"]]}
                (compiler/compile-query
                  test-fields
                  {:terms [] :filters [{:field "created" :op ">" :value "2024-01-01" :negated true}]}
                  base-stmt)))))

(deftest compile-number-filter-test
  (testing "number with greater than operator"
    (is (match? {:where [:> :m.quantity 10]}
                (compiler/compile-query
                  test-fields
                  {:terms [] :filters [{:field "quantity" :op ">" :value "10" :negated false}]}
                  base-stmt))))

  (testing "number with less than or equal operator"
    (is (match? {:where [:<= :m.quantity 100]}
                (compiler/compile-query
                  test-fields
                  {:terms [] :filters [{:field "quantity" :op "<=" :value "100" :negated false}]}
                  base-stmt))))

  (testing "number without operator uses exact match"
    (is (match? {:where [:= :m.quantity 50]}
                (compiler/compile-query
                  test-fields
                  {:terms [] :filters [{:field "quantity" :value "50" :negated false}]}
                  base-stmt)))))

(deftest compile-negation-test
  (testing "negated filter wraps in NOT"
    (is (match? {:where [:not [:= :m.type "dead"]]}
                (compiler/compile-query
                  test-fields
                  {:terms [] :filters [{:field "type" :value "dead" :negated true}]}
                  base-stmt))))

  (testing "negated boolean"
    (is (match? {:where [:not [:= :m.private true]]}
                (compiler/compile-query
                  test-fields
                  {:terms [] :filters [{:field "private" :negated true}]}
                  base-stmt)))))

(deftest compile-joins-test
  (testing "filter with joins adds join clause"
    (let [result (compiler/compile-query
                   test-fields
                   {:terms [] :filters [{:field "taxon" :value "Quercus" :negated false}]}
                   base-stmt)]
      (is (contains? result :join))
      (is (match? [[:accession :a] [:= :a.id :m.accession_id]
                   [:taxon :t] [:= :t.id :a.taxon_id]]
                  (:join result)))))

  (testing "joins trigger select-distinct"
    (let [result (compiler/compile-query
                   test-fields
                   {:terms [] :filters [{:field "taxon" :value "Quercus" :negated false}]}
                   base-stmt)]
      (is (contains? result :select-distinct))
      (is (not (contains? result :select))))))

(deftest compile-multiple-joins-test
  (testing "multiple filters with same join don't duplicate"
    (let [fields {:taxon1 {:column :t.name :type :text
                           :joins [[:taxon :t] [:= :t.id :a.taxon_id]]}
                  :taxon2 {:column :t.author :type :text
                           :joins [[:taxon :t] [:= :t.id :a.taxon_id]]}}
          result (compiler/compile-query
                   fields
                   {:terms []
                    :filters [{:field "taxon1" :value "Quercus" :negated false}
                              {:field "taxon2" :value "L." :negated false}]}
                   base-stmt)]
      ;; Should only have one join to taxon, not two
      (is (= 2 (count (:join result))))))) ; [[:taxon :t] [:= ...]]

(deftest compile-multiple-filters-test
  (testing "multiple filters combined with AND"
    (let [result (compiler/compile-query
                   test-fields
                   {:terms []
                    :filters [{:field "type" :value "seed" :negated false}
                              {:field "status" :value "alive" :negated false}]}
                   base-stmt)]
      (is (match? {:where [:and
                           [:= :m.type "seed"]
                           [:= :m.status "alive"]]}
                  result)))))

(deftest compile-terms-test
  (testing "terms use FTS subquery on first FTS field"
    (let [result (compiler/compile-query
                   test-fields
                   {:terms ["quercus" "alba"] :filters []}
                   base-stmt)]
      (is (match? {:where [:in :t.id {:select [:rowid]
                                      :from [:taxon_fts]
                                      :where [:match :taxon_fts "quercus alba*"]}]}
                  result)))))

(deftest compile-terms-and-filters-test
  (testing "terms and filters combined"
    (let [result (compiler/compile-query
                   test-fields
                   {:terms ["alba"]
                    :filters [{:field "type" :value "seed" :negated false}]}
                   base-stmt)]
      (is (match? {:where [:and
                           [:= :m.type "seed"]
                           [:in :t.id {:select [:rowid]
                                       :from [:taxon_fts]
                                       :where [:match :taxon_fts "alba*"]}]]}
                  result)))))

(deftest compile-unknown-field-test
  (testing "unknown fields are ignored"
    (is (= base-stmt
           (compiler/compile-query
             test-fields
             {:terms [] :filters [{:field "unknown" :value "x" :negated false}]}
             base-stmt)))))
