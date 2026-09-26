(ns sepal.accession.interface.spec-labels-test
  (:require [clojure.test :refer [deftest is]]
            [sepal.accession.interface.spec :as spec]))

(deftest test-every-enum-value-has-a-label
  ;; A value missing from its labels map still renders, but as its humanised
  ;; keyword, which is never translated.
  (doseq [[enum labels] [[spec/id-qualifier-rank spec/id-qualifier-rank-labels]
                         [spec/provenance-type spec/provenance-type-labels]
                         [spec/wild-provenance-status spec/wild-provenance-status-labels]
                         [spec/received-type spec/received-type-labels]]]
    (is (= (set (filter keyword? (rest enum))) (set (keys labels))))))
