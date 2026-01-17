(ns sepal.search.interface-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [matcher-combinators.test :refer [match?]]
            [sepal.search.interface :as search.i]))

;; =============================================================================
;; Test fixtures: Register a test resource
;; =============================================================================

(defmethod search.i/search-config :test-resource [_]
  {:table [:test_resource :tr]
   :fields
   {:name   {:column :tr.name :type :text :label "Name"}
    :type   {:column :tr.type :type :enum :values [:a :b :c] :label "Type"}
    :id     {:column :tr.id :type :id :label "ID"}
    :active {:column :tr.active :type :boolean :label "Active"}
    :taxon  {:column :t.name
             :type :fts
             :fts-table :taxon_fts
             :label "Taxon"
             :joins [[:taxon :t] [:= :t.id :tr.taxon_id]]}}})

;; Clean up after tests
(use-fixtures :once
  (fn [f]
    (try
      (f)
      (finally
        (remove-method search.i/search-config :test-resource)))))

;; =============================================================================
;; Configuration accessor tests
;; =============================================================================

(deftest get-table-test
  (testing "returns table and alias"
    (is (= [:test_resource :tr] (search.i/get-table :test-resource)))))

(deftest get-fields-test
  (testing "returns all fields"
    (let [fields (search.i/get-fields :test-resource)]
      (is (contains? fields :name))
      (is (contains? fields :type))
      (is (contains? fields :taxon)))))

(deftest get-field-test
  (testing "returns specific field"
    (is (match? {:column :tr.name :type :text :label "Name"}
                (search.i/get-field :test-resource :name))))

  (testing "returns nil for unknown field"
    (is (nil? (search.i/get-field :test-resource :unknown)))))

(deftest search-config-unknown-resource-test
  (testing "throws for unknown resource"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo
                          #"No search configuration defined"
                          (search.i/search-config :unknown-resource)))))

;; =============================================================================
;; Parse/unparse tests (delegating to parser)
;; =============================================================================

(deftest parse-test
  (testing "delegates to parser"
    (is (match? {:terms ["hello"] :filters []}
                (search.i/parse "hello")))
    (is (match? {:terms [] :filters [{:field "name" :value "test"}]}
                (search.i/parse "name:test")))))

(deftest unparse-test
  (testing "delegates to parser"
    (is (= "name:test"
           (search.i/unparse {:terms [] :filters [{:field "name" :value "test"}]})))))

(deftest parse-error-test
  (testing "detects parse errors"
    (let [result (search.i/parse "name:\"unclosed")]
      (is (search.i/parse-error? result))
      (is (string? (search.i/failure-message result))))))

;; =============================================================================
;; Compile tests
;; =============================================================================

(deftest compile-query-test
  (testing "compiles simple filter"
    (let [result (search.i/compile-query
                   :test-resource
                   {:terms [] :filters [{:field "name" :value "test" :negated false}]}
                   {:select [:*] :from [[:test_resource :tr]]})]
      (is (match? {:select [:*]
                   :from [[:test_resource :tr]]
                   :where [:like :tr.name "%test%"]}
                  result))))

  (testing "compiles filter with joins"
    (let [result (search.i/compile-query
                   :test-resource
                   {:terms [] :filters [{:field "taxon" :value "Quercus" :negated false}]}
                   {:select [:*] :from [[:test_resource :tr]]})]
      (is (contains? result :join))
      (is (contains? result :select-distinct)))))

;; =============================================================================
;; UI helper tests
;; =============================================================================

(deftest field-options-test
  (testing "returns fields formatted for UI"
    (let [options (search.i/field-options :test-resource)]
      (is (vector? (vec options)))
      (is (every? #(contains? % :key) options))
      (is (every? #(contains? % :label) options))
      (is (every? #(contains? % :type) options))))

  (testing "excludes :id and :boolean type fields"
    (let [keys (set (map :key (search.i/field-options :test-resource)))]
      ;; Should include text, enum, fts fields
      (is (contains? keys "name"))
      (is (contains? keys "type"))
      (is (contains? keys "taxon"))
      ;; Should exclude id and boolean fields
      (is (not (contains? keys "id")))
      (is (not (contains? keys "active")))))

  (testing "includes enum values (as strings for JSON)"
    (let [type-option (->> (search.i/field-options :test-resource)
                           (filter #(= "type" (:key %)))
                           first)]
      (is (= ["a" "b" "c"] (:values type-option)))))

  (testing "sorted by label"
    (let [labels (map :label (search.i/field-options :test-resource))]
      (is (= (sort labels) labels)))))

(deftest ast->filter-badges-test
  (testing "generates badges from filters"
    (let [badges (search.i/ast->filter-badges
                   :test-resource
                   {:terms []
                    :filters [{:field "name" :value "test" :negated false}
                              {:field "type" :value "a" :negated false}]})]
      (is (= 2 (count badges)))
      (is (match? [{:label "Name" :value "test" :negated false}
                   {:label "Type" :value "a" :negated false}]
                  badges))))

  (testing "generates clear-q for each badge"
    (let [badges (search.i/ast->filter-badges
                   :test-resource
                   {:terms ["alba"]
                    :filters [{:field "name" :value "test" :negated false}
                              {:field "type" :value "a" :negated false}]})]
      ;; First badge's clear-q should have second filter + terms
      (is (= "type:a alba" (:clear-q (first badges))))
      ;; Second badge's clear-q should have first filter + terms
      (is (= "name:test alba" (:clear-q (second badges))))))

  (testing "handles multi-value filters"
    (let [badges (search.i/ast->filter-badges
                   :test-resource
                   {:terms []
                    :filters [{:field "type" :values ["a" "b"] :negated false}]})]
      (is (= "a, b" (:value (first badges))))))

  (testing "handles negated filters"
    (let [badges (search.i/ast->filter-badges
                   :test-resource
                   {:terms []
                    :filters [{:field "active" :negated true}]})]
      (is (true? (:negated (first badges))))))

  (testing "uses field label for known fields"
    (let [badges (search.i/ast->filter-badges
                   :test-resource
                   {:terms []
                    :filters [{:field "name" :value "x" :negated false}]})]
      (is (= "Name" (:label (first badges))))))

  (testing "capitalizes unknown field names"
    (let [badges (search.i/ast->filter-badges
                   :test-resource
                   {:terms []
                    :filters [{:field "unknown" :value "x" :negated false}]})]
      (is (= "Unknown" (:label (first badges)))))))

(deftest validate-query-test
  (testing "valid query"
    (is (= {:valid true}
           (search.i/validate-query
             :test-resource
             {:terms [] :filters [{:field "name" :value "test"}]}))))

  (testing "invalid field"
    (let [result (search.i/validate-query
                   :test-resource
                   {:terms [] :filters [{:field "bogus" :value "x"}]})]
      (is (false? (:valid result)))
      (is (= [{:field "bogus" :message "Unknown field"}]
             (:errors result)))))

  (testing "multiple invalid fields"
    (let [result (search.i/validate-query
                   :test-resource
                   {:terms []
                    :filters [{:field "bogus1" :value "x"}
                              {:field "name" :value "ok"}
                              {:field "bogus2" :value "y"}]})]
      (is (false? (:valid result)))
      (is (= 2 (count (:errors result)))))))
