(ns sepal.contact.interface-test
  (:require [clojure.test :as test :refer :all]
            [malli.core :as m]
            [next.jdbc :as jdbc]
            [next.jdbc.sql :as jdbc.sql]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.contact.interface :as contact.i]
            [sepal.contact.interface.spec :as contact.spec]
            [sepal.error.interface :as err.i]))

(use-fixtures :once default-system-fixture)

(def contact-types
  "Bauble's 12 source_type values, spelled as Sepal enum keywords."
  [:expedition :staff :commercial :gene_bank :university_department
   :individual :botanic_garden :club :other :research_station
   :municipal_department :unknown])

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
