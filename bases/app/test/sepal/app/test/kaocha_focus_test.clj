(ns sepal.app.test.kaocha-focus-test
  (:require [clojure.test :refer [deftest is testing]]
            [kaocha.plugin :as plugin]
            [sepal.app.test.kaocha-focus :as focus]))

(def ^:private suites #{:unit :e2e})

(deftest test-narrowing-is-declined-when-it-would-change-what-runs
  (testing "no --focus at all"
    (is (nil? (focus/ns-focus-patterns nil nil suites))))

  (testing "--focus-meta, whose match is only knowable after loading"
    (is (nil? (focus/ns-focus-patterns [:sepal.accession.interface-test] [:slow] suites))))

  (testing "--focus names a suite, not a namespace"
    (is (nil? (focus/ns-focus-patterns [:unit] nil suites))))

  (testing "--focus names a suite alongside a namespace: focus is an OR, so
            narrowing to the namespace would drop the rest of the suite"
    (is (nil? (focus/ns-focus-patterns [:unit :sepal.accession.interface-test] nil suites)))))

(deftest test-a-focused-namespace-narrows-to-itself
  (testing "a bare namespace"
    (is (= ["^sepal\\.accession\\.interface-test$"]
           (focus/ns-focus-patterns [:sepal.accession.interface-test] nil suites))))

  (testing "a var carries its namespace"
    (is (= ["^sepal\\.accession\\.interface-test$"]
           (focus/ns-focus-patterns [:sepal.accession.interface-test/test-create] nil suites))))

  (testing "several namespaces, in the order given"
    (is (= ["^sepal\\.accession\\.interface-test$"
            "^sepal\\.taxon\\.interface-test$"]
           (focus/ns-focus-patterns [:sepal.accession.interface-test
                                     :sepal.taxon.interface-test]
                                    nil
                                    suites))))

  (testing "two vars in one namespace produce one pattern"
    (is (= ["^sepal\\.accession\\.interface-test$"]
           (focus/ns-focus-patterns [:sepal.accession.interface-test/test-create
                                     :sepal.accession.interface-test/test-update]
                                    nil
                                    suites)))))

(deftest test-the-pattern-is-anchored-and-its-dots-are-escaped
  (testing "an unescaped dot is a wildcard, and an unanchored pattern is a
            substring match — either one would load namespaces the run did not
            ask for, which is the whole cost this plugin exists to avoid"
    (let [[pattern] (focus/ns-focus-patterns [:sepal.a-test] nil suites)
          re (re-pattern pattern)]
      (is (re-find re "sepal.a-test"))
      (is (not (re-find re "sepalXa-test")))
      (is (not (re-find re "other.sepal.a-test")))
      (is (not (re-find re "sepal.a-test.more"))))))

(deftest test-pre-load-rewrites-every-suite-when-it-can
  (let [config {:kaocha.filter/focus [:sepal.accession.interface-test]
                :kaocha/tests [{:kaocha.testable/id :unit
                                :kaocha/ns-patterns ["-test$"]}
                               {:kaocha.testable/id :e2e
                                :kaocha/ns-patterns [".*e2e.*-test"]}]}]
    (testing "each suite loads only the focused namespace"
      (is (= [["^sepal\\.accession\\.interface-test$"]
              ["^sepal\\.accession\\.interface-test$"]]
             (->> (focus/narrow-ns-patterns config) :kaocha/tests (map :kaocha/ns-patterns)))))))

(deftest test-pre-load-leaves-the-config-alone-when-it-cannot-narrow
  (let [config {:kaocha/tests [{:kaocha.testable/id :unit
                                :kaocha/ns-patterns ["-test$"]}]}]
    (testing "no --focus means load everything, exactly as before"
      (is (= config (focus/narrow-ns-patterns config))))))

(deftest test-kaocha-can-register-and-run-the-plugin
  (testing "the hook reaches Kaocha's plugin chain under the name tests.edn
            lists, which is the only path that actually matters and the one a
            unit test of narrow-ns-patterns cannot cover"
    (let [chain (plugin/load-all [:sepal.app.test/kaocha-focus])
          config {:kaocha.filter/focus [:sepal.accession.interface-test]
                  :kaocha/tests [{:kaocha.testable/id :unit
                                  :kaocha/ns-patterns ["-test$"]}]}]
      (is (= [:sepal.app.test/kaocha-focus] (map :kaocha.plugin/id chain)))
      (is (= [["^sepal\\.accession\\.interface-test$"]]
             (->> (plugin/run-hook* chain :kaocha.hooks/pre-load config)
                  :kaocha/tests
                  (map :kaocha/ns-patterns)))))))
