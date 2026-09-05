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
            [sepal.database.interface :as db.i]
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

;;; ---------------------------------------------------------------------------
;;; `synonym:` as a search field
;;; ---------------------------------------------------------------------------

(defn- with-synonym-ids-pool
  "Run `f` with `synonym.i/taxon-ids-for-synonym` reading a real reference pool.

  The same shape as `with-reference-pool` above and for the same reason: the
  test system's process carries no WFO reference file, so the pool is injected
  into the context the real function receives rather than the function being
  replaced. Everything past that point is production code, including the
  cores-to-ids scan and the FTS MATCH."
  [f]
  (let [dir (fs/create-temp-dir)
        path (str (fs/path dir "ref.db"))]
    (build-reference-fixture! path)
    (let [pool (ig/init-key ::synonym.i/reference-pool {:path path})
          real synonym.i/taxon-ids-for-synonym]
      (try
        (with-redefs [synonym.i/taxon-ids-for-synonym
                      (fn [ctx db q] (real (assoc ctx :synonym-reference pool) db q))]
          (f))
        (finally
          (ig/halt-key! ::synonym.i/reference-pool pool)
          (fs/delete-tree dir))))))

(deftest test-a-synonym-filter-finds-a-taxon-that-does-not-match-by-name
  ;; The point of the feature: `Prosthechea` never appears in the query, and
  ;; `Encyclia` never appears in the taxon's own name.
  (tf/testing "synonym:Encyclia"
    {[::taxon.i/factory :key/taxon] {:db *db*}}
    (fn [{:keys [taxon]}]
      (jdbc.sql/update! *db* :taxon
                        {:wfo_taxon_id "wfo-0000283538-2025-12"
                         :name "Prosthechea cochleata"}
                        {:id (:taxon/id taxon)})
      (with-synonym-ids-pool
        (fn []
          (let [email (create-user! *db*)
                sess (app.test/login email password)
                body (-> sess
                         (peri/request "/taxon/" :params {"q" "synonym:Encyclia"})
                         :response :body)]
            (is (re-find #"Prosthechea cochleata" body)
                "the accepted taxon is a row in the table, not a block entry")
            (is (not (re-find #"Type at least" body)))
            (is (not (re-find #"matches more than" body)))))))))

(deftest test-a-synonym-filter-narrows-rather-than-widens
  ;; A filter that lost its clause would return every taxon. Two taxa exist and
  ;; only one has the synonym, so a widened query shows both.
  (tf/testing "synonym:Encyclia with a second taxon present"
    {[::taxon.i/factory :key/a] {:db *db*}
     [::taxon.i/factory :key/b] {:db *db*}}
    (fn [{:keys [a b]}]
      (jdbc.sql/update! *db* :taxon
                        {:wfo_taxon_id "wfo-0000283538-2025-12"
                         :name "Prosthechea cochleata"}
                        {:id (:taxon/id a)})
      (jdbc.sql/update! *db* :taxon
                        {:wfo_taxon_id nil :name "Zzz notasynonym"}
                        {:id (:taxon/id b)})
      (with-synonym-ids-pool
        (fn []
          (let [email (create-user! *db*)
                sess (app.test/login email password)
                body (-> sess
                         (peri/request "/taxon/" :params {"q" "synonym:Encyclia"})
                         :response :body)]
            (is (re-find #"Prosthechea cochleata" body))
            (is (not (re-find #"Zzz notasynonym" body))
                "a taxon without the synonym must not appear")))))))

(deftest test-a-synonym-filter-survives-a-free-text-term
  ;; compile-query does `(assoc :where …)`, so a :where placed in base-stmt is
  ;; overwritten once terms or filters produce a clause. If the id filter were
  ;; applied before compiling, this query would return every taxon matching the
  ;; term and quietly ignore the synonym.
  (tf/testing "synonym:Encyclia plus a term that matches the other taxon"
    {[::taxon.i/factory :key/a] {:db *db*}
     [::taxon.i/factory :key/b] {:db *db*}}
    (fn [{:keys [a b]}]
      (jdbc.sql/update! *db* :taxon
                        {:wfo_taxon_id "wfo-0000283538-2025-12"
                         :name "Prosthechea cochleata"}
                        {:id (:taxon/id a)})
      (jdbc.sql/update! *db* :taxon
                        {:wfo_taxon_id nil :name "Wombat cochleata"}
                        {:id (:taxon/id b)})
      (with-synonym-ids-pool
        (fn []
          (let [email (create-user! *db*)
                sess (app.test/login email password)
                body (-> sess
                         (peri/request "/taxon/"
                                       :params {"q" "synonym:Encyclia cochleata"})
                         :response :body)]
            (is (not (re-find #"Wombat cochleata" body))
                "the term matched it but the synonym filter must still exclude it")))))))

(deftest test-a-one-character-synonym-query-is-refused
  (tf/testing "synonym:a"
    {[::taxon.i/factory :key/taxon] {:db *db*}}
    (fn [{:keys [taxon]}]
      (jdbc.sql/update! *db* :taxon
                        {:wfo_taxon_id "wfo-0000283538-2025-12"
                         :name "Prosthechea cochleata"}
                        {:id (:taxon/id taxon)})
      (with-synonym-ids-pool
        (fn []
          (let [email (create-user! *db*)
                sess (app.test/login email password)
                body (-> sess
                         (peri/request "/taxon/" :params {"q" "synonym:a"})
                         :response :body)]
            (is (re-find #"Type at least" body) "it says why it searched nothing")
            (is (not (re-find #"Prosthechea cochleata" body))
                "and returns nothing rather than every taxon")))))))

(deftest test-botanical-notation-in-a-synonym-filter-does-not-500
  ;; `sp.`, `subsp.` and `var.` are how botanists write names, and FTS5 reads
  ;; `.` `'` `(` `)` `-` as syntax. reference/search quotes its tokens, but the
  ;; filter path is not the term path, so it gets its own test.
  (tf/testing "punctuation a curator actually types"
    {[::taxon.i/factory :key/taxon] {:db *db*}}
    (fn [{:keys [_taxon]}]
      (with-synonym-ids-pool
        (fn []
          (let [email (create-user! *db*)
                sess (app.test/login email password)]
            ;; Single-token values only, deliberately. A multi-word query like
            ;; `synonym:Rosa 'Peace'` parses to filter "Rosa" plus the TERM
            ;; "'Peace'", and that term goes to taxon_fts through
            ;; components/search's terms->clause, which does not quote and 500s
            ;; on it. That is the pre-existing bug 032's final review recorded,
            ;; not this path -- verified with search.i/parse. Testing it here
            ;; would assert someone else's defect and fail for the wrong reason.
            (doseq [q ["synonym:sp." "synonym:subsp." "synonym:var."
                       "synonym:x-hybrid" "synonym:Peace'" "synonym:(L.)"]]
              (testing q
                (is (= 200 (-> sess
                               (peri/request "/taxon/" :params {"q" q})
                               :response :status)))))))))))

(deftest test-a-synonym-filter-below-the-schema-floor-returns-nothing
  ;; taxon_synonym is above the supported floor. The garden half must be gated
  ;; and the page must render rather than error.
  (tf/testing "below the gate"
    {[::taxon.i/factory :key/taxon] {:db *db*}}
    (fn [{:keys [taxon]}]
      (let [id (:taxon/id taxon)
            row (synonym.i/add-synonym! *db* {:taxon-id id
                                              :synonym-name "Bucida buceras"})]
        (jdbc.sql/update! *db* :taxon {:name "Terminalia buceras"} {:id id})
        (with-synonym-ids-pool
          (fn []
            (let [email (create-user! *db*)
                  sess (app.test/login email password)
                  find-body (fn [] (-> sess
                                       (peri/request "/taxon/"
                                                     :params {"q" "synonym:Bucida"})
                                       :response :body))]
              (testing "the row is really found at the latest schema"
                (is (re-find #"Terminalia buceras" (find-body))))
              (testing "and not below the floor, without erroring"
                (with-redefs [db.i/at-least-version? (constantly false)]
                  (is (not (re-find #"Terminalia buceras" (find-body)))))))))
        (synonym.i/remove-synonym! *db* (:synonym/id row))))))
