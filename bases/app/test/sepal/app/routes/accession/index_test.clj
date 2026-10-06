(ns sepal.app.routes.accession.index-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [integrant.core :as ig]
            [next.jdbc.sql :as jdbc.sql]
            [peridot.core :as peri]
            [sepal.accession.interface :as accession.i]
            [sepal.app.test :as app.test]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.contact.interface :as contact.i]
            [sepal.settings.interface :as settings.i]
            [sepal.taxon.interface :as taxon.i]
            [sepal.user.interface :as user.i])
  (:import [org.jsoup Jsoup]))

(use-fixtures :once default-system-fixture)

(defn- fixtures
  "A function, not a def: *db* is bound by the fixture at run time, so reading
   it at namespace load captures nil."
  []
  {[::user.i/factory :key/user] {:db *db*
                                 :password "testpassword123"
                                 :role :editor}
   [::taxon.i/factory :key/taxon] {:db *db*}
   [::accession.i/factory :key/accession] {:db *db* :taxon (ig/ref :key/taxon)}})

(defn- list-page [user]
  (let [sess (app.test/login (:user/email user) "testpassword123")
        {:keys [response]} (-> sess (peri/request "/accession/"))]
    (Jsoup/parse ^String (:body response))))

(deftest test-list-renders-the-new-table
  (tf/testing "the list uses the spl- table rather than DaisyUI's"
    (fixtures)
    (fn [{:keys [user]}]
      (let [body (list-page user)]
        (is (some? (.selectFirst body "table.spl-table")))
        (is (some? (.selectFirst body ".spl-table-card"))
            "the list sits in the spl- card")
        (is (nil? (.selectFirst body ".spl-table-card.rounded-box"))
            "the DaisyUI card wrapper is gone from the table container")))))

(deftest test-columns-carry-type-and-priority
  (tf/testing "column metadata drives width, face and responsive shedding"
    (fixtures)
    (fn [{:keys [user]}]
      (let [body (list-page user)]
        (is (some? (.selectFirst body "th.spl-col--identifier")) "Code")
        (is (some? (.selectFirst body "th.spl-col--name")) "Taxon")
        (is (some? (.selectFirst body "th.spl-col--text")) "Provenance")
        (is (some? (.selectFirst body "th.spl-col--date")) "Received")
        (is (some? (.selectFirst body "th.spl-shed-3"))
            "the lowest-priority column sheds first")))))

(deftest test-taxon-cell-goes-through-the-name-renderer
  (tf/testing "principle 2: every scientific name renders through one function"
    (fixtures)
    (fn [{:keys [user]}]
      (let [body (list-page user)]
        (is (some? (.selectFirst body "td .spl-name"))
            "the taxon cell uses ui.taxon-name/render")
        (is (some? (.selectFirst body "td i.spl-sci"))
            "the italicised part is a real <i>, not a CSS class on the cell")))))

(deftest test-row-navigation-is-keyboard-reachable
  (tf/testing "the row's click handler is an enhancement; the anchor is the
               actual affordance and must be focusable"
    (fixtures)
    (fn [{:keys [user accession]}]
      (let [body (list-page user)
            href (str "/accession/" (:accession/id accession) "/")
            link (.selectFirst body (str "td a[href='" href "']"))]
        (is (some? link) "the code cell links to the accession")
        (is (not (.hasAttr link "tabindex"))
            "no tabindex override that would remove it from tab order")))))

(deftest test-panel-has-a-close-control
  (tf/testing "the panel appears on row click and must be dismissible"
    (fixtures)
    (fn [{:keys [user]}]
      (let [body (list-page user)
            close (.selectFirst body "[data-panel-close]")]
        (is (some? close) "there is a control that clears the selection")
        (is (seq (.attr close "aria-label"))
            "the close control has an accessible name")))))

(deftest test-identifier-cell-carries-stacked-content-for-phones
  (tf/testing "below 640px the table collapses to one column whose cell stacks
               the row; the stacked text renders in .spl-cell-narrow"
    (fixtures)
    (fn [{:keys [user]}]
      (let [body (list-page user)
            cell (.selectFirst body "td.spl-col--identifier")]
        (is (some? cell))
        (is (seq (.text (.selectFirst cell ".spl-cell-narrow")))
            "the identifier cell carries the phone-width summary")))))

(deftest test-created-searches-the-gardens-day
  (tf/testing "created:DAY matches what was created on that day in the garden"
    (fixtures)
    (fn [{:keys [user accession]}]
      ;; 7:00 PM on March 13 in Belize, which UTC already calls the 14th.
      (jdbc.sql/update! *db* :accession {:created_at "2026-03-14 01:00:00"}
                        {:id (:accession/id accession)})
      (settings.i/set-value! *db* "organization.timezone" "America/Belize")
      (try
        (let [sess (app.test/login (:user/email user) "testpassword123")
              listed? (fn [q]
                        (let [{:keys [response]} (peri/request sess "/accession/" :params {:q q})]
                          (some? (.selectFirst (Jsoup/parse ^String (:body response))
                                               (format "tr:contains(%s)" (:accession/code accession))))))]
          (is (listed? "created:2026-03-13") "the garden's day")
          (is (not (listed? "created:2026-03-14")) "not the UTC day")
          (is (listed? "created:<=2026-03-13") "on or before includes the day itself")
          (is (not (listed? "created:>2026-03-13")) "after excludes the day itself"))
        (finally
          (settings.i/set-value! *db* "organization.timezone" "UTC"))))))

