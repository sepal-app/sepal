(ns sepal.malli.interface-test
  (:require [clojure.test :as test :refer :all]
            [sepal.malli.interface :as malli.i]
            [sepal.store.interface :as store.i]))

(use-fixtures :once (fn [_] (malli.i/init)))

(deftest json-schema-test
  (testing "json decode/store"
    (is (= (store.i/coerce :json "{}") {}))
    (is (= (store.i/coerce :json "{\"x\": 1}") {"x" 1}))
    (is (= (store.i/coerce :json "[{\"x\": 1}]") [{"x" 1}])))
  (testing "json encode/store"
    (is (= (store.i/encode :json {"x" 1}) "{\"x\":1}"))
    (is (= (store.i/encode :json [{"x" 1}]) "[{\"x\":1}]"))))
