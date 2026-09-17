(ns sepal.contact.interface-test
  (:require [clojure.test :as test :refer :all]
            [malli.core :as m]
            [next.jdbc :as jdbc]
            [next.jdbc.sql :as jdbc.sql]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.contact.interface :as contact.i]
            [sepal.contact.interface.activity :as contact.activity]
            [sepal.contact.interface.spec :as contact.spec]
            [sepal.database.interface :as db.i]
            [sepal.error.interface :as err.i]
            [sepal.user.interface :as user.i]))

(use-fixtures :once default-system-fixture)

(def contact-types
  "Bauble's 12 source_type values, spelled as Sepal enum keywords."
  [:expedition :staff :commercial :gene_bank :university_department
   :individual :botanic_garden :club :other :research_station
   :municipal_department :unknown])

(def sepal-contact-types
  "The three Sepal adds beyond Bauble's list. A garden that buys from
  nurseries and swaps with seed banks outgrew the vocabulary an old desktop
  application shipped with."
  [:nursery :seed_bank :arboretum])

(deftest test-type-enum-validates-every-bauble-value
  (doseq [t contact-types]
    (is (m/validate contact.spec/CreateContact {:name "Fairchild" :type t})
        (str "CreateContact should accept " t))
    (is (m/validate contact.spec/UpdateContact {:type t})
        (str "UpdateContact should accept " t)))
  (is (not (m/validate contact.spec/CreateContact {:name "Fairchild" :type :not_a_type}))
      "an invalid type must not pass the spec"))

(deftest test-type-roundtrips
  (let [db *db*
        created (contact.i/create! db {:name "Fairchild Tropical Gardens"
                                       :type :botanic_garden})]
    (is (not (err.i/error? created)) (err.i/data created))
    (is (= :botanic_garden (:contact/type created)))
    (is (m/validate contact.spec/Contact created))
    (let [fetched (contact.i/get-by-id db (:contact/id created))]
      (is (= :botanic_garden (:contact/type fetched))
          "the keyword round-trips through the store")
      (let [updated (contact.i/update! db (:contact/id created)
                                       {:type :expedition})]
        (is (not (err.i/error? updated)) (err.i/data updated))
        (is (= :expedition (:contact/type updated)))))))

(deftest test-type-absent-when-not-set
  (let [db *db*
        created (contact.i/create! db {:name "No Type"})]
    (is (not (err.i/error? created)) (err.i/data created))
    (is (nil? (:contact/type created))
        "a contact with no type is unaffected")
    (is (m/validate contact.spec/Contact created))))

(deftest test-contact-fts-still-syncs
  ;; The migration adds a column to contact, which has an external-content FTS
  ;; table and three triggers hanging off it. The triggers name their columns
  ;; explicitly, so they should be indifferent to the new column -- asserted
  ;; rather than assumed.
  (let [db *db*
        created (contact.i/create! db {:name "Botanic Gardens Conservation"
                                       :business "BGCI"
                                       :type :botanic_garden})
        id (:contact/id created)]
    (is (not (err.i/error? created)) (err.i/data created))
    (let [fts (jdbc/execute-one! db ["select name, business, email from contact_fts where rowid = ?" id])]
      (is (= "Botanic Gardens Conservation" (:contact-fts/name fts))
          "the insert trigger still populates contact_fts")
      (is (= "BGCI" (:contact-fts/business fts))))
    (contact.i/update! db id {:business "BGCI Kew"})
    (let [fts (jdbc/execute-one! db ["select name, business, email from contact_fts where rowid = ?" id])]
      (is (= "BGCI Kew" (:contact-fts/business fts))
          "the update trigger still syncs contact_fts"))
    (jdbc.sql/delete! db :contact {:id id})
    (is (nil? (jdbc/execute-one! db ["select rowid from contact_fts where rowid = ?" id]))
        "the delete trigger still removes from contact_fts")))

(deftest test-activity-for-a-contact-with-no-business
  ;; This threw out of m/coerce, failing the transaction that creates the
  ;; contact. The form masked it: `business` is the one optional text field
  ;; without an empty->nil decoder, so a blank arrives as "" rather than nil.
  (tf/testing "an activity about a contact with no business"
    {[::user.i/factory :key/user] {:db *db*}}
    (fn [{:keys [user]}]
      (let [db *db*
            contact (contact.i/create! db {:name "Jane Doe"})]
        (try
          (is (nil? (:contact/business contact))
              "the contact was created without one")
          (let [activity (contact.activity/create! db
                                                   contact.activity/created
                                                   (:user/id user)
                                                   contact)]
            (is (= :contact/created (:activity/type activity)))
            (is (= :contact (:activity/resource-type activity)))
            (is (= (:contact/id contact) (:activity/resource-id activity)))
            (is (nil? (get-in activity [:activity/data :contact-business]))))
          (finally
            (jdbc.sql/delete! db :activity {:created_by (:user/id user)})
            (jdbc.sql/delete! db :contact {:id (:contact/id contact)})))))))

(deftest test-delete
  (let [db *db*]
    (tf/testing "contact.i/delete!"
      {[::contact.i/factory :key/contact] {:db db}}
      (fn [{:keys [contact]}]
        (let [id (:contact/id contact)]
          (is (some? (contact.i/get-by-id db id)))
          (contact.i/delete! db id)
          (is (nil? (contact.i/get-by-id db id))))))))

(deftest test-the-enum-and-the-lookup-table-say-the-same-thing
  (testing "contact_type is what the database enforces and contact.spec/type is
            what a read coerces against. A value in one and not the other is
            either a row nobody can save or a 500 on the contact page, and
            neither announces itself until someone hits it."
    (let [in-table (->> (db.i/execute! *db* {:select [:name] :from [:contact_type]})
                        (map :contact-type/name)
                        (set))
          in-spec (->> (m/children contact.spec/type)
                       (map name)
                       (set))]
      (is (= in-spec in-table)
          (str "only in the spec: " (sort (remove in-table in-spec))
               "; only in the table: " (sort (remove in-spec in-table)))))))

(deftest test-a-type-the-vocabulary-does-not-know-is-refused
  ;; The failure this replaces: an unknown value wrote without complaint and
  ;; came back as a coercion error on every later read of that contact.
  (tf/testing "the database rejects it rather than storing it"
    {[::user.i/factory :key/user] {:db *db*}}
    (fn [_]
      (is (thrown? Exception
                   (jdbc.sql/insert! *db* :contact {:name "Bad Co"
                                                    :type "not_a_type"}))))))

(deftest test-the-types-sepal-adds-beyond-bauble-validate
  (doseq [t sepal-contact-types]
    (is (m/validate contact.spec/CreateContact {:name "Fairchild" :type t})
        (str "CreateContact should accept " t))))
