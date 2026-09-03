(ns sepal.app.routes.taxon.index-test
  (:require [babashka.fs :as fs]
            [clojure.data.json :as json]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [integrant.core :as ig]
            [next.jdbc :as jdbc]
            [next.jdbc.sql :as jdbc.sql]
            [peridot.core :as peri]
            [sepal.app.test :as app.test]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.synonym.interface :as synonym.i]
            [sepal.taxon.interface :as taxon.i]
            [sepal.user.interface :as user.i]))

(use-fixtures :once default-system-fixture)

(def ^:private password "testpassword123")

(defn- create-user! [db]
  (let [email (str "user-" (random-uuid) "@test.com")]
    (user.i/create! db {:email email :password password :role :admin})
    email))

;; A canned resolve, standing in for the real reference-pool lookup. The
;; process the test system builds carries no WFO reference file (there is no
;; way to hand default-system-fixture one without changing shared test
;; infrastructure other tasks also rely on), so `sepal.synonym.interface/resolve`
;; itself is redefined here rather than seeded through the request context.
;; This still proves what this route owns: that `context` and `q` reach
;; `resolve`, and that the two branches use its result the way the brief
;; describes. The reference file's own matching behaviour is covered by
;; components/synonym/test/sepal/synonym/reference_test.clj.
(defn- resolve-to [taxon-id taxon-name]
  (fn [_ctx _db q]
    (if (= q "Encyclia")
      [{:synonym/synonym-name "Encyclia cochleata"
        :synonym/source "wfo"
        :taxon/id taxon-id
        :taxon/name taxon-name}]
      [])))

