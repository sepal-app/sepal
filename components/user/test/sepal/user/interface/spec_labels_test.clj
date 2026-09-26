(ns sepal.user.interface.spec-labels-test
  (:require [clojure.test :refer [deftest is]]
            [sepal.user.interface.spec :as spec]))

(deftest test-every-role-and-status-has-a-label
  ;; A value missing from its labels map would render as nothing.
  (is (= (set (filter keyword? (rest spec/role))) (set (keys spec/role-labels))))
  (is (= (set (filter keyword? (rest spec/status))) (set (keys spec/status-labels)))))
