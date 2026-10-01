(ns sepal.app.routes.location.hierarchy-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [integrant.core :as ig]
            [peridot.core :as peri]
            [sepal.app.test :as app.test]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.location.interface :as location.i]
            [sepal.user.interface :as user.i])
  (:import [org.jsoup Jsoup]))

(use-fixtures :once default-system-fixture)

(def ^:private password "testpassword123")

(defn- tree
  "Hier zone › Hier orchard › Hier row. A function, because *db* is bound by
  the fixture, after this namespace loads."
  []
  {[::user.i/factory :key/user] {:db *db* :password password :role :editor}
   [::location.i/factory :key/zone] {:db *db* :data {:name "Hier zone"}}
   [::location.i/factory :key/orchard] {:db *db* :parent (ig/ref :key/zone)
                                        :data {:name "Hier orchard"}}
   [::location.i/factory :key/row] {:db *db* :parent (ig/ref :key/orchard)
                                    :data {:name "Hier row"}}})

(defn- page [sess path & {:as params}]
  (-> sess
      (peri/request path :params (or params {}))
      :response :body
      (as-> ^String b (Jsoup/parse b))))

(defn- link-to [doc location]
  (.selectFirst doc (str "a[href=\"/location/" (:location/id location) "/\"]")))

(deftest test-panel-names-parent-and-sub-locations
  (tf/testing "a middle location links up to its parent and down to its rows"
    (tree)
    (fn [{:keys [user zone orchard row]}]
      (let [sess (app.test/login (:user/email user) password)
            doc (page sess (str "/location/" (:location/id orchard) "/panel/"))]
        (is (some? (link-to doc zone))
            "the Parent row links to the zone")
        (is (some? (link-to doc row))
            "the Sub-locations section links to the row")
        (is (some? (.selectFirst doc (str "a[href=\"/location/new/?parent-id="
                                          (:location/id orchard) "\"]")))
            "and offers to add another")))))

(deftest test-reader-page-shows-the-same-panel
  (tf/testing "a reader sees the parent and sub-locations too"
    (assoc (tree) [::user.i/factory :key/user] {:db *db* :password password :role :reader})
    (fn [{:keys [user zone orchard row]}]
      (let [sess (app.test/login (:user/email user) password)
            doc (page sess (str "/location/" (:location/id orchard) "/"))]
        (is (some? (link-to doc zone)))
        (is (some? (link-to doc row)))))))

(deftest test-breadcrumb-shows-the-ancestors
  (tf/testing "Locations › Hier zone › Hier orchard › Hier row"
    (tree)
    (fn [{:keys [user zone orchard row]}]
      (let [sess (app.test/login (:user/email user) password)
            crumbs (.select (page sess (str "/location/" (:location/id row) "/general/"))
                            ".spl-crumbs li")]
        (is (= ["Locations" "Hier zone" "Hier orchard" "Hier row"]
               (mapv #(.text %) crumbs)))
        (is (some? (link-to (.get crumbs 1) zone)))
        (is (some? (link-to (.get crumbs 2) orchard)))))))

(deftest test-reader-breadcrumb-shows-the-ancestors
  (tf/testing "the reader page builds its breadcrumb the same way"
    (assoc (tree) [::user.i/factory :key/user] {:db *db* :password password :role :reader})
    (fn [{:keys [user row]}]
      (let [sess (app.test/login (:user/email user) password)
            crumbs (.select (page sess (str "/location/" (:location/id row) "/"))
                            ".spl-crumbs li")]
        (is (= ["Locations" "Hier zone" "Hier orchard" "Hier row"]
               (mapv #(.text %) crumbs)))))))

(deftest test-list-shows-the-parent
  (tf/testing "the Parent column names the parent's path"
    (tree)
    (fn [{:keys [user row]}]
      (let [sess (app.test/login (:user/email user) password)
            doc (page sess "/location/" "q" "Hier")
            tr (.closest (link-to doc row) "tr")]
        (is (re-find #"Hier zone › Hier orchard" (.text tr)))))))

(deftest test-search-by-parent
  (tf/testing "parent.id: finds direct children, and parent: finds them by name"
    (tree)
    (fn [{:keys [user zone orchard row]}]
      (let [sess (app.test/login (:user/email user) password)
            by-id (page sess "/location/" "q" (str "parent.id:" (:location/id zone)))
            by-name (page sess "/location/" "q" "parent:\"Hier orchard\"")]
        (is (some? (link-to by-id orchard)))
        (is (nil? (link-to by-id row)))
        (is (some? (link-to by-name row)))))))

(deftest test-an-archived-panel-offers-no-sub-location
  (tf/testing "an archived location takes no new sub-locations"
    (tree)
    (fn [{:keys [user row]}]
      (location.i/set-status! *db* (:location/id row) :archived)
      (let [sess (app.test/login (:user/email user) password)
            doc (page sess (str "/location/" (:location/id row) "/panel/"))]
        (is (nil? (.selectFirst doc "a[href^=\"/location/new/\"]")))))))
