(ns sepal.search.parser-test
  (:require [clojure.test :refer [deftest is testing]]
            [matcher-combinators.test :refer [match?]]
            [sepal.search.parser :as parser]))

(deftest parse-empty-test
  (testing "empty string"
    (is (= {:terms [] :filters []} (parser/parse ""))))

  (testing "nil"
    (is (= {:terms [] :filters []} (parser/parse nil))))

  (testing "whitespace only"
    (is (= {:terms [] :filters []} (parser/parse "   ")))))

(deftest parse-terms-test
  (testing "single term"
    (is (match? {:terms ["quercus"] :filters []}
                (parser/parse "quercus"))))

  (testing "multiple terms"
    (is (match? {:terms ["quercus" "alba"] :filters []}
                (parser/parse "quercus alba"))))

  (testing "quoted phrase"
    (is (match? {:terms ["red oak"] :filters []}
                (parser/parse "\"red oak\""))))

  (testing "mixed terms and phrases"
    (is (match? {:terms ["quercus" "red oak" "alba"] :filters []}
                (parser/parse "quercus \"red oak\" alba")))))

(deftest parse-filters-test
  (testing "simple field:value"
    (is (match? {:terms []
                 :filters [{:field "taxon" :value "Quercus" :negated false}]}
                (parser/parse "taxon:Quercus"))))

  (testing "multiple filters"
    (is (match? {:terms []
                 :filters [{:field "taxon" :value "Quercus" :negated false}
                           {:field "location" :value "GH" :negated false}]}
                (parser/parse "taxon:Quercus location:GH"))))

  (testing "quoted value"
    (is (match? {:terms []
                 :filters [{:field "taxon" :value "Quercus alba" :negated false}]}
                (parser/parse "taxon:\"Quercus alba\""))))

  (testing "dotted field name"
    (is (match? {:terms []
                 :filters [{:field "material.type" :value "seed" :negated false}]}
                (parser/parse "material.type:seed"))))

  (testing "multi-value (comma-separated)"
    (is (match? {:terms []
                 :filters [{:field "type" :values ["seed" "plant"] :negated false}]}
                (parser/parse "type:seed,plant"))))

  (testing "multi-value with three values"
    (is (match? {:terms []
                 :filters [{:field "location" :values ["GH" "SH" "NH"] :negated false}]}
                (parser/parse "location:GH,SH,NH")))))

(deftest parse-negation-test
  (testing "negated filter with value"
    (is (match? {:terms []
                 :filters [{:field "type" :value "dead" :negated true}]}
                (parser/parse "-type:dead"))))

  (testing "negated boolean filter"
    (is (match? {:terms []
                 :filters [{:field "private" :negated true}]}
                (parser/parse "-private"))))

  (testing "negated term"
    ;; Note: negated terms become negated filters without values
    (is (match? {:terms []
                 :filters [{:field "obsolete" :negated true}]}
                (parser/parse "-obsolete")))))

(deftest parse-complex-test
  (testing "terms and filters combined"
    (is (match? {:terms ["alba"]
                 :filters [{:field "taxon" :value "Quercus" :negated false}
                           {:field "location" :value "GH" :negated false}]}
                (parser/parse "taxon:Quercus alba location:GH"))))

  (testing "all features combined"
    (is (match? {:terms ["red oak"]
                 :filters [{:field "taxon" :value "Quercus" :negated false}
                           {:field "location" :values ["GH" "SH"] :negated false}
                           {:field "private" :negated true}]}
                (parser/parse "taxon:Quercus location:GH,SH -private \"red oak\"")))))