(deftest test-the-picker-annotates-a-synonym-match
  ;; The accession form's taxon field calls this with Accept: application/json.
  ;; A name only WFO knows must resolve to the accepted taxon and say why.
  (tf/testing "JSON branch"
    {[::taxon.i/factory :key/taxon] {:db *db*}}
    (fn [{:keys [taxon]}]
      (jdbc.sql/update! *db* :taxon {:name "Prosthechea cochleata"} {:id (:taxon/id taxon)})
      (with-redefs [synonym.i/resolve (resolve-to (:taxon/id taxon) "Prosthechea cochleata")]
        (let [email (create-user! *db*)
              sess (app.test/login email password)
              body (-> sess
                       (peri/header "accept" "application/json")
                       (peri/request "/taxon/" :params {"q" "Encyclia"})
                       :response :body
                       (json/read-str :key-fn keyword))]
          (is (some #(= "Encyclia cochleata" (:matchedSynonym %)) body))
          (is (some #(= (:taxon/id taxon) (:id %)) body))
          (is (every? (comp string? :text) body)))))))

(deftest test-the-picker-does-not-list-a-taxon-twice
  ;; Both the name search and the synonym search can return the same taxon.
  ;; Rows carry :taxon/id, not :id — reading the wrong key silently disables
  ;; the dedupe, which is the defect this test exists to catch.
  (tf/testing "overlap"
    {[::taxon.i/factory :key/taxon] {:db *db*}}
    (fn [{:keys [taxon]}]
      (jdbc.sql/update! *db* :taxon {:name "Encyclia cochleata"} {:id (:taxon/id taxon)})
      (with-redefs [synonym.i/resolve (resolve-to (:taxon/id taxon) "Encyclia cochleata")]
        (let [email (create-user! *db*)
              sess (app.test/login email password)
              body (-> sess
                       (peri/header "accept" "application/json")
                       (peri/request "/taxon/" :params {"q" "Encyclia"})
                       :response :body
                       (json/read-str :key-fn keyword))
              ids (map :id body)]
          (is (= (count ids) (count (distinct ids)))))))))

(deftest test-the-list-shows-synonym-matches-in-their-own-block
  (tf/testing "HTML branch"
    {[::taxon.i/factory :key/taxon] {:db *db*}}
    (fn [{:keys [taxon]}]
      (jdbc.sql/update! *db* :taxon {:name "Prosthechea cochleata"} {:id (:taxon/id taxon)})
      (with-redefs [synonym.i/resolve (resolve-to (:taxon/id taxon) "Prosthechea cochleata")]
        (let [email (create-user! *db*)
              sess (app.test/login email password)
              body (-> sess (peri/request "/taxon/" :params {"q" "Encyclia"})
                       :response :body)]
          (is (re-find #"spl-alert--info" body)
              "the block's wrapper is present")
          (is (re-find #"Encyclia cochleata" body))
          (is (re-find #"Prosthechea cochleata" body)))))))

(deftest test-the-infinite-scroll-partial-carries-no-synonym-matches
  ;; The `rows` query param asks for <tr>s alone to append to the table. A
  ;; synonym match is not a table row — it has no author, rank or parent — and
  ;; appending one would put blank cells in the table.
  ;;
  ;; The taxon's own name ("Prosthechea cochleata") does not match the query
  ;; ("Encyclia"), so the real name search returns zero rows here: the only way
  ;; a data row could appear at all is the defect this test exists to catch,
  ;; concatenating the synonym hit into the rows passed to index-rows. A real
  ;; data row carries "spl-row" (sepal.app.ui.pages.list/row-attrs); the
  ;; sentinel and end-of-list markers this partial always emits do not, so
  ;; counting that class is an exact check rather than a string search that a
  ;; bug and a fix could equally satisfy.
  (tf/testing "rows partial"
    {[::taxon.i/factory :key/taxon] {:db *db*}}
    (fn [{:keys [taxon]}]
      (jdbc.sql/update! *db* :taxon {:name "Prosthechea cochleata"} {:id (:taxon/id taxon)})
      (with-redefs [synonym.i/resolve (resolve-to (:taxon/id taxon) "Prosthechea cochleata")]
        (let [email (create-user! *db*)
              sess (app.test/login email password)
              body (-> sess (peri/request "/taxon/"
                                          :params {"q" "Encyclia" "rows" "1"})
                       :response :body)]
          (is (zero? (count (re-seq #"spl-row" body))))
          (is (not (re-find #"Encyclia cochleata" body))))))))

(defn- build-reference-fixture!
  "A one-row WFO reference file, the same schema bin/build-synonym-ref.sh
  builds and components/synonym's tests use."
  [path]
  (with-open [conn (jdbc/get-connection
                     (jdbc/get-datasource {:dbtype "sqlite" :dbname (str path)}))]
    (doseq [stmt ["create table syn (name text not null, accepted_core text not null,
                   accepted_wfo_id text not null, name_id text not null) strict"
                  "insert into syn values
                   ('Encyclia cochleata','wfo-0000283538','wfo-0000283538-2025-06','wfo-0001')"
                  "create index syn_accepted_core_idx on syn(accepted_core)"
                  "create virtual table syn_fts using fts5(name, content='syn',
                   content_rowid='rowid', tokenize='unicode61')"
                  "insert into syn_fts(syn_fts) values('rebuild')"
                  "create table metadata (key text primary key, value text) strict"
                  "insert into metadata values ('wfo_plant_list.version','2025-06')"]]
      (jdbc/execute! conn [stmt])))
  path)

(defn- with-reference-pool
  "Run `f` with `synonym.i/resolve` reading a real reference pool.

  The test system's process carries no WFO reference file, and there is no way
  to hand default-system-fixture one without changing shared test
  infrastructure. So rather than stubbing resolve away -- which is what let the
  FTS injection below survive fourteen reviews -- this wraps the real function
  and puts a real pool in the context it is given. Everything past that point
  is production code, including the MATCH."
  [f]
  (let [dir (fs/create-temp-dir)
        path (str (fs/path dir "ref.db"))]
    (build-reference-fixture! path)
    (let [pool (ig/init-key ::synonym.i/reference-pool {:path path})
          real synonym.i/resolve]
      (try
        (with-redefs [synonym.i/resolve
                      (fn [ctx db q] (real (assoc ctx :synonym-reference pool) db q))]
          (f))
        (finally
          (ig/halt-key! ::synonym.i/reference-pool pool)
          (fs/delete-tree dir))))))

(deftest test-a-filter-query-does-not-reach-the-synonym-fts-parser
  ;; Ticking "Only taxa with accessions" sets q to "accessions:>0"
  ;; (query-builder.ts). The taxon compiler parses that into a :count filter
  ;; with no FTS terms at all, but the raw string used to go straight into the
  ;; synonym reference's FTS5 MATCH, where `accessions:>0` reads as a column
  ;; reference: SQLiteException, and a 500 on one click. It only ever fired
  ;; where a reference pool exists, which is never in dev or in a stubbed test
  ;; and always in production.
  (tf/testing "a real reference pool"
    {[::taxon.i/factory :key/taxon] {:db *db*}}
    (fn [{:keys [taxon]}]
      ;; A name that does not itself match "cochleat", so the positive case
      ;; below reaches the synonym block rather than being deduped out of it as
      ;; a taxon the name search already found.
      (jdbc.sql/update! *db* :taxon
                        {:name "Xanthosoma dealbatum"
                         :wfo_taxon_id "wfo-0000283538-2025-12"}
                        {:id (:taxon/id taxon)})
      (with-reference-pool
        (fn []
          (let [email (create-user! *db*)
                sess (app.test/login email password)
                get-response (fn [q]
                               (-> sess (peri/request "/taxon/" :params {"q" q}) :response))]
            (doseq [q ["accessions:>0" "rank:genus" "-cochleat" "   "]]
              (is (= 200 (:status (get-response q)))
                  (str "the taxon list 500ed on " (pr-str q))))
            (testing "the pool really is wired in, so the 200s above are not vacuous"
              (let [body (:body (get-response "cochleat"))]
                (is (re-find #"Also matching a synonym" body))
                (is (re-find #"Encyclia cochleata" body))
                (is (re-find #"Xanthosoma dealbatum" body))))))))))

(deftest test-the-synonym-search-is-given-the-parsed-terms-not-the-raw-query
  ;; The layer fix behind the test above. A synonym name search has no use for
  ;; filter syntax: `accessions:>0` is a WHERE clause the taxon compiler
  ;; handles, and there is nothing in it a synonym could match. Passing the raw
  ;; query string is what put filter syntax in front of FTS5 in the first
  ;; place, so what reaches resolve is asserted directly.
  (tf/testing "what the route hands resolve"
    {[::taxon.i/factory :key/taxon] {:db *db*}}
    (fn [{:keys [_taxon]}]
      (let [seen (atom [])
            email (create-user! *db*)
            sess (app.test/login email password)]
        (with-redefs [synonym.i/resolve (fn [_ctx _db q] (swap! seen conj q) [])]
          (doseq [q ["accessions:>0" "rank:genus" "-cochleat" "Encyclia cochleata"
                     "rank:genus Encyclia"]]
            (peri/request sess "/taxon/" :params {"q" q}))
          (is (= ["" "" "" "Encyclia cochleata" "Encyclia"] @seen)))))))

(deftest test-no-synonym-matches-renders-no-block
  ;; A query nothing matches — real resolve, unmocked. The test process has no
  ;; WFO reference pool and no local taxon_synonym row for this query, so
  ;; resolve's own [] path is exercised, not a stand-in for it.
  ;;
  ;; Asserting the absence of "matches synonym" is not enough: that string
  ;; only ever appears inside a per-item <li>, so it is equally absent whether
  ;; synonym-matches-block's `(when (seq matches) ...)` guard is intact or
  ;; deleted outright — an empty `synonym-matches` still renders the wrapper
  ;; div, the heading and an empty <ul> either way. The heading text is
  ;; emitted unconditionally once inside the div, so it is the assertion that
  ;; actually distinguishes "no block at all" from "an empty block".
  (tf/testing "a query nothing matches"
    {[::taxon.i/factory :key/taxon] {:db *db*}}
    (fn [{:keys [_taxon]}]
      (let [email (create-user! *db*)
            sess (app.test/login email password)
            body (-> sess (peri/request "/taxon/"
                                        :params {"q" "Zzzznotataxon"})
                     :response :body)]
        (is (not (re-find #"Also matching a synonym" body)))
        (is (not (re-find #"spl-alert--info" body)))))))
