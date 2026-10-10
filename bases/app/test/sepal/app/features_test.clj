(ns sepal.app.features-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [sepal.app.features :as features]
            [sepal.app.globals :as g]
            [sepal.app.test :as app.test]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.propagation.interface.permission :as propagation.perm]
            [sepal.settings.interface :as settings.i]))

(use-fixtures :once default-system-fixture)

(deftest test-disabled
  (testing "a garden with no features rows has every feature on"
    (is (= #{} (features/disabled *db*))))
  (testing "a row set to off turns that feature off"
    (app.test/with-features-off *db* [:propagation :tags]
      #(is (= #{:propagation :tags} (features/disabled *db*)))))
  (testing "any other value leaves it on"
    (try
      (settings.i/set-value! *db* "features.media" "on")
      (is (= #{} (features/disabled *db*)))
      (finally
        (settings.i/delete! *db* "features.media")))))

(deftest test-enabled?
  (binding [g/*disabled-features* #{:observations}]
    (is (false? (features/enabled? :observations)))
    (is (true? (features/enabled? :propagation)))
    (is (true? (features/enabled? nil)) "a section that cannot be turned off"))
  (is (true? (features/enabled? :observations)) "every feature is on outside a request"))

(deftest test-withheld?
  (binding [g/*disabled-features* #{:propagation}]
    (is (features/withheld? propagation.perm/create))
    (is (features/withheld? propagation.perm/edit))
    (is (features/withheld? propagation.perm/delete))
    (is (not (features/withheld? propagation.perm/view))
        "view stays, so the record renders read-only"))
  (is (not (features/withheld? propagation.perm/edit))))

(deftest test-list-enabled?
  (binding [g/*disabled-features* #{:tags}]
    (is (false? (features/list-enabled? :tag)))
    (is (true? (features/list-enabled? :material)))))

(deftest test-field-options
  (is (some #(= "tag" (:key %)) (features/field-options :material)))
  (binding [g/*disabled-features* #{:tags}]
    (is (not-any? #(= "tag" (:key %)) (features/field-options :material)))))

(deftest test-parse-search
  (is (= [{:field "tag" :value "label" :negated false}]
         (:filters (features/parse-search "tag:label"))))
  (binding [g/*disabled-features* #{:tags}]
    (let [ast (features/parse-search "tag:label -tag:old status:alive quercus")]
      (is (= ["status"] (map :field (:filters ast))) "tag filters are dropped, negated ones too")
      (is (= ["quercus"] (:terms ast)) "free text is kept"))))
