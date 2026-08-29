(ns sepal.app.routes.activity.index-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [peridot.core :as peri]
            [sepal.app.test :as app.test]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.database.interface :as db.i]
            [sepal.location.interface :as location.i]
            [sepal.location.interface.activity :as location.activity]
            [sepal.settings.interface.activity :as settings.activity]
            [sepal.user.interface :as user.i]))

(use-fixtures :once default-system-fixture)

(def password "testpassword123")

(defn- clear-activity! []
  (db.i/execute! *db* {:delete-from :activity}))

(defmacro with-cleared-activity
  "Run the body against an empty activity table, and empty it again afterwards.

  The system fixture is :once, so rows left behind leak into whichever test runs
  next. The clear has to happen inside the test function rather than in an :each
  fixture: the user factory's teardown runs before that fixture would, and it
  cannot delete a user an activity row still references."
  [& body]
  `(do (clear-activity!)
       (try
         ~@body
         (finally
           (clear-activity!)))))

(defn- get-activity-page [user]
  (-> (app.test/login (:user/email user) password)
      (peri/request "/activity")
      :response))

(deftest test-empty-state-shown-when-there-is-no-activity
  (tf/testing "An editor with an empty feed sees the empty state"
    {[::user.i/factory :key/user] {:db *db*
                                   :password password
                                   :role :editor}}
    (fn [{:keys [user]}]
      (with-cleared-activity
        (let [response (get-activity-page user)]
          (is (app.test/body-contains? response "No activity yet")
              "Empty feed should render the empty state heading"))))))

(deftest test-empty-state-offers-create-links-to-an-editor
  (tf/testing "The empty state links an editor to the first records to create"
    {[::user.i/factory :key/user] {:db *db*
                                   :password password
                                   :role :editor}}
    (fn [{:keys [user]}]
      (with-cleared-activity
        (let [body (app.test/parse-body (get-activity-page user))]
          (is (some? (.selectFirst body "a[href='/location/new/']"))
              "Editor should be offered a link to create a location")
          (is (some? (.selectFirst body "a[href='/accession/new/']"))
              "Editor should be offered a link to create an accession"))))))

(deftest test-empty-state-withholds-create-links-from-a-reader
  (tf/testing "A reader has no create permission, so is offered no create links"
    {[::user.i/factory :key/user] {:db *db*
                                   :password password
                                   :role :reader}}
    (fn [{:keys [user]}]
      (with-cleared-activity
        (let [response (get-activity-page user)
              body (app.test/parse-body response)]
          (is (app.test/body-contains? response "No activity yet")
              "A reader should still see the empty state heading")
          (is (nil? (.selectFirst body "a[href='/location/new/']"))
              "Reader should not be offered a link to create a location")
          (is (nil? (.selectFirst body "a[href='/accession/new/']"))
              "Reader should not be offered a link to create an accession"))))))

(deftest test-empty-state-offers-the-invite-link-to-an-admin
  (tf/testing "Only an admin can invite, so only an admin is offered the link"
    {[::user.i/factory :key/user] {:db *db*
                                   :password password
                                   :role :admin}}
    (fn [{:keys [user]}]
      (with-cleared-activity
        (let [body (app.test/parse-body (get-activity-page user))]
          (is (some? (.selectFirst body "a[href='/settings/users/invite']"))
              "Admin should be offered a link to invite a user"))))))

(deftest test-empty-state-withholds-the-invite-link-from-an-editor
  (tf/testing "An editor cannot invite, so is offered no invite link"
    {[::user.i/factory :key/user] {:db *db*
                                   :password password
                                   :role :editor}}
    (fn [{:keys [user]}]
      (with-cleared-activity
        (let [body (app.test/parse-body (get-activity-page user))]
          (is (nil? (.selectFirst body "a[href='/settings/users/invite']"))
              "Editor should not be offered a link to invite a user"))))))

(deftest test-empty-state-hidden-when-the-feed-renders-an-activity
  (tf/testing "A feed with a renderable activity shows it instead of the empty state"
    {[::user.i/factory :key/user] {:db *db*
                                   :password password
                                   :role :editor}
     [::location.i/factory :key/location] {:db *db*}}
    (fn [{:keys [user location]}]
      (with-cleared-activity
        (location.activity/create! *db*
                                   location.activity/created
                                   (:user/id user)
                                   location)
        (let [response (get-activity-page user)]
          (is (not (app.test/body-contains? response "No activity yet"))
              "A rendered activity should suppress the empty state")
          (is (app.test/body-contains? response (:location/name location))
              "The location activity should be rendered"))))))

(deftest test-empty-state-shown-when-every-activity-is-unrenderable
  (tf/testing "Activity the feed has no renderer for leaves the page blank, so the
  empty state is what should show"
    {[::user.i/factory :key/user] {:db *db*
                                   :password password
                                   :role :editor}}
    (fn [{:keys [user]}]
      (with-cleared-activity
        (settings.activity/create! *db*
                                   settings.activity/updated
                                   (:user/id user)
                                   {:changes {:organization.long_name "Kew"}})
        (let [response (get-activity-page user)]
          (is (app.test/body-contains? response "No activity yet")
              "An activity with no renderer should not suppress the empty state"))))))

(deftest test-empty-state-not-appended-to-a-later-page
  (tf/testing "A later page that comes back empty renders nothing, not the empty
  state: the infinite-scroll sentinel swaps pages in with beforeend, so an empty
  state here would land underneath a populated feed"
    {[::user.i/factory :key/user] {:db *db*
                                   :password password
                                   :role :editor}}
    (fn [{:keys [user]}]
      (with-cleared-activity
        (let [{:keys [response]} (-> (app.test/login (:user/email user) password)
                                     (peri/request "/activity?page=2"
                                                   :headers {"hx-request" "true"}))]
          (is (not (app.test/body-contains? response "No activity yet"))
              "Page 2 should not render the empty state"))))))
