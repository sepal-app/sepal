(ns sepal.signals.interface-test
  (:require [clojure.test :as test :refer :all]
            [sepal.signals.interface :as signals.i]))

(deftest dummy-test
  (let [signal (signals.i/create-signal)
        value (atom nil)
        cb (fn [v] (reset! value v))
        sub (signals.i/subscribe signal cb)]
    ;; value hasn't changed
    (is (nil? @value))
    ;; After publishing value should be updated
    (signals.i/publish signal "1234")
    (Thread/sleep 10)
    (is (= @value "1234"))
    ;; After unsubscribing value shouldn't update
    (signals.i/unsubscribe sub)
    (signals.i/publish signal "5678")
    (Thread/sleep 10)
    (is (= @value "1234"))))
