(ns sepal.app.test.email-test
  (:require [clojure.test :refer [deftest is testing]]
            [malli.core :as m]
            [sepal.app.test.email :as test.email]
            [sepal.user.interface.spec :as user.spec]))

(def ^:private html-email-re
  "The format a browser enforces for `<input type=email>`."
  #"^[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*$")

(deftest test-the-address-is-one-a-browser-will-submit
  (testing "the e2e login types this into a type=email field, and Chromium will
            not submit a form holding an address it rejects"
    (let [addresses (repeatedly 200 test.email/unique)]
      (is (empty? (remove #(re-matches html-email-re %) addresses)))
      (is (empty? (remove #(m/validate user.spec/email %) addresses))))))

(deftest test-each-call-returns-a-different-address
  (testing "one garden per test, but the address has to survive being reused
            against a database another test already wrote to"
    (let [addresses (repeatedly 200 test.email/unique)]
      (is (= 200 (count (distinct addresses)))))))
