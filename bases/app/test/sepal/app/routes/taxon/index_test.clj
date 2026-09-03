(ns sepal.app.routes.taxon.index-test
  (:require [clojure.data.json :as json]
            [clojure.test :refer [deftest is use-fixtures]]
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
