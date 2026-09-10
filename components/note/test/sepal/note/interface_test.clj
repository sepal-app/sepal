(ns sepal.note.interface-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [integrant.core :as ig]
            [matcher-combinators.test :refer [match?]]
            [next.jdbc.sql :as next.jdbc.sql]
            [sepal.accession.interface :as accession.i]
            [sepal.activity.interface :as activity.i]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.contact.interface :as contact.i]
            [sepal.note.interface :as note.i]
            [sepal.note.interface.activity :as note.activity]
            [sepal.taxon.interface :as taxon.i]
            [sepal.user.interface :as user.i]))

(use-fixtures :once default-system-fixture)

(deftest test-create-and-get
  (let [db *db*]
    (tf/testing "create! then get-by-id"
      {[::user.i/factory :key/user] {:db db}
       [::taxon.i/factory :key/taxon] {:db db}}
      (fn [{:keys [user taxon]}]
        (let [created (note.i/create! db {:body "Found dead in the orchid nursery"
                                          :resource-type :taxon
                                          :resource-id (:taxon/id taxon)
                                          :created-by (:user/id user)})]
          (is (match? {:note/id pos-int?
                       :note/body "Found dead in the orchid nursery"
                       :note/resource-type :taxon
                       :note/resource-id (:taxon/id taxon)
                       :note/created-by (:user/id user)
                       :note/created-at string?}
                      created))
          (is (match? {:note/id (:note/id created)}
                      (note.i/get-by-id db (:note/id created))))
          (is (nil? (note.i/get-by-id db 999999)))
          (note.i/delete! db (:note/id created)))))))

(deftest test-get-for-resource-is-scoped-to-its-own-resource
  (let [db *db*]
    (tf/testing "get-for-resource"
      {[::user.i/factory :key/user] {:db db}
       [::contact.i/factory :key/contact] {:db db}
       [::taxon.i/factory :key/taxon] {:db db}
       [::accession.i/factory :key/accession] {:db db
                                               :taxon (ig/ref :key/taxon)
                                               :contact (ig/ref :key/contact)}}
      (fn [{:keys [user taxon accession]}]
        (let [mine (note.i/create! db {:body "on the accession"
                                       :resource-type :accession
                                       :resource-id (:accession/id accession)
                                       :created-by (:user/id user)})
              ;; Same id, different type. The row that catches a query missing
              ;; its resource_type predicate.
              same-id-other-type (note.i/create! db {:body "on the material"
                                                     :resource-type :material
                                                     :resource-id (:accession/id accession)
                                                     :created-by (:user/id user)})
              other-resource (note.i/create! db {:body "on the taxon"
                                                 :resource-type :taxon
                                                 :resource-id (:taxon/id taxon)
                                                 :created-by (:user/id user)})
              found (note.i/get-for-resource db :accession (:accession/id accession))]
          (is (= [(:note/id mine)] (mapv :note/id found)))
          (is (= 1 (note.i/count-for-resource db :accession (:accession/id accession))))
          (is (= (:user/email user) (:note/author-email (first found))))
          (doseq [n [mine same-id-other-type other-resource]]
            (note.i/delete! db (:note/id n))))))))

(deftest test-newest-first
  (let [db *db*]
    (tf/testing "get-for-resource orders newest first"
      {[::user.i/factory :key/user] {:db db}
       [::taxon.i/factory :key/taxon] {:db db}}
      (fn [{:keys [user taxon]}]
        (let [first-note (note.i/create! db {:body "first"
                                             :resource-type :taxon
                                             :resource-id (:taxon/id taxon)
                                             :created-by (:user/id user)})
              second-note (note.i/create! db {:body "second"
                                              :resource-type :taxon
                                              :resource-id (:taxon/id taxon)
                                              :created-by (:user/id user)})]
          ;; created_at has one-second resolution, so id breaks the tie. Two
          ;; notes written in the same second must still come back newest first.
          (is (= ["second" "first"]
                 (mapv :note/body (note.i/get-for-resource db :taxon (:taxon/id taxon)))))
          (note.i/delete! db (:note/id first-note))
          (note.i/delete! db (:note/id second-note)))))))

(deftest test-null-author-round-trips
  (let [db *db*]
    (tf/testing "a note with no created_by"
      {[::taxon.i/factory :key/taxon] {:db db}}
      (fn [{:keys [taxon]}]
        (let [created (note.i/create! db {:body "imported from Bauble"
                                          :resource-type :taxon
                                          :resource-id (:taxon/id taxon)
                                          :created-by nil})
              found (first (note.i/get-for-resource db :taxon (:taxon/id taxon)))]
          (is (nil? (:note/created-by created)))
          (is (nil? (:note/author-email found)))
          (is (= "imported from Bauble" (:note/body found)))
          (note.i/delete! db (:note/id created)))))))

(deftest test-update
  (let [db *db*]
    (tf/testing "update!"
      {[::user.i/factory :key/user] {:db db}
       [::taxon.i/factory :key/taxon] {:db db}}
      (fn [{:keys [user taxon]}]
        (let [created (note.i/create! db {:body "befre the typo was fixed"
                                          :resource-type :taxon
                                          :resource-id (:taxon/id taxon)
                                          :created-by (:user/id user)})
              updated (note.i/update! db (:note/id created) {:body "before the typo was fixed"})]
          (is (= "before the typo was fixed" (:note/body updated)))
          (is (= (:note/resource-type created) (:note/resource-type updated)))
          (note.i/delete! db (:note/id created)))))))

(deftest test-activity
  (let [db *db*]
    (tf/testing "note activity"
      {[::user.i/factory :key/user] {:db db}
       [::taxon.i/factory :key/taxon] {:db db}}
      (fn [{:keys [user taxon]}]
        (try
          (let [note (note.i/create! db {:body "a note worth logging"
                                         :resource-type :taxon
                                         :resource-id (:taxon/id taxon)
                                         :created-by (:user/id user)})
                activity (note.activity/create! db
                                                note.activity/created
                                                (:user/id user)
                                                note)]
            ;; The note's subject is the record it hangs on, not the note.
            (is (match? {:activity/type :note/created
                         :activity/created-by (:user/id user)
                         :activity/resource-type :taxon
                         :activity/resource-id (:taxon/id taxon)
                         :activity/data {:note-id (:note/id note)}}
                        activity))
            (testing "the body is not copied into the activity payload"
              (is (nil? (get-in activity [:activity/data :body]))))
            ;; Until the subject moved into the columns this returned nothing:
            ;; the payload named its parent as the generic :resource-id, and
            ;; the query looked for a literal :taxon-id key. A note's events
            ;; appeared on no record's history at all.
            (testing "the note's event is on its parent's history"
              (is (some #(= :note/created (:activity/type %))
                        (activity.i/get-by-resource db
                                                    :resource-type :taxon
                                                    :resource-id (:taxon/id taxon)))))
            (note.i/delete! db (:note/id note)))
          (finally
            ;; Clean up activity records before user fixture cleanup
            (next.jdbc.sql/delete! db :activity {:created_by (:user/id user)})))))))
