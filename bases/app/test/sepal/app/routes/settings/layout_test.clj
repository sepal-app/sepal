(ns sepal.app.routes.settings.layout-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is use-fixtures]]
            [peridot.core :as peri]
            [sepal.app.test :as app.test]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.user.interface :as user.i])
  (:import [org.jsoup Jsoup]))

(use-fixtures :once default-system-fixture)

(defn- fixtures []
  {[::user.i/factory :key/user] {:db *db*
                                 :password "testpassword123"
                                 :role :admin}})

(defn- settings-page [user path]
  (let [sess (app.test/login (:user/email user) "testpassword123")
        {:keys [response]} (-> sess (peri/request path))]
    (Jsoup/parse ^String (:body response))))

(deftest test-settings-nav-is-a-landmark
  (tf/testing "the settings aside is a third navigation surface and needs its
               own accessible name to be distinguishable from the section rail"
    (fixtures)
    (fn [{:keys [user]}]
      (let [body (settings-page user "/settings/profile")]
        (is (some? (.selectFirst body "nav[aria-label='Settings sections']")))))))

(deftest test-current-settings-page-is-marked
  (tf/testing "the settings sidebar already honoured current? — it is the
               model ui/page.clj's sidebar-item should have followed"
    (fixtures)
    (fn [{:keys [user]}]
      (let [body (settings-page user "/settings/profile")
            current (.selectFirst body "nav[aria-label='Settings sections'] [aria-current=page]")]
        (is (some? current))
        (is (= "Profile" (str/trim (.text current))))))))

(deftest test-settings-nav-marks-a-different-page
  (tf/testing "marking follows the request rather than being hardcoded"
    (fixtures)
    (fn [{:keys [user]}]
      (let [body (settings-page user "/settings/security")
            current (.selectFirst body "nav[aria-label='Settings sections'] [aria-current=page]")]
        (is (some? current))
        (is (= "Security" (str/trim (.text current))))))))

(deftest test-section-rail-marks-settings-too
  (tf/testing "both navigation levels agree: the rail marks Settings while the
               aside marks the page within it"
    (fixtures)
    (fn [{:keys [user]}]
      (let [body (settings-page user "/settings/security")
            rail-current (.selectFirst body "nav[aria-label=Sections] [aria-current=page]")]
        (is (some? rail-current))
        (is (= "Settings" (.attr rail-current "aria-label")))))))

(deftest test-settings-nav-emits-no-hardcoded-palette
  (tf/testing "layout.clj used text-gray-500 for its group headings"
    (fixtures)
    (fn [{:keys [user]}]
      (let [nav (.selectFirst (settings-page user "/settings/profile")
                              "nav[aria-label='Settings sections']")
            html (.outerHtml nav)]
        (doseq [cls ["text-gray-500" "bg-base-300" "bg-base-200"]]
          (is (not (str/includes? html cls))
              (str "settings nav still emits " cls)))))))
