(ns sepal.taxon.interface.spec-labels-test
  (:require [clojure.test :refer [deftest is]]
            [sepal.taxon.interface.spec :as spec]))

(deftest test-every-rank-has-a-label
  ;; A rank missing from rank-labels would render as nothing.
  (is (= (set (rest spec/rank)) (set (keys spec/rank-labels)))))
