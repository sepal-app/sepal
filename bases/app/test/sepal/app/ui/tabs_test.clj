(ns sepal.app.ui.tabs-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [dev.onionpancakes.chassis.core :as chassis]
            [sepal.app.ui.tabs :as tabs])
  (:import [org.jsoup Jsoup]))

(defn- parse [hiccup] (Jsoup/parseBodyFragment (chassis/html hiccup)))

(defn- nav []
  (parse (tabs/tabs {:label "Accession sections"
                     :items [(tabs/item "General" {:href "/a/1/general/" :active true})
                             (tabs/item "Collection" {:href "/a/1/collection/"
                                                      :disabled "Available when provenance is wild collected"})
                             (tabs/item "Media" {:href "/a/1/media/"})]})))

(deftest test-tabs-are-navigation-not-a-tablist
  (testing "these are links to separate documents. role=tablist promises a
            screen reader a panel in THIS document that the tab controls,
            which is not what happens — the browser navigates away."
    (let [body (nav)]
      (is (some? (.selectFirst body "nav[aria-label='Accession sections']")))
      (is (nil? (.selectFirst body "[role=tablist]")))
      (is (nil? (.selectFirst body "[role=tab]"))))))

(deftest test-active-tab-is-marked-with-aria-current
  (let [body (nav)
        current (.selectFirst body "[aria-current=page]")]
    (is (some? current))
    (is (= "General" (str/trim (.text current))))))

(deftest test-disabled-tab-is-not-a-link
  (testing "a disabled destination must not be focusable or activatable"
    (let [body (nav)
          disabled (.selectFirst body "[aria-disabled=true]")]
      (is (some? disabled))
      (is (not= "a" (.tagName disabled))
          "rendered as a span, so there is no href to follow")
      (is (str/includes? (.text disabled) "Collection")))))

(deftest test-disabled-tab-explains-itself-accessibly
  (testing "a CSS ::after tooltip reaches neither keyboard nor screen reader"
    (let [body (nav)
          disabled (.selectFirst body "[aria-disabled=true]")
          described-by (.attr disabled "aria-describedby")]
      (is (seq described-by))
      (let [reason (.selectFirst body (str "#" described-by))]
        (is (some? reason) "the reason is a real element in the document")
        (is (str/includes? (.text reason) "wild collected"))))))

(deftest test-enabled-tabs-are-links
  (let [body (nav)]
    (is (some? (.selectFirst body "a[href='/a/1/media/']")))))

(deftest test-emits-no-daisyui
  (let [html (chassis/html (tabs/tabs {:label "X"
                                       :items [(tabs/item "A" {:href "/a"})]}))]
    (doseq [cls ["tabs-box" "tab-active" "text-accent" "text-primary-content"]]
      (is (not (str/includes? html cls))
          (str "tabs still emit " cls)))))