(defn- get-page [sess params & {:keys [headers]}]
  (let [sess (reduce-kv peri/header sess (or headers {}))
        {:keys [response]} (peri/request sess "/accession/" :params params)]
    response))

(def sortable-keys
  ["code" "taxon" "provenance" "received" "supplier" "accessioned"
   "received-as" "quantity-received" "created" "updated"])

(deftest test-every-sort-answers
  (tf/testing "every sortable column, both ways, is valid SQL"
    (fixtures)
    (fn [{:keys [user]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")]
        (doseq [k sortable-keys dir ["asc" "desc"]]
          (is (= 200 (:status (get-page sess {:sort k :dir dir}))) (str k " " dir)))))))

(deftest test-sort-orders-rows
  (tf/testing "received, newest first, nulls last"
    (assoc (fixtures)
           [::accession.i/factory :key/newer] {:db *db* :taxon (ig/ref :key/taxon)}
           [::accession.i/factory :key/undated] {:db *db* :taxon (ig/ref :key/taxon)})
    (fn [{:keys [user accession newer undated]}]
      (jdbc.sql/update! *db* :accession {:date_received "2020-01-01"} {:id (:accession/id accession)})
      (jdbc.sql/update! *db* :accession {:date_received "2024-01-01"} {:id (:accession/id newer)})
      (jdbc.sql/update! *db* :accession {:date_received nil} {:id (:accession/id undated)})
      (let [sess (app.test/login (:user/email user) "testpassword123")
            codes (app.test/first-cells (app.test/parse-body (get-page sess {:sort "received" :dir "desc"})))
            expected (mapv :accession/code [newer accession undated])]
        (is (= expected (filterv (set expected) codes)))))))

(deftest test-sorted-header-and-next-page
  (tf/testing "the sorted header is marked and paging keeps the sort"
    (fixtures)
    (fn [{:keys [user]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            body (app.test/parse-body (get-page sess {:sort "received" :dir "desc" :page-size 1}))]
        (is (= "descending" (.attr (.selectFirst body "th[aria-sort]") "aria-sort")))
        (when-let [prefetch (.selectFirst body "tr.spl-prefetch")]
          (is (re-find #"sort=received" (.attr prefetch "hx-get"))))))))

(deftest test-chosen-columns
  (tf/testing "a saved choice shows an extra column and turns shedding off"
    (fixtures)
    (fn [{:keys [user]}]
      (user.i/set-list-columns! *db* (:user/id user) :accession {:supplier true :provenance false})
      (let [sess (app.test/login (:user/email user) "testpassword123")
            body (app.test/parse-body (get-page sess {}))
            headers (mapv #(.text %) (.select body "thead th:not(.spl-col--picker)"))]
        (is (some #{"Supplier"} headers))
        (is (not (some #{"Provenance"} headers)))
        (is (nil? (.selectFirst body "[class*=spl-shed]")))
        (is (some? (.selectFirst body "thead th.spl-col--picker button")))))))

(deftest test-toolbar-refinement-keeps-the-sort
  (tf/testing "typing more keeps the sort and pushes it"
    (fixtures)
    (fn [{:keys [user]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            response (get-page sess {:q "querc"}
                               :headers {"hx-request" "true"
                                         "hx-trigger" "list-toolbar"
                                         "hx-current-url" "http://localhost/accession/?q=quer&sort=received&dir=desc"})]
        (is (re-find #"sort=received" (get-in response [:headers "HX-Push-Url"])))))))

(deftest test-joining-filter-with-supplier-column
  (tf/testing "a filter that joins, with the supplier column visible and sorted"
    (fixtures)
    (fn [{:keys [user taxon]}]
      (user.i/set-list-columns! *db* (:user/id user) :accession {:supplier true})
      (let [contact-id (:contact/id (contact.i/create! *db* {:name "Zorbax"}))
            acc (accession.i/create! *db* {:code "ZZ.1"
                                           :taxon-id (:taxon/id taxon)
                                           :supplier-contact-id contact-id})]
        (try
          (let [sess (app.test/login (:user/email user) "testpassword123")
                response (get-page sess {:q "supplier:Zorbax" :sort "supplier" :dir "asc"})]
            (is (= 200 (:status response)))
            (is (some #(.startsWith ^String % "Supplier")
                      (map #(.text %) (.select (app.test/parse-body response) "thead th")))))
          (finally
            (jdbc.sql/delete! *db* :accession {:id (:accession/id acc)})
            (jdbc.sql/delete! *db* :contact {:id contact-id})))))))
