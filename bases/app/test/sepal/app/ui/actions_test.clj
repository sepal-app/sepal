(ns sepal.app.ui.actions-test
  (:require [clojure.test :refer [deftest is testing]]
            [dev.onionpancakes.chassis.core :as chassis]
            [sepal.app.ui.actions :as ui.actions])
  (:import [org.jsoup Jsoup]))

(defn- parse [hiccup] (Jsoup/parseBodyFragment (chassis/html hiccup)))

(deftest test-nothing-to-show-renders-nothing
  (is (nil? (ui.actions/menu))
      "a page with no actions gets no empty bar"))

(deftest test-items-render-in-the-order-given
  (let [body (parse (ui.actions/menu
                      :items [{:label "Add an accession" :href "/accession/new/?taxon-id=1"}
                              {:label "Add a child taxon" :href "/taxon/new/?parent-id=1"}]))
        items (.select body "[role=menuitem]")]
    (is (= ["Add an accession" "Add a child taxon"]
           (mapv #(.text %) items)))
    (is (= "/accession/new/?taxon-id=1" (.attr (.first items) "href")))))

(deftest test-delete-is-last-and-marked-dangerous
  (let [body (parse (ui.actions/menu
                      :items [{:label "Add material" :href "/material/new/"}]
                      :delete-url "/accession/1/delete/"))
        items (.select body "[role=menuitem]")
        delete (.last items)]
    (is (= "Delete" (.text delete)))
    (is (.hasClass delete "spl-menu-item--danger"))
    (is (= "/accession/1/delete/" (.attr delete "hx-get"))
        "the dialog is fetched, so the blockers are computed when it opens")
    (testing "and a rule separates it from the rest"
      (is (some? (.selectFirst body ".spl-menu-sep"))))))

(deftest test-delete-brings-its-modal-container
  (let [body (parse (ui.actions/menu :delete-url "/location/1/delete/"))]
    (is (some? (.selectFirst body "#delete-modal-container"))
        "the confirmation has nowhere to swap into without it")
    (is (nil? (.selectFirst body ".spl-menu-sep"))
        "no rule when Delete is the only item")))

(deftest test-primary-stays-outside-the-menu
  (let [body (parse (ui.actions/menu
                      :primary [:button {:id "upload-button"} "Upload"]
                      :delete-url "/taxon/1/delete/"))
        bar (.selectFirst body ".spl-actions")]
    (is (some? (.selectFirst bar "> #upload-button"))
        "Upload is a direct child of the bar, not an entry in the panel")
    (is (nil? (.selectFirst body ".spl-actions-panel #upload-button")))))

(deftest test-the-panel-is-hidden-before-alpine-boots
  (let [body (parse (ui.actions/menu :items [{:label "Add" :href "/x"}]))
        panel (.selectFirst body ".spl-actions-panel")]
    (is (= "open" (.attr panel "x-show")))
    (is (= "display: none;" (.attr panel "style"))
        "x-show alone leaves the panel painted over the page on first render")))
