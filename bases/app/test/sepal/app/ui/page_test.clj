(ns sepal.app.ui.page-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [dev.onionpancakes.chassis.core :as chassis]
            [peridot.core :as peri]
            [sepal.app.test :as app.test]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.app.ui.page :as page]
            [sepal.user.interface :as user.i])
  (:import [org.jsoup Jsoup]))

(use-fixtures :once default-system-fixture)

(defn- item [& {:as opts}]
  (Jsoup/parseBodyFragment
    (chassis/html (page/sidebar-item :href "/accession/"
                                     :label "Accessions"
                                     :icon [:svg {:viewBox "0 0 24 24"}]
                                     :current? (:current? opts false)))))

(deftest test-current-item-is-marked
  (testing "regression: sidebar-item accepted current? and never bound it, so
            all eight call sites passed a value that did nothing"
    (is (some? (.selectFirst (item :current? true) "a[aria-current=page]")))))

(deftest test-non-current-item-is-not-marked
  (is (nil? (.selectFirst (item :current? false) "a[aria-current=page]"))))

(deftest test-item-has-an-accessible-name
  (testing "the visible label is display:none while the rail is collapsed, so
            it cannot supply the accessible name"
    (let [a (.selectFirst (item) "a")]
      (is (= "Accessions" (.attr a "aria-label"))))))

(deftest test-decorative-icon-is-hidden-from-assistive-tech
  (is (some? (.selectFirst (item) "[aria-hidden=true]"))))

(deftest test-item-emits-no-daisyui-classes
  (let [html (chassis/html (page/sidebar-item :href "/x/" :label "X"
                                              :icon [:svg] :current? false))]
    (doseq [cls ["tooltip" "is-drawer-close" "menu"]]
      (is (not (str/includes? html cls))
          (str "sidebar-item still emits " cls)))))

(deftest test-page-marks-the-current-section
  (tf/testing "the rendered page marks the section the request is in"
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :editor}}
    (fn [{:keys [user]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response]} (-> sess (peri/request "/accession/"))
            body (Jsoup/parse ^String (:body response))
            current (.select body "a[aria-current=page]")]
        (is (= 200 (:status response)))
        (is (= 1 (.size current))
            "exactly one nav item is current")
        (is (= "/accession/" (.attr (.first current) "href")))))))

(deftest test-current-marking-survives-lazy-rendering
  (tf/testing "regression: the rail's items are built by `for`, a lazy seq that
               Chassis realises while writing the response — after the dynamic
               binding carrying the URI has unwound. The value must be captured
               eagerly and closed over."
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :editor}}
    (fn [{:keys [user]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")]
        (doseq [[uri label] [["/accession/" "Accessions"]
                             ["/taxon/" "Taxa"]
                             ["/location/" "Locations"]]]
          (let [{:keys [response]} (-> sess (peri/request uri))
                body (Jsoup/parse ^String (:body response))
                current (.selectFirst body "nav[aria-label=Sections] a[aria-current=page]")]
            (is (some? current) (str "no current item on " uri))
            (when current
              (is (= label (.attr current "aria-label"))))))))))

(deftest test-navigation-is-a-landmark
  (tf/testing "the rail is a nav with an accessible name"
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :editor}}
    (fn [{:keys [user]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response]} (-> sess (peri/request "/accession/"))
            body (Jsoup/parse ^String (:body response))]
        (is (some? (.selectFirst body "nav[aria-label]"))
            "a nav landmark lets a screen reader jump to navigation")))))
