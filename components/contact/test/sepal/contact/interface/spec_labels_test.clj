(ns sepal.contact.interface.spec-labels-test
  (:require [clojure.test :refer [deftest is]]
            [sepal.contact.interface.spec :as spec]))

(deftest test-every-type-has-a-label
  ;; A type missing from type-labels would render as nothing on the panel.
  (is (= (set (filter keyword? (rest spec/type))) (set (keys spec/type-labels)))))
