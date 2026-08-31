(ns sepal.app.ui.activity-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [dev.onionpancakes.chassis.core :as chassis]
            [sepal.app.routes.activity.index :as activity.index]
            [sepal.app.ui.activity :as ui.activity])
  (:import [org.jsoup Jsoup]))

(defn- parse [hiccup] (Jsoup/parseBodyFragment (chassis/html hiccup)))

(deftest test-badges-use-the-four-semantic-colours
  (testing "principle 1 allows exactly four semantic colours beyond the accent"
    (doseq [[t cls] [[:accession/created "spl-badge--ok"]
                     [:accession/updated "spl-badge--info"]
                     [:accession/deleted "spl-badge--danger"]
                     [:setup/completed "spl-badge--ok"]]]
      (let [html (chassis/html (ui.activity/action-badge t))]
        (is (str/includes? html cls)
            (str t " should render " cls))))))

(deftest test-unknown-action-falls-back-to-neutral
  (is (str/includes? (chassis/html (ui.activity/action-badge :thing/frobnicated))
                     "spl-badge--neutral")))

(deftest test-badge-carries-its-word
  (testing "colour is never the only carrier of meaning — WCAG 1.4.1"
    (let [body (parse (ui.activity/action-badge :accession/deleted))]
      (is (= "deleted" (str/trim (.text body)))))))

(deftest test-badges-emit-no-daisyui
  (let [html (chassis/html (ui.activity/action-badge :accession/created))]
    (doseq [cls ["badge-success" "badge-info" "badge-error" "badge-ghost"
                 "badge-soft" "badge-sm"]]
      (is (not (str/includes? html cls))
          (str "action-badge still emits " cls)))))

(deftest test-summarise-collapses-a-run-into-counts
  (testing "an event records no diff, so four updates to one accession are four
            identical lines. Collapsing is what makes that readable."
    (is (= "created 3 accessions"
           (activity.index/summarise
             [{:activity/type :accession/created}
              {:activity/type :accession/created}
              {:activity/type :accession/created}])))
    (is (= "updated an accession"
           (activity.index/summarise [{:activity/type :accession/updated}])))
    (is (= "updated 2 taxa and deleted an accession"
           (activity.index/summarise
             [{:activity/type :taxon/updated}
              {:activity/type :taxon/updated}
              {:activity/type :accession/deleted}])))))

(deftest test-summarise-pluralises-taxon-correctly
  (testing "taxon/taxa, not taxons — a botanist notices"
    (is (= "created a taxon" (activity.index/summarise [{:activity/type :taxon/created}])))
    (is (= "created 2 taxa" (activity.index/summarise
                              [{:activity/type :taxon/created}
                               {:activity/type :taxon/created}])))))

(deftest test-summarise-handles-an-empty-run
  (is (= "" (activity.index/summarise []))))
