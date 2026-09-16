(ns sepal.app.ui.page-title-test
  (:require [clojure.test :refer [deftest is testing]]
            [sepal.app.ui.page :as ui.page]
            [sepal.app.ui.taxon-name :as taxon-name]))

(deftest test-title-is-most-specific-first
  (testing "a tab truncates from the right, and the page is what tells two
            Sepal tabs apart"
    (is (= "Accessions — More Tomorrow Farm — Sepal"
           (ui.page/document-title :breadcrumbs ["Accessions"]
                                   :organization-name "More Tomorrow Farm")))))

(deftest test-title-uses-the-last-crumb
  (is (= "New accession — More Tomorrow Farm — Sepal"
         (ui.page/document-title
           :breadcrumbs [[:a {:href "/accession/"} "Accessions"] "New accession"]
           :organization-name "More Tomorrow Farm"))))

(deftest test-title-reads-through-hiccup
  (testing "a breadcrumb is hiccup — a link, or a name split into italic and
            upright runs — so the text has to be walked for, and an href must
            not leak into it"
    (is (= "Acer palmatum — Sepal"
           (ui.page/document-title
             :breadcrumbs [[:a {:href "/taxon/"} "Taxa"]
                           [:span (taxon-name/render "Acer palmatum")]])))))

(deftest test-title-leaves-out-what-it-does-not-know
  (testing "a garden that has not named itself gets no placeholder"
    (is (= "Contacts — Sepal"
           (ui.page/document-title :breadcrumbs ["Contacts"])))
    (is (= "Sepal" (ui.page/document-title)))
    (is (= "Sepal" (ui.page/document-title :breadcrumbs [])))))
