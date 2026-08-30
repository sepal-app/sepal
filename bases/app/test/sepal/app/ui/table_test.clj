(ns sepal.app.ui.table-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [dev.onionpancakes.chassis.core :as chassis]
            [sepal.app.ui.table :as table])
  (:import [org.jsoup Jsoup]))

(def columns
  [{:name "Code" :type :identifier :priority 1 :cell :code}
   {:name "Taxon" :type :name :priority 1 :cell :taxon}
   {:name "Location" :type :text :priority 2 :cell :location}
   {:name "Received" :type :date :priority 3 :cell :received}])

(def rows
  [{:code "2024.0117" :taxon "Quercus alba"
    :location "North Woodland" :received "2024-03-14"}])

(defn- render [& {:as opts}]
  (chassis/html (table/table :columns (:columns opts columns)
                             :rows (:rows opts rows)
                             :row-attrs (:row-attrs opts))))

(defn- parse [& {:as opts}]
  (Jsoup/parseBodyFragment (apply render (mapcat identity opts))))

(deftest test-renders-a-real-table
  (testing "semantics survive: layout must never come from display:grid on tr,
            because that drops the implicit row and cell ARIA roles"
    (let [body (parse)]
      (is (some? (.selectFirst body "table")))
      (is (some? (.selectFirst body "thead")))
      (is (some? (.selectFirst body "tbody")))
      (is (= 4 (.size (.select body "thead th[scope=col]")))
          "every header cell declares its scope"))))

(deftest test-header-text-comes-from-column-name
  (let [body (parse)]
    (is (= ["Code" "Taxon" "Location" "Received"]
           (mapv #(.text %) (.select body "thead th"))))))

(deftest test-cells-render-through-the-cell-fn
  (let [body (parse)
        cells (mapv #(.text %) (.select body "tbody td"))]
    (is (= ["2024.0117" "Quercus alba" "North Woodland" "2024-03-14"] cells))))

(deftest test-column-type-becomes-a-class
  (testing "type drives width and face — identifier and date are mono"
    (let [body (parse)]
      (is (some? (.selectFirst body "td.spl-col--identifier")))
      (is (some? (.selectFirst body "td.spl-col--name")))
      (is (some? (.selectFirst body "td.spl-col--text")))
      (is (some? (.selectFirst body "td.spl-col--date")))
      (is (some? (.selectFirst body "th.spl-col--identifier"))
          "the header carries the same class so the column stays aligned"))))

(deftest test-priority-becomes-a-shed-class
  (testing "higher priorities are hidden first as width drops; priority 1 never
            sheds and must not carry a shed class at all"
    (let [body (parse)]
      (is (some? (.selectFirst body "th.spl-shed-2")))
      (is (some? (.selectFirst body "th.spl-shed-3")))
      (is (some? (.selectFirst body "td.spl-shed-3")))
      (is (nil? (.selectFirst body "[class*=spl-shed-1]"))))))

(deftest test-missing-type-defaults-to-text
  (let [body (parse :columns [{:name "Notes" :cell :notes}]
                    :rows [{:notes "hello"}])]
    (is (some? (.selectFirst body "td.spl-col--text")))))

(deftest test-row-attrs-are-applied
  (let [body (parse :row-attrs (fn [row] {:data-id (:code row)}))]
    (is (some? (.selectFirst body "tr[data-id=2024.0117]")))))

(deftest test-empty-rows-render-a-table-with-no-body-rows
  (let [body (parse :rows [])]
    (is (some? (.selectFirst body "table")) "the header still renders")
    (is (zero? (.size (.select body "tbody tr"))))))

(deftest test-no-daisyui-or-hardcoded-palette
  (testing "principle 7: the table emits no DaisyUI class and no literal colour"
    (let [html (render)]
      (doseq [cls ["bg-base-200" "bg-base-100" "border-base-300" "border-base-200"
                   "text-gray-900" "rounded-box"]]
        (is (not (str/includes? html cls))
            (str "table still emits " cls))))))
