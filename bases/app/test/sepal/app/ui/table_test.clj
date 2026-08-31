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
        cells (mapv #(.text %) (.select body "tbody tr:not(.spl-end):not(.spl-sentinel) td"))]
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
    (is (zero? (.size (.select body "tbody tr:not(.spl-end)")))
        "no data rows")
    (is (some? (.selectFirst body "tr.spl-end"))
        "an empty list still shows where it ends")))

(deftest test-last-page-ends-the-list
  (testing "with no next page the list shows its bottom rather than stopping
            silently, which is indistinguishable from still loading"
    (let [body (parse)]
      (is (some? (.selectFirst body "tr.spl-end")))
      (is (nil? (.selectFirst body "tr.spl-sentinel"))))))

(def ^:private next-url "/accession/?page=2&rows=1")

(defn- many-rows [n]
  (mapv #(hash-map :code (format "2024.%04d" %)
                   :taxon "Quercus alba"
                   :location "North Woodland"
                   :received "2024-03-14")
        (range n)))

(defn- with-next-page [rows]
  (Jsoup/parseBodyFragment
    (chassis/html (table/table :columns columns
                               :rows rows
                               :href "/accession/"
                               :page 1
                               :page-size 25
                               :total 500))))

(deftest test-more-pages-render-a-sentinel-instead
  (let [body (with-next-page rows)
        sentinel (.selectFirst body "tr.spl-sentinel")]
    (is (some? sentinel))
    (is (= table/sentinel-id (.attr sentinel "id"))
        "the trigger row targets it by id, so it needs one")
    (is (str/blank? (.attr sentinel "hx-trigger"))
        "the sentinel carries no trigger of its own — two triggers race, and a
         fast scroll fetches the same page twice")
    (is (nil? (.selectFirst body "tr.spl-end")) "not the end yet")))

(deftest test-the-fetch-is-triggered-three-rows-early
  (let [body (with-next-page (many-rows 25))
        triggers (.select body "tbody tr.spl-prefetch")
        trigger (.first triggers)
        after (.select (.nextElementSiblings trigger) "tr:not(.spl-prefetch)")]
    (is (= 1 (.size triggers))
        "exactly one trigger, or the same page arrives twice")
    (is (= 4 (.size after))
        "three data rows plus the sentinel follow it, so the request is already
         in flight when the reader reaches the end")
    (is (= "2024.0022" (.text (.selectFirst (.first after) "td")))
        "the 23rd of 25 rows")
    (is (= "intersect once" (.attr trigger "hx-trigger"))
        "not `revealed` — htmx implements that against window scroll, and this
         shell is viewport-locked, so it never fires. Verified in a browser:
         25 rows before scrolling and 25 after.")
    (is (= "outerHTML" (.attr trigger "hx-swap"))
        "the sentinel is replaced, so exactly one is ever in the table")
    (is (= (str "#" table/sentinel-id) (.attr trigger "hx-target")))
    (is (= (str "#" table/sentinel-id) (.attr trigger "hx-indicator"))
        "the spinner shows where the rows will land, not on whichever row
         happened to trigger the fetch")
    (is (= next-url (.attr trigger "hx-get")))))

(deftest test-a-short-page-still-triggers
  (testing "fewer rows than the prefetch offset falls back to the top of the
            page rather than to no trigger at all"
    (let [body (with-next-page (many-rows 2))
          triggers (.select body "tbody tr.spl-prefetch")]
      (is (= 1 (.size triggers)))
      (is (= "2024.0000"
             (.text (.selectFirst (.nextElementSibling (.first triggers)) "td")))))))

(deftest test-data-rows-keep-their-own-htmx-attributes
  (testing "every list row already carries an hx-get that opens the resource
            panel — the prefetch must not land on one and replace it"
    (let [body (Jsoup/parseBodyFragment
                 (chassis/html (table/table :columns columns
                                            :rows (many-rows 25)
                                            :row-attrs (fn [_]
                                                         {:hx-get "/taxon/1/panel/"
                                                          :hx-trigger "panel-select"})
                                            :href "/accession/"
                                            :page 1
                                            :page-size 25
                                            :total 500)))
          data-rows (.select body "tbody tr:not(.spl-prefetch):not(.spl-sentinel)")]
      (is (= 25 (.size data-rows)))
      (is (every? #(= "panel-select" (.attr % "hx-trigger")) data-rows)
          "no data row was turned into a scroll trigger")
      (is (every? #(= "/taxon/1/panel/" (.attr % "hx-get")) data-rows)))))

(deftest test-the-sentinel-announces-loading
  (testing "a live region that is already in the DOM when its text appears —
            one inserted with its text in place announces nothing"
    (let [body (with-next-page rows)
          sentinel (.selectFirst body "tr.spl-sentinel")]
      (is (some? (.selectFirst sentinel ".spl-sentinel-spinner[aria-hidden=true]"))
          "the spinner is decorative; the status text carries the meaning")
      (is (= "status" (.attr (.selectFirst sentinel ".spl-sentinel-status") "role")))
      (is (= "Loading more rows"
             (.text (.selectFirst sentinel ".spl-sentinel-loading")))))))

(deftest test-a-scroll-response-updates-the-toolbar-count
  (testing "without this the count freezes at the first page while the list
            grows under it"
    (let [body (Jsoup/parseBodyFragment
                 (str "<div>"
                      (chassis/html (table/rows-only :columns columns
                                                     :rows (many-rows 25)
                                                     :href "/accession/"
                                                     :page 3
                                                     :page-size 25
                                                     :total 500))
                      "</div>"))
          count-el (.selectFirst body (str "#" table/count-id))]
      (is (some? count-el))
      (is (= "true" (.attr count-el "hx-swap-oob")))
      (is (= "75 of 500" (.text count-el))
          "three pages loaded, not just the page in this response"))))

(deftest test-the-page-itself-carries-no-out-of-band-count
  (testing "an hx-swap-oob inside the initial <tbody> would be a stray <p> in
            the table and a duplicate id"
    (let [html (chassis/html (table/table :columns columns
                                          :rows rows
                                          :href "/accession/"
                                          :page 1
                                          :page-size 25
                                          :total 500))]
      (is (not (str/includes? html "hx-swap-oob"))))))

(deftest test-a-fixed-list-has-no-scroll-machinery
  (testing "settings tables pass no paging state and must not sprout a
            sentinel, a trigger or a count"
    (let [body (parse)]
      (is (nil? (.selectFirst body "tr.spl-prefetch")))
      (is (nil? (.selectFirst body "tr.spl-sentinel")))
      (is (some? (.selectFirst body "tr.spl-end"))))))

(deftest test-an-empty-list-explains-itself
  (testing "a header row over nothing, with END OF LIST under it, reads as a
            list that failed to load"
    (let [body (Jsoup/parseBodyFragment
                 (chassis/html (table/table :columns columns
                                            :rows []
                                            :empty-state [:p {:class "spl-empty"}
                                                          "No contacts yet"])))]
      (is (nil? (.selectFirst body "table")) "the table is replaced, not filled")
      (is (nil? (.selectFirst body "tr.spl-end")))
      (is (= "No contacts yet" (.text (.selectFirst body ".spl-empty")))))))

(deftest test-a-list-with-no-empty-state-still-renders-a-table
  (testing "settings tables pass none and must keep their header"
    (let [body (parse :rows [])]
      (is (some? (.selectFirst body "table")))
      (is (some? (.selectFirst body "tr.spl-end"))))))

(deftest test-rows-only-matches-what-the-table-renders
  (testing "an appended row is built by the same code as a row present on load"
    (let [in-table (Jsoup/parseBodyFragment
                     (chassis/html (table/table :columns columns :rows rows)))
          ;; Wrapped in a table because Jsoup discards a bare <tr>, which is
          ;; also what a browser does — the real swap target is a <tbody>.
          standalone (Jsoup/parseBodyFragment
                       (str "<table><tbody>"
                            (chassis/html (table/rows-only :columns columns :rows rows))
                            "</tbody></table>"))]
      (is (= (.html (.selectFirst in-table "tbody tr"))
             (.html (.selectFirst standalone "tr")))))))

(deftest test-no-daisyui-or-hardcoded-palette
  (testing "principle 7: the table emits no DaisyUI class and no literal colour"
    (let [html (render)]
      (doseq [cls ["bg-base-200" "bg-base-100" "border-base-300" "border-base-200"
                   "text-gray-900" "rounded-box"]]
        (is (not (str/includes? html cls))
            (str "table still emits " cls))))))
