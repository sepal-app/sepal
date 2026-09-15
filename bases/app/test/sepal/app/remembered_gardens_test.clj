(ns sepal.app.remembered-gardens-test
  (:require [clojure.data.json :as json]
            [clojure.test :refer [deftest is testing]]
            [sepal.app.remembered-gardens :as remembered]))

(deftest test-remember
  (testing "no cookie yet: a list of one"
    (is (= ["brooklyn.sepal.app"] (remembered/remember nil "brooklyn.sepal.app"))))

  (testing "a second garden goes in front"
    (is (= ["queens.sepal.app" "brooklyn.sepal.app"]
           (remembered/remember (json/write-str ["brooklyn.sepal.app"]) "queens.sepal.app"))))

  (testing "logging in again moves a garden to the front without duplicating it"
    (is (= ["brooklyn.sepal.app" "queens.sepal.app"]
           (remembered/remember (json/write-str ["queens.sepal.app" "brooklyn.sepal.app"])
                                "brooklyn.sepal.app"))))

  (testing "an eleventh garden drops the oldest"
    (let [ten (mapv #(str "g" % ".sepal.app") (range 10))
          result (remembered/remember (json/write-str ten) "new.sepal.app")]
      (is (= 10 (count result)))
      (is (= "new.sepal.app" (first result)))
      (is (not (some #{"g9.sepal.app"} result)))))

  (testing "a value that is not a JSON array of strings is treated as empty"
    (is (= ["brooklyn.sepal.app"] (remembered/remember "not json" "brooklyn.sepal.app")))
    (is (= ["brooklyn.sepal.app"] (remembered/remember (json/write-str {:a 1}) "brooklyn.sepal.app")))
    (is (= ["brooklyn.sepal.app"] (remembered/remember (json/write-str [1 2]) "brooklyn.sepal.app")))))

(deftest test-cookie
  (let [cookie (remembered/cookie {:domain "sepal.app"
                                   :existing nil
                                   :hostname "brooklyn.sepal.app"})]
    (testing "the value is the JSON list"
      (is (= ["brooklyn.sepal.app"] (json/read-str (:value cookie)))))

    (testing "one year, on the parent domain, readable by the marketing page"
      (is (= "sepal.app" (:domain cookie)))
      (is (= "/" (:path cookie)))
      (is (= 31536000 (:max-age cookie)))
      (is (true? (:secure cookie)))
      (is (= :lax (:same-site cookie)))
      (is (false? (:http-only cookie))))))
