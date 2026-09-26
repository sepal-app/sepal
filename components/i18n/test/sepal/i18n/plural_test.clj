(ns sepal.i18n.plural-test
  (:require [clojure.test :refer [are deftest is]]
            [sepal.i18n.plural :as plural]))

(deftest test-parse-header
  (is (= 2 (:nplurals (plural/parse "nplurals=2; plural=(n != 1);"))))
  (is (= 1 (:nplurals (plural/parse "nplurals=1; plural=0;")))))

(deftest test-two-forms
  (let [f (:index (plural/parse "nplurals=2; plural=(n != 1);"))]
    (are [n i] (= i (f n))
      0 1
      1 0
      2 1
      21 1)))

(deftest test-three-forms
  (let [f (:index (plural/parse (str "nplurals=3; plural=(n==1 ? 0 : n%10>=2 && n%10<=4 "
                                     "&& (n%100<10 || n%100>=20) ? 1 : 2);")))]
    (are [n i] (= i (f n))
      1 0
      2 1
      4 1
      5 2
      12 2
      22 1
      25 2
      112 2)))

(deftest test-malformed
  (is (thrown? clojure.lang.ExceptionInfo (plural/parse "nplurals=2; plural=(n != );")))
  (is (thrown? clojure.lang.ExceptionInfo (plural/parse "plural=(n != 1);"))))
