(ns sepal.material.interface.spec-labels-test
  (:require [clojure.test :refer [deftest is]]
            [sepal.material.interface.spec :as spec]))

(deftest test-every-enum-value-has-a-label
  ;; A value missing from its labels map would render as nothing.
  (is (= (set (rest spec/status)) (set (keys spec/status-labels))))
  (is (= (set (rest spec/type)) (set (keys spec/type-labels)))))