(deftest parse-comparison-operators-test
  (testing "greater than"
    (is (match? {:terms []
                 :filters [{:field "created" :op ">" :value "2024-01-01" :negated false}]}
                (parser/parse "created:>2024-01-01"))))

  (testing "less than"
    (is (match? {:terms []
                 :filters [{:field "updated" :op "<" :value "2024-06-01" :negated false}]}
                (parser/parse "updated:<2024-06-01"))))

  (testing "greater than or equal"
    (is (match? {:terms []
                 :filters [{:field "created" :op ">=" :value "2024-01-01" :negated false}]}
                (parser/parse "created:>=2024-01-01"))))

  (testing "less than or equal"
    (is (match? {:terms []
                 :filters [{:field "quantity" :op "<=" :value "100" :negated false}]}
                (parser/parse "quantity:<=100"))))

  (testing "exact match"
    (is (match? {:terms []
                 :filters [{:field "code" :op "=" :value "GH" :negated false}]}
                (parser/parse "code:=GH"))))

  (testing "exact match with dotted field"
    (is (match? {:terms []
                 :filters [{:field "location.code" :op "=" :value "GH" :negated false}]}
                (parser/parse "location.code:=GH"))))

  (testing "comparison with negation"
    (is (match? {:terms []
                 :filters [{:field "created" :op ">" :value "2024-01-01" :negated true}]}
                (parser/parse "-created:>2024-01-01"))))

  (testing "mixed comparison and regular filters"
    (is (match? {:terms []
                 :filters [{:field "type" :value "seed" :negated false}
                           {:field "created" :op ">" :value "2024-01-01" :negated false}]}
                (parser/parse "type:seed created:>2024-01-01")))))

(deftest parse-error-test
  (testing "unclosed quote"
    (let [result (parser/parse "taxon:\"unclosed")]
      (is (parser/parse-error? result))
      (is (contains? result :error))
      (is (string? (parser/failure-message result))))))

(deftest unparse-test
  (testing "simple filter"
    (is (= "taxon:Quercus"
           (parser/unparse {:terms [] :filters [{:field "taxon" :value "Quercus"}]}))))

  (testing "filter with spaces (quoted)"
    (is (= "taxon:\"Quercus alba\""
           (parser/unparse {:terms [] :filters [{:field "taxon" :value "Quercus alba"}]}))))

  (testing "multi-value filter"
    (is (= "type:seed,plant"
           (parser/unparse {:terms [] :filters [{:field "type" :values ["seed" "plant"]}]}))))

  (testing "negated filter"
    (is (= "-private"
           (parser/unparse {:terms [] :filters [{:field "private" :negated true}]}))))

  (testing "terms"
    (is (= "quercus alba"
           (parser/unparse {:terms ["quercus" "alba"] :filters []}))))

  (testing "quoted term"
    (is (= "\"red oak\""
           (parser/unparse {:terms ["red oak"] :filters []}))))

  (testing "combined"
    (is (= "taxon:Quercus location:GH alba"
           (parser/unparse {:terms ["alba"]
                            :filters [{:field "taxon" :value "Quercus"}
                                      {:field "location" :value "GH"}]}))))

  (testing "comparison operator"
    (is (= "created:>2024-01-01"
           (parser/unparse {:terms []
                            :filters [{:field "created" :op ">" :value "2024-01-01"}]}))))

  (testing "exact match operator"
    (is (= "location.code:=GH"
           (parser/unparse {:terms []
                            :filters [{:field "location.code" :op "=" :value "GH"}]}))))

  (testing "comparison with negation"
    (is (= "-updated:<=2024-06-01"
           (parser/unparse {:terms []
                            :filters [{:field "updated" :op "<=" :value "2024-06-01" :negated true}]})))))

(deftest roundtrip-test
  (testing "parse then unparse preserves semantics"
    (let [queries ["taxon:Quercus"
                   "type:seed,plant"
                   "-private"
                   "taxon:\"Quercus alba\""
                   "location:GH alba"
                   "taxon:Quercus location:GH,SH -private"]]
      (doseq [q queries]
        (let [ast (parser/parse q)
              unparsed (parser/unparse ast)
              reparsed (parser/parse unparsed)]
          (is (= (:terms ast) (:terms reparsed))
              (str "Terms should match for: " q))
          (is (= (count (:filters ast)) (count (:filters reparsed)))
              (str "Filter count should match for: " q)))))))
