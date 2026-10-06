(ns sepal.app.routes.material.index-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [integrant.core :as ig]
            [peridot.core :as peri]
            [sepal.accession.interface :as accession.i]
            [sepal.app.test :as app.test]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.location.interface :as location.i]
            [sepal.material.interface :as material.i]
            [sepal.taxon.interface :as taxon.i]
            [sepal.user.interface :as user.i])
  (:import [org.jsoup Jsoup]))

(use-fixtures :once default-system-fixture)

(def ^:private password "testpassword123")

;; A function, not a top-level def: *db* is bound by the fixture at run time.
(defn- fixtures []
  {[::user.i/factory :key/user] {:db *db* :password password :role :editor}
   [::taxon.i/factory :key/taxon] {:db *db*}
   [::location.i/factory :key/location] {:db *db*}
   [::accession.i/factory :key/accession] {:db *db* :taxon (ig/ref :key/taxon)}
   [::material.i/factory :key/material] {:db *db*
                                         :accession (ig/ref :key/accession)
                                         :location (ig/ref :key/location)
                                         :data {:status :dormant :quantity 1}}})

(defn- fetch [sess url & {:as params}]
  (let [{:keys [response]} (if (seq params)
                             (peri/request sess url :params params)
                             (peri/request sess url))]
    (Jsoup/parse ^String (:body response))))

(defn- living-checkbox [body]
  (.selectFirst body "label[x-data^=termFilter]"))

(deftest test-the-living-checkbox-carries-the-alive-term
  (tf/testing "a living-only checkbox on the page, checked when the query has it"
    (fixtures)
    (fn [{:keys [user]}]
      (let [sess (app.test/login (:user/email user) password)
            unchecked (living-checkbox (fetch sess "/material/"))
            checked (living-checkbox (fetch sess "/material/" "q" "status:alive"))]
        (is (.contains (.attr unchecked "x-data") "'status:alive', false)"))
        (is (.contains (.attr checked "x-data") "'status:alive', true)"))
        (is (.contains (.text unchecked) "Only living material"))))))

(deftest test-the-list-shows-and-filters-by-status
  (tf/testing "a dormant material shows its status, and status:dormant finds it"
    (fixtures)
    (fn [{:keys [user material]}]
      (let [sess (app.test/login (:user/email user) password)
            row (str "tr:has(a[href*=/material/" (:material/id material) "/])")]
        (is (.contains (.text (.selectFirst (fetch sess "/material/" "q" "status:dormant") row))
                       "Dormant"))
        (is (nil? (.selectFirst (fetch sess "/material/" "q" "status:alive") row)))))))

(def sortable-keys
  ["code" "taxon" "location" "type" "quantity" "status" "created" "updated"])

(deftest test-every-sort-answers
  (tf/testing "every sortable column, both ways, is valid SQL"
    (fixtures)
    (fn [{:keys [user]}]
      (let [sess (app.test/login (:user/email user) password)]
        (doseq [k sortable-keys dir ["asc" "desc"]]
          (is (= 200 (:status (:response (peri/request sess "/material/" :params {:sort k :dir dir}))))
              (str k " " dir)))))))

(deftest test-type-and-quantity-show-by-default
  (tf/testing "two new default columns"
    (fixtures)
    (fn [{:keys [user]}]
      (let [sess (app.test/login (:user/email user) password)
            body (app.test/parse-body (:response (peri/request sess "/material/")))
            headers (set (map #(.text %) (.select body "thead th")))]
        (is (contains? headers "Type"))
        (is (contains? headers "Quantity"))))))

(deftest test-sort-by-quantity
  (tf/testing "quantity, largest first"
    (fixtures)
    (fn [{:keys [user]}]
      (let [sess (app.test/login (:user/email user) password)
            body (app.test/parse-body (:response (peri/request sess "/material/"
                                                               :params {:sort "quantity" :dir "desc"})))]
        (is (= "descending" (.attr (.selectFirst body "th[aria-sort]") "aria-sort")))))))

(deftest test-next-page-keeps-the-sort
  (tf/testing "the prefetch row asks for the next page with the same sort"
    (assoc (fixtures)
           [::material.i/factory :key/second] {:db *db*
                                               :accession (ig/ref :key/accession)
                                               :location (ig/ref :key/location)
                                               :data {:status :dormant :quantity 2}})
    (fn [{:keys [user]}]
      (let [sess (app.test/login (:user/email user) password)
            body (app.test/parse-body (:response (peri/request sess "/material/"
                                                               :params {:sort "quantity" :dir "desc" :page-size 1})))
            prefetch (.selectFirst body "tr.spl-prefetch")]
        (is (some? prefetch))
        (is (re-find #"sort=quantity" (.attr prefetch "hx-get")))))))

(defn- rows-partial
  "The infinite-scroll response. Its <tr>s are parsed inside a table, where
  they belong."
  [sess url]
  (let [{:keys [response]} (peri/request sess url :params {:rows "1"})]
    (Jsoup/parse (str "<table><tbody>" (:body response) "</tbody></table>"))))

(deftest test-only-an-editor-can-select-rows
  (tf/testing "the select column and bulk bar are an editor's; a reader sees neither"
    (assoc (fixtures)
           [::user.i/factory :key/reader] {:db *db* :password password :role :reader})
    (fn [{:keys [user reader]}]
      (let [as-reader (app.test/login (:user/email reader) password)
            as-editor (app.test/login (:user/email user) password)
            reader-page (fetch as-reader "/material/")
            editor-page (fetch as-editor "/material/")]
        (is (some? (.selectFirst reader-page "tr.spl-row")) "the reader sees the rows")
        (is (nil? (.selectFirst reader-page "td.spl-col--select")))
        (is (nil? (.selectFirst reader-page ".spl-bulk-bar")))
        (is (nil? (.selectFirst (rows-partial as-reader "/material/") "td.spl-col--select")))
        (is (some? (.selectFirst editor-page "tr.spl-row td.spl-col--select input[data-select-row]")))
        (is (some? (.selectFirst editor-page ".spl-bulk-bar")))
        (is (some? (.selectFirst (rows-partial as-editor "/material/")
                                 "tr.spl-row td.spl-col--select input[data-select-row]")))))))
