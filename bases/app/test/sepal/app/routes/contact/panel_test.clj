(ns sepal.app.routes.contact.panel-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [peridot.core :as peri]
            [sepal.app.test :as app.test]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.contact.interface :as contact.i]
            [sepal.user.interface :as user.i]))

(use-fixtures :once default-system-fixture)

(defn- panel-body
  "The panel fragment as the browser gets it.

  Requested through the real route rather than by calling `panel-content`
  directly: the panel renders `z/url-for` links, which need the reitit router
  bound on the request, so it cannot be rendered outside one."
  [sess contact-id]
  (-> sess
      (peri/request (format "/contact/%s/panel/" contact-id))
      :response :body))

(deftest test-a-contacts-split-address-appears-in-the-panel
  (tf/testing "the panel is the whole record for a reader, so the address has
               to be legible here and not only in the form"
    {[::user.i/factory :key/user] {:db *db* :password "testpassword123" :role :admin}}
    (fn [{:keys [user]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            contact (contact.i/create! *db* {:name "Fairchild Tropical Garden"
                                             :address1 "10901 Old Cutler Road"
                                             :address2 "Attn: Herbarium"
                                             :city "Coral Gables"
                                             :province "Florida"
                                             :country "USA"})
            body (panel-body sess (:contact/id contact))]
        (is (re-find #"Address" body))
        (is (re-find #"10901 Old Cutler Road" body))
        (is (re-find #"Attn: Herbarium" body))
        (is (re-find #"Coral Gables" body))))))

(deftest test-a-legacy-one-line-address-still-shows
  ;; No parser splits the old column, so a contact saved before the split keeps
  ;; its address in `address` — and it has to stay readable.
  (tf/testing "a contact with only the old address field"
    {[::user.i/factory :key/user] {:db *db* :password "testpassword123" :role :admin}}
    (fn [{:keys [user]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            contact (contact.i/create! *db* {:name "Kew Gardens"
                                             :address "Richmond, London TW9 3AE"})
            body (panel-body sess (:contact/id contact))]
        (is (re-find #"Richmond, London TW9 3AE" body))))))

(deftest test-the-address-section-is-present-when-empty
  ;; Present but disabled, matching Statistics and Activity — a section that
  ;; vanishes makes the panel's shape vary between contacts.
  (tf/testing "a contact with no address at all"
    {[::user.i/factory :key/user] {:db *db* :password "testpassword123" :role :admin}}
    (fn [{:keys [user]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            contact (contact.i/create! *db* {:name "No Address Nursery"})
            body (panel-body sess (:contact/id contact))]
        (is (re-find #"Address" body) "the section is present, not absent")))))

(deftest test-city-is-a-column-on-the-contact-list
  (tf/testing "city gets its own column now that it is its own field"
    {[::user.i/factory :key/user] {:db *db* :password "testpassword123" :role :admin}}
    (fn [{:keys [user]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            _ (contact.i/create! *db* {:name "Fairchild Tropical Garden"
                                       :city "Coral Gables"})
            body (-> sess (peri/request "/contact/") :response :body)]
        (is (re-find #"City" body) "the column header")
        (testing "and the value reaches the cell"
          (is (re-find #"Coral Gables" body)))))))
