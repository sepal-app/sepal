(ns sepal.config.interface-test
  (:require [babashka.fs :as fs]
            [clojure.test :refer [deftest is testing]]
            [sepal.config.interface :as config.i]))

(deftest test-data-home-priority
  (testing "SEPAL_DATA_HOME wins over everything else"
    (is (= "/custom/data"
           (config.i/data-home {"SEPAL_DATA_HOME" "/custom/data"
                                "XDG_DATA_HOME" "/xdg/data"}))))

  (testing "XDG_DATA_HOME is honoured and gets /Sepal appended"
    ;; This is the regression guard: the old system.edn expression
    ;; (#or [#env SEPAL_DATA_HOME #join [#env HOME "/.local/share/Sepal"]])
    ;; ignored XDG_DATA_HOME entirely and would have produced
    ;; "/home/user/.local/share/Sepal" here instead.
    (is (= (str (fs/path "/xdg/data" "Sepal"))
           (config.i/data-home {"XDG_DATA_HOME" "/xdg/data"}))))

  (testing "neither set falls back to the platform default"
    (let [expected (if (= "Mac OS X" (System/getProperty "os.name"))
                     (str (fs/path (System/getProperty "user.home") "Library" "Application Support" "Sepal"))
                     (str (fs/path (fs/xdg-data-home) "Sepal")))]
      (is (= expected (config.i/data-home {}))))))
