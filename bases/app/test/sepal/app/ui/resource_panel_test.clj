(ns sepal.app.ui.resource-panel-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [dev.onionpancakes.chassis.core :as chassis]
            [sepal.app.ui.resource-panel :as panel])
  (:import [org.jsoup Jsoup]))

(defn- parse [hiccup] (Jsoup/parseBodyFragment (chassis/html hiccup)))

(def ^:private fields
  [{:label "Code" :value "2026.0001"}
   {:label "Taxon" :value [:span "Maxillaria variabilis"]}
   {:label "Absent" :value nil}])

(deftest test-summary-renders-pairs-into-the-kv-grid
  (let [body (parse (panel/summary-section :fields fields))]
    (is (some? (.selectFirst body "dl.spl-kv")))
    (is (= 2 (.size (.select body "dt.spl-k"))) "nil values are skipped")
    (is (= 2 (.size (.select body "dd.spl-v"))))
    (is (= "Code" (.text (.first (.select body "dt.spl-k")))))))

(deftest test-pairs-are-direct-children-of-the-grid
  (testing "the grid places dt and dd in columns, so a wrapper around each pair
            would break the alignment it exists to provide"
    (let [body (parse (panel/summary-section :fields fields))
          dl (.selectFirst body "dl.spl-kv")]
      (doseq [child (.children dl)]
        (is (contains? #{"dt" "dd"} (.tagName child))
            (str "unexpected wrapper in the kv grid: " (.tagName child)))))))

(deftest test-no-fragment-markup-leaks-into-the-output
  (testing "regression: `[:<> …]` is a React reflex. Chassis has no fragment
            element and renders it as a literal <<>> tag, which showed up in
            the panel as the text `<<>>` beside every label."
    (let [html (chassis/html (panel/summary-section :fields fields))]
      (is (not (str/includes? html "<<>>")))
      (is (not (str/includes? html "<:<>")))
      (is (not (re-find #"<\s*<" html))))))

(deftest test-panel-header-labels-the-record-like-the-page-does
  (let [body (parse (panel/panel-header :title "2026.0001"
                                        :subtitle [:span "Maxillaria variabilis"]
                                        :on-close "closePanel()"))]
    (is (= "2026.0001" (.text (.selectFirst body ".spl-panel-code"))))
    (is (some? (.selectFirst body ".spl-panel-name")))
    (is (= "Close panel" (.attr (.selectFirst body "[data-panel-close]") "aria-label")))))

(deftest test-panel-header-without-close-has-no-button
  (let [body (parse (panel/panel-header :title "2026.0001"))]
    (is (nil? (.selectFirst body "[data-panel-close]")))))
