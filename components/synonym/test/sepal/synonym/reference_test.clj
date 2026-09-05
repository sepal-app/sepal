(ns sepal.synonym.reference-test
  (:require [babashka.fs :as fs]
            [clojure.test :refer [deftest is testing]]
            [next.jdbc :as jdbc]
            [sepal.synonym.reference :as reference]))

(defn- build-fixture!
  "A 3-row reference file, built the way bin/build-synonym-ref.sh builds the
  real one so the schema under test is the schema that ships."
  [path]
  (with-open [conn (jdbc/get-connection
                     (jdbc/get-datasource {:dbtype "sqlite" :dbname (str path)}))]
    (doseq [stmt ["create table syn (name text not null, accepted_core text not null,
                   accepted_wfo_id text not null, name_id text not null) strict"
                  "insert into syn values
                   ('Encyclia cochleata','wfo-0000283538','wfo-0000283538-2025-06','wfo-0001'),
                   ('Dracaena marginata','wfo-0000111111','wfo-0000111111-2025-06','wfo-0002'),
                   ('Dracaena marginata','wfo-0000222222','wfo-0000222222-2025-06','wfo-0003')"
                  "create index syn_accepted_core_idx on syn(accepted_core)"
                  "create virtual table syn_fts using fts5(name, content='syn',
                   content_rowid='rowid', tokenize='unicode61')"
                  "insert into syn_fts(syn_fts) values('rebuild')"
                  "create table metadata (key text primary key, value text) strict"
                  "insert into metadata values ('wfo_plant_list.version','2025-06')"]]
      (jdbc/execute! conn [stmt])))
  path)

(deftest test-a-missing-file-yields-no-pool
  ;; The garden must run without the file. This is the degrade the whole design
  ;; rests on, so it is the first thing asserted.
  (testing "nil path and absent path"
    (is (nil? (reference/open nil)))
    (is (nil? (reference/open "/nonexistent/sepal-synonyms.db")))))

(deftest test-a-nil-pool-yields-empty-results
  ;; The default install has no reference file, so every function here must
  ;; degrade to an empty result rather than throw. Task 8 relies on this: it
  ;; calls these functions with whatever pool the process has, nil included,
  ;; with no nil-check of its own.
  (testing "no pool at all"
    (is (= [] (reference/list-for-accepted-core nil "wfo-0000283538")))
    (is (= [] (reference/search nil "cochleat")))
    (is (nil? (reference/version nil))))
  (testing "a real pool but an empty query"
    (let [dir (fs/create-temp-dir)
          path (str (fs/path dir "ref.db"))]
      (build-fixture! path)
      (let [pool (reference/open path)]
        (try
          (is (= [] (reference/search pool "")))
          (finally (reference/close! pool) (fs/delete-tree dir)))))))

(deftest test-the-pool-cannot-write
  (let [dir (fs/create-temp-dir)
        path (str (fs/path dir "ref.db"))]
    (build-fixture! path)
    (let [pool (reference/open path)]
      (try
        (is (thrown? Exception
                     (jdbc/execute! pool ["insert into metadata values ('x','y')"])))
        (finally (reference/close! pool) (fs/delete-tree dir))))))

(deftest test-search-matches-a-name-fragment-by-prefix
  (let [dir (fs/create-temp-dir)
        path (str (fs/path dir "ref.db"))]
    (build-fixture! path)
    (let [pool (reference/open path)]
      (try
        (testing "the same prefix treatment taxon search gives taxon_fts"
          (is (= ["Encyclia cochleata"]
                 (mapv :name (reference/search pool "cochleat")))))
        (testing "one name string may carry two accepted taxa, disambiguated by name_id"
          (let [hits (reference/search pool "marginata")]
            (is (= 2 (count hits)))
            (is (= 2 (count (distinct (map :name-id hits)))))
            (is (= #{"wfo-0000111111" "wfo-0000222222"}
                   (set (map :accepted-core hits))))))
        (finally (reference/close! pool) (fs/delete-tree dir))))))

(deftest test-a-raw-user-query-cannot-reach-the-fts-parser
  ;; Every one of these is a string the taxon list can hand this function. FTS5
  ;; gives `:`, `.`, `'` and `-` meanings of their own inside MATCH, so before
  ;; the query was quoted each of the four below threw an SQLiteException --
  ;; `no such column: accessions`, `no such column: rank`,
  ;; `fts5: syntax error near "."`, `fts5: syntax error near "'"` -- and the one
  ;; a user reaches in a single click is `accessions:>0`, which the "Only taxa
  ;; with accessions" checkbox sets.
  (let [dir (fs/create-temp-dir)
        path (str (fs/path dir "ref.db"))]
    (build-fixture! path)
    (let [pool (reference/open path)]
      (try
        (testing "a plain term still matches"
          (is (= ["Encyclia cochleata"] (mapv :name (reference/search pool "cochleat")))))
        (doseq [q ["accessions:>0"
                   "rank:genus"
                   "sp."
                   "Rosa 'Peace'"
                   "\"unbalanced"
                   "(NEAR)"
                   "a OR b"
                   "-cochleat"
                   "   "]]
          (testing (str "no throw for " (pr-str q))
            (is (vector? (reference/search pool q)))))
        (testing "quoting does not cost a multi-word prefix match"
          (is (= ["Encyclia cochleata"]
                 (mapv :name (reference/search pool "Encyclia cochleat")))))
        (testing "punctuation is tokenized rather than parsed, so a trailing
                  colon is simply not part of the token"
          (is (= ["Encyclia cochleata"] (mapv :name (reference/search pool "cochleat:"))))
          (is (= ["Encyclia cochleata"] (mapv :name (reference/search pool "  cochleat  ")))))
        (finally (reference/close! pool) (fs/delete-tree dir))))))

(deftest test-list-for-accepted-core
  (let [dir (fs/create-temp-dir)
        path (str (fs/path dir "ref.db"))]
    (build-fixture! path)
    (let [pool (reference/open path)]
      (try
        (is (= ["Encyclia cochleata"]
               (mapv :name (reference/list-for-accepted-core pool "wfo-0000283538"))))
        (is (= [] (reference/list-for-accepted-core pool "wfo-9999999999")))
        (finally (reference/close! pool) (fs/delete-tree dir))))))
