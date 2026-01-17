(ns sepal.app.routes.accession.export-test
  "Unit tests for accession CSV export handler."
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is use-fixtures]]
            [integrant.core :as ig]
            [sepal.accession.interface :as accession.i]
            [sepal.app.routes.accession.export :as export]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.taxon.interface :as taxon.i]
            [zodiac.core :as z]))

(use-fixtures :once default-system-fixture)

(defn- call-handler
  "Call the export handler with given params."
  [db params]
  (export/handler ::z/context {:db db}
                  :query-params params))

(deftest export-csv-test
  (tf/testing "exports accessions as CSV with correct headers and content"
    {[::taxon.i/factory :key/taxon] {:db *db*}
     [::accession.i/factory :key/acc] {:db *db* :taxon (ig/ref :key/taxon)}}
    (fn [{:keys [acc]}]
      (let [response (call-handler *db* {:q ""
                                         :include_taxon "true"
                                         :include_collection "false"})
            body (:body response)
            lines (str/split-lines body)]
        ;; Response headers
        (is (= 200 (:status response)))
        (is (str/includes? (get-in response [:headers "Content-Type"]) "text/csv"))
        (is (str/includes? (get-in response [:headers "Content-Disposition"]) "attachment"))
        (is (str/includes? (get-in response [:headers "Content-Disposition"]) "accessions-"))

        ;; CSV header row
        (is (str/includes? (first lines) "accession_id"))
        (is (str/includes? (first lines) "accession_code"))
        (is (str/includes? (first lines) "taxon_name"))

        ;; Data row contains our accession
        (is (some #(str/includes? % (:accession/code acc)) lines))))))

(deftest export-without-taxon-test
  (tf/testing "excludes taxon columns when include_taxon is false"
    {[::taxon.i/factory :key/taxon] {:db *db*}
     [::accession.i/factory :key/acc] {:db *db* :taxon (ig/ref :key/taxon)}}
    (fn [{:keys [_acc]}]
      (let [response (call-handler *db* {:q ""
                                         :include_taxon "false"
                                         :include_collection "false"})
            body (:body response)
            header-line (first (str/split-lines body))]
        (is (= 200 (:status response)))
        ;; Should NOT have taxon columns
        (is (not (str/includes? header-line "taxon_name")))
        (is (not (str/includes? header-line "taxon_author")))
        ;; Should still have accession columns
        (is (str/includes? header-line "accession_code"))))))

(deftest export-with-search-filter-test
  (tf/testing "filters results based on search query"
    {[::taxon.i/factory :key/taxon] {:db *db*}
     [::accession.i/factory :key/acc1] {:db *db* :taxon (ig/ref :key/taxon)}
     [::accession.i/factory :key/acc2] {:db *db* :taxon (ig/ref :key/taxon)}}
    (fn [{:keys [acc1 _acc2]}]
      ;; Search for specific accession code (use first few chars for prefix match)
      (let [search-term (subs (:accession/code acc1) 0 5)
            response (call-handler *db* {:q (str "code:" search-term)
                                         :include_taxon "false"
                                         :include_collection "false"})
            body (:body response)]
        (is (= 200 (:status response)))
        ;; Should include matching accession
        (is (str/includes? body (:accession/code acc1)))))))
