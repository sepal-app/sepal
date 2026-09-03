(ns sepal.synonym.interface-test
  (:require [babashka.fs :as fs]
            [clojure.test :refer [deftest is use-fixtures testing]]
            [integrant.core :as ig]
            [malli.core :as m]
            [next.jdbc :as jdbc]
            [next.jdbc.sql :as jdbc.sql]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.database.interface :as db.i]
            [sepal.synonym.interface :as synonym.i]
            [sepal.synonym.interface.spec :as synonym.spec]
            [sepal.taxon.interface :as taxon.i]))

(defn- build-fixture!
  "A 3-row WFO reference file, built the way bin/build-synonym-ref.sh builds
  the real one, and the same content sepal.synonym.reference-test uses. The
  schema and query behaviour are exhaustively covered there; the tests here
  only prove the interface and the union reach that implementation."
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

(def ^:dynamic *ref-pool* nil)

(defn- ref-pool-fixture
  "A process-shaped WFO reference pool, opened once for the whole namespace
  through the same ::synonym.i/reference-pool integrant key process-config
  uses, and bound to *ref-pool* for the union tests below."
  [f]
  (let [dir (fs/create-temp-dir)
        path (str (fs/path dir "ref.db"))]
    (build-fixture! path)
    (let [pool (ig/init-key ::synonym.i/reference-pool {:path path})]
      (try
        (binding [*ref-pool* pool]
          (f))
        (finally
          (ig/halt-key! ::synonym.i/reference-pool pool)
          (fs/delete-tree dir))))))

(use-fixtures :once default-system-fixture ref-pool-fixture)

(def base-ctx {:schema-version (db.i/latest-version)})

(deftest test-the-reference-pool-is-reachable-through-the-interface
  ;; Task 8 calls search, list-for-accepted-core and version through this
  ;; namespace, never through sepal.synonym.reference directly (AGENTS.md:
  ;; always import from interface, not core). This is the front door those
  ;; calls actually use: the ::reference-pool integrant key, opened and
  ;; closed the way process-config wires it in instance.clj.
  (let [dir (fs/create-temp-dir)
        path (str (fs/path dir "ref.db"))]
    (build-fixture! path)
    (let [pool (ig/init-key ::synonym.i/reference-pool {:path path})]
      (try
        (is (= "2025-06" (synonym.i/version pool)))
        (is (= ["Encyclia cochleata"]
               (mapv :name (synonym.i/search pool "cochleat"))))
        (is (= ["Encyclia cochleata"]
               (mapv :name (synonym.i/list-for-accepted-core pool "wfo-0000283538"))))
        (finally
          (ig/halt-key! ::synonym.i/reference-pool pool)
          (fs/delete-tree dir))))))

(deftest test-no-path-yields-no-pool
  (is (nil? (ig/init-key ::synonym.i/reference-pool {:path nil}))))

(deftest test-list-for-taxon-unions-both-sources
  (tf/testing "one local row and one WFO row on the same taxon"
    {[::taxon.i/factory :key/taxon] {:db *db*}}
    (fn [{:keys [taxon]}]
      ;; The factory taxon has no wfo_taxon_id, so give it one that the fixture
      ;; reference file knows about.
      (jdbc.sql/update! *db* :taxon
                        {:wfo_taxon_id "wfo-0000283538-2025-12"}
                        {:id (:taxon/id taxon)})
      (let [row (synonym.i/add-synonym! *db* {:taxon-id (:taxon/id taxon)
                                              :synonym-name "Epidendrum cochleatum"})
            ctx (assoc base-ctx :synonym-reference *ref-pool*)
            rows (synonym.i/list-for-taxon ctx *db* (:taxon/id taxon))]
        (is (= #{"local" "wfo"} (set (map :synonym/source rows))))
        (is (contains? (set (map :synonym/synonym-name rows)) "Encyclia cochleata"))
        (synonym.i/remove-synonym! *db* (:synonym/id row))))))

(deftest test-a-taxon-with-no-wfo-id-gets-only-local-rows
  ;; taxon.wfo_taxon_id is nullable — a hand-added taxon has none, and the WFO
  ;; half must be skipped rather than queried with a null core. A real pool
  ;; queried with a null core would also just match nothing, which would let
  ;; this pass even if the nil-guard broke — so :synonym-reference here is a
  ;; sentinel that is not a real pool at all. reference/list-for-accepted-core
  ;; would throw trying to query it; the only way this test passes is if the
  ;; guard keeps that call from ever happening.
  (tf/testing "no wfo id"
    {[::taxon.i/factory :key/taxon] {:db *db*}}
    (fn [{:keys [taxon]}]
      (jdbc.sql/update! *db* :taxon {:wfo_taxon_id nil} {:id (:taxon/id taxon)})
      (let [row (synonym.i/add-synonym! *db* {:taxon-id (:taxon/id taxon)
                                              :synonym-name "Some name"})
            ctx (assoc base-ctx :synonym-reference ::not-a-real-pool)]
        (is (= ["local"] (mapv :synonym/source
                               (synonym.i/list-for-taxon ctx *db* (:taxon/id taxon)))))
        (synonym.i/remove-synonym! *db* (:synonym/id row))))))

(deftest test-resolve-drops-a-hit-whose-accepted-taxon-is-not-in-this-garden
  ;; A picker's job is to pick a real taxon. An annotated dead end is noise.
  (testing "the fixture's Dracaena rows point at cores this garden lacks"
    (let [ctx (assoc base-ctx :synonym-reference *ref-pool*)]
      (is (= [] (synonym.i/resolve ctx *db* "marginata"))))))

(deftest test-resolve-of-an-empty-or-nil-query-is-empty
  ;; A picker's first keystroke is an empty query. reference/search already
  ;; treats "" and nil as no results; local-matches must agree rather than
  ;; running LIKE '%%' (up to 50 arbitrary local rows) on "" or throwing an
  ;; NPE out of str/lower-case on nil.
  (let [ctx (assoc base-ctx :synonym-reference *ref-pool*)]
    (is (= [] (synonym.i/resolve ctx *db* "")))
    (is (= [] (synonym.i/resolve ctx *db* nil)))))

(deftest test-resolve-with-no-reference-only-returns-local-matches
  ;; resolve's nil-pool contract, mirroring list-for-taxon's below: a garden
  ;; with no reference file still resolves its own synonyms.
  (tf/testing "a nil pool"
    {[::taxon.i/factory :key/taxon] {:db *db*}}
    (fn [{:keys [taxon]}]
      (let [row (synonym.i/add-synonym! *db* {:taxon-id (:taxon/id taxon)
                                              :synonym-name "Encyclia cochleata"})
            ctx (assoc base-ctx :synonym-reference nil)]
        (is (= ["local"] (mapv :synonym/source (synonym.i/resolve ctx *db* "cochleat"))))
        (synonym.i/remove-synonym! *db* (:synonym/id row))))))

(deftest test-resolve-finds-a-local-row-and-a-wfo-row
  (tf/testing "both halves resolve to garden taxa"
    {[::taxon.i/factory :key/taxon] {:db *db*}}
    (fn [{:keys [taxon]}]
      (jdbc.sql/update! *db* :taxon
                        {:wfo_taxon_id "wfo-0000283538-2025-12"}
                        {:id (:taxon/id taxon)})
      (let [row (synonym.i/add-synonym! *db* {:taxon-id (:taxon/id taxon)
                                              :synonym-name "Epidendrum cochleatum"})
            ctx (assoc base-ctx :synonym-reference *ref-pool*)
            hits (synonym.i/resolve ctx *db* "cochleat")]
        (is (= #{"local" "wfo"} (set (map :synonym/source hits))))
        (is (every? #(= (:taxon/id taxon) (:taxon/id %)) hits))
        (testing "the accepted taxon's name and the synonym name it matched are
                  both present -- a route rendering a hit reads both"
          (is (every? #(= (:taxon/name taxon) (:taxon/name %)) hits))
          (is (some #(= "Encyclia cochleata" (:synonym/synonym-name %)) hits))
          (is (some #(= "Epidendrum cochleatum" (:synonym/synonym-name %)) hits)))
        (synonym.i/remove-synonym! *db* (:synonym/id row))))))

(deftest test-no-reference-leaves-the-wfo-half-empty
  ;; CreateTaxon's :taxon/wfo-taxon-id is optional, so mg/generate sets one on
  ;; the factory taxon often enough that leaving it alone would make this
  ;; test reach the nil-pool branch only by chance — and identical to the
  ;; no-wfo-id test above it the rest of the time. Pin a real wfo_taxon_id so
  ;; accepted-core is always non-nil and the nil-pool guard in
  ;; reference/list-for-accepted-core is what's actually being exercised.
  (tf/testing "a nil pool"
    {[::taxon.i/factory :key/taxon] {:db *db*}}
    (fn [{:keys [taxon]}]
      (jdbc.sql/update! *db* :taxon
                        {:wfo_taxon_id "wfo-0000283538-2025-12"}
                        {:id (:taxon/id taxon)})
      (let [row (synonym.i/add-synonym! *db* {:taxon-id (:taxon/id taxon)
                                              :synonym-name "Encyclia cochleata"})
            ctx (assoc base-ctx :synonym-reference nil)]
        (is (= ["local"] (mapv :synonym/source
                               (synonym.i/list-for-taxon ctx *db* (:taxon/id taxon)))))
        (synonym.i/remove-synonym! *db* (:synonym/id row))))))

(deftest test-add-and-remove
  (tf/testing "a synonym round-trips"
    {[::taxon.i/factory :key/taxon] {:db *db*}}
    (fn [{:keys [taxon]}]
      (let [result (synonym.i/add-synonym! *db* {:taxon-id (:taxon/id taxon)
                                                 :synonym-name "Encyclia cochleata"})]
        (is (m/validate synonym.spec/Synonym result))
        (is (= "local" (:synonym/source result)))
        (is (= [(:synonym/id result)]
               (mapv :synonym/id (synonym.i/list-for-taxon base-ctx *db* (:taxon/id taxon)))))
        (synonym.i/remove-synonym! *db* (:synonym/id result))
        (is (empty? (synonym.i/list-for-taxon base-ctx *db* (:taxon/id taxon))))))))

(deftest test-add-synonym-with-an-unknown-taxon-is-refused
  ;; The FK is the real failure contract here: `add-synonym!` does not catch
  ;; and does not return an error map, it throws, same as `store.core/create!`
  ;; would have.
  (is (thrown? org.sqlite.SQLiteException
               (synonym.i/add-synonym! *db* {:taxon-id 999999999
                                             :synonym-name "Nonexistent taxonicus"}))))

(deftest test-the-same-name-against-two-taxa-is-legal
  ;; WFO itself has one name string that is a synonym of two accepted taxa, so
  ;; there is no unique constraint to violate. Asserting this keeps a
  ;; well-meaning future migration from adding one.
  (tf/testing "two taxa, one synonym name"
    {[::taxon.i/factory :key/a] {:db *db*}
     [::taxon.i/factory :key/b] {:db *db*}}
    (fn [{:keys [a b]}]
      (let [x (synonym.i/add-synonym! *db* {:taxon-id (:taxon/id a)
                                            :synonym-name "Dracaena marginata"})
            y (synonym.i/add-synonym! *db* {:taxon-id (:taxon/id b)
                                            :synonym-name "Dracaena marginata"})]
        (is (m/validate synonym.spec/Synonym x))
        (is (m/validate synonym.spec/Synonym y))
        (jdbc.sql/delete! *db* :taxon_synonym {:id (:synonym/id x)})
        (jdbc.sql/delete! *db* :taxon_synonym {:id (:synonym/id y)})))))

(deftest test-imported-rows-are-listed-and-removable
  ;; `resolve` and `list-for-taxon` include imported rows: they are real garden
  ;; records, and an operator who wants one gone must be able to remove it.
  (tf/testing "source imported"
    {[::taxon.i/factory :key/taxon] {:db *db*}}
    (fn [{:keys [taxon]}]
      (let [row (synonym.i/add-synonym! *db* {:taxon-id (:taxon/id taxon)
                                              :synonym-name "Bucida buceras"
                                              :source "imported"})]
        (is (= "imported" (:synonym/source row)))
        (is (= ["imported"]
               (mapv :synonym/source
                     (synonym.i/list-for-taxon base-ctx *db* (:taxon/id taxon)))))
        (synonym.i/remove-synonym! *db* (:synonym/id row))))))

(deftest test-a-floor-database-has-no-local-synonymy
  ;; The table is above the supported floor. A gated read must return empty, not
  ;; throw: the taxon picker calls this on every keystroke.
  (testing "below the gate"
    (is (= [] (synonym.i/list-for-taxon {:schema-version "20260113120000"}
                                        *db* 1)))))

(deftest test-factory
  ;; Exercises `::synonym.i/factory` and its `halt-key!` teardown through the
  ;; fixture map, the way a real caller uses it — a factory that only ever runs
  ;; as a hand-rolled call inside its own test proves nothing about either.
  (tf/testing "::synonym.i/factory"
    {[::taxon.i/factory :key/taxon] {:db *db*}
     [::synonym.i/factory :key/syn] {:db *db* :taxon (ig/ref :key/taxon)}}
    (fn [{:keys [taxon syn]}]
      (is (m/validate synonym.spec/Synonym syn))
      (is (= (:taxon/id taxon) (:synonym/taxon-id syn))))))
