(ns sepal.database.honeysql-test
  (:require [clojure.test :refer [deftest is testing]]
            [honey.sql :as sql]
            [sepal.database.honeysql :as db.honeysql]))

(defn- fmt [stmt]
  (db.honeysql/init)
  (sql/format stmt))

(deftest match-is-parameterized-test
  ;; The FTS pattern is always user input from a search box. Formatting it by
  ;; concatenation put it inside a SQL string literal unescaped, so a single
  ;; quote closed the literal early. Asserting on the params vector rather than
  ;; on the absence of an error is what makes this fail for the right reason.
  (testing "the pattern is a bind parameter, not part of the SQL text"
    (let [[sql & params] (fmt {:select [:rowid]
                               :from [:taxon_fts]
                               :where [:match :taxon_fts "\"Quercus\"*"]})]
      (is (= "SELECT rowid FROM taxon_fts WHERE taxon_fts match ?" sql))
      (is (= ["\"Quercus\"*"] params))))

  (testing "a quote in the pattern stays in the parameter and out of the SQL"
    (let [[sql & params] (fmt {:select [:rowid]
                               :from [:taxon_fts]
                               :where [:match :taxon_fts "\"Rosa\" \"'Peace'\"*"]})]
      (is (= "SELECT rowid FROM taxon_fts WHERE taxon_fts match ?" sql))
      (is (= ["\"Rosa\" \"'Peace'\"*"] params))
      (is (not (re-find #"Peace" sql))
          "the value must not appear in the SQL text at all")))

  (testing "a pattern that would terminate the statement is still one parameter"
    (let [[sql & params] (fmt {:select [:rowid]
                               :from [:taxon_fts]
                               :where [:match :taxon_fts "x' or 1=1 --"]})]
      (is (= "SELECT rowid FROM taxon_fts WHERE taxon_fts match ?" sql))
      (is (= ["x' or 1=1 --"] params))
      (is (not (re-find #"1=1" sql))))))
