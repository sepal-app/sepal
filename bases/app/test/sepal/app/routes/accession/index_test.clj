(ns sepal.app.routes.accession.index-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [integrant.core :as ig]
            [peridot.core :as peri]
            [sepal.accession.interface :as accession.i]
            [sepal.app.test :as app.test]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
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
               the row; the stacked text comes from data-stacked"
    (fixtures)
    (fn [{:keys [user]}]
      (let [body (list-page user)
            cell (.selectFirst body "td.spl-col--identifier")]
        (is (some? cell))
        (is (seq (.attr cell "data-stacked"))
            "the identifier cell carries the phone-width summary")))))
