(ns sepal.app.list-sort-test
  (:require [clojure.test :refer [deftest is testing]]
            [sepal.app.list-sort :as list-sort]))

(def code {:key :code :type :identifier :sort [:a.code]})
(def received {:key :received :type :date :sort [:a.date_received]})
(def parent {:key :parent :type :text})
(def columns [code received parent])

(deftest test-sort-first
  (is (= :asc (list-sort/sort-first code)))
  (is (= :desc (list-sort/sort-first received)) "dates sort newest first")
  (is (= :asc (list-sort/sort-first (assoc received :sort-first :asc)))))

(deftest test-resolve-sort
  (is (= {:column received :dir :desc}
         (list-sort/resolve-sort columns {"sort" "received" "dir" "desc"})))
  (is (= {:column code :dir :asc}
         (list-sort/resolve-sort columns {"sort" "code"}))
      "no dir: the column's first direction")
  (testing "falls back to the default order"
    (is (nil? (list-sort/resolve-sort columns {})))
    (is (nil? (list-sort/resolve-sort columns {"sort" "nope"})) "unknown key")
    (is (nil? (list-sort/resolve-sort columns {"sort" "parent"})) "not sortable")
    (is (nil? (list-sort/resolve-sort columns {"sort" "code" "dir" "sideways"})) "bad dir")))

(deftest test-next-sort
  (testing "a text column: ascending, descending, default"
    (is (= {:column code :dir :asc} (list-sort/next-sort code nil)))
    (is (= {:column code :dir :desc} (list-sort/next-sort code {:column code :dir :asc})))
    (is (nil? (list-sort/next-sort code {:column code :dir :desc}))))
  (testing "a date column: descending, ascending, default"
    (is (= {:column received :dir :desc} (list-sort/next-sort received nil)))
    (is (= {:column received :dir :asc} (list-sort/next-sort received {:column received :dir :desc})))
    (is (nil? (list-sort/next-sort received {:column received :dir :asc}))))
  (testing "another column's sort starts this one fresh"
    (is (= {:column code :dir :asc} (list-sort/next-sort code {:column received :dir :desc})))))

(deftest test-sort-params
  (is (= {:sort "received" :dir "desc"} (list-sort/sort-params {:column received :dir :desc})))
  (is (nil? (list-sort/sort-params nil))))

(deftest test-refined?
  (is (list-sort/refined? "quer" "quer") "only filters changed")
  (is (list-sort/refined? "" "") "no free text either side")
  (is (list-sort/refined? "quer" "querc") "typed more")
  (is (list-sort/refined? "querc" "quer") "deleted some")
  (is (not (list-sort/refined? "" "quer")) "search started")
  (is (not (list-sort/refined? "quer" "")) "search cleared")
  (is (not (list-sort/refined? "quercus" "acer")) "new search"))

(deftest test-carry
  (let [previous {"q" "quer provenance:wild" "sort" "received" "dir" "desc"}]
    (is (= {"q" "querc provenance:wild" "sort" "received" "dir" "desc"}
           (list-sort/carry {"q" "querc provenance:wild"} previous)))
    (is (= {"q" "Quer"} (dissoc (list-sort/carry {"q" "Quer"} {"q" "acer" "sort" "code"}) "dir"))
        "a new search drops it")
    (is (= {"q" "quer" "sort" "received" "dir" "desc"}
           (list-sort/carry {"q" "quer"} {"q" "quer provenance:wild" "sort" "received" "dir" "desc"}))
        "removing a filter keeps it: free text is unchanged")
    (is (= {"q" "quer" "sort" "code"}
           (list-sort/carry {"q" "quer" "sort" "code"} previous))
        "a request with its own sort keeps it")
    (is (= {"q" "quer"} (list-sort/carry {"q" "quer"} {"q" "quer"}))
        "nothing to carry")))
