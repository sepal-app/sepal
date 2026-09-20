(ns sepal.observation.interface-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [integrant.core :as ig]
            [matcher-combinators.test :refer [match?]]
            [next.jdbc.sql :as next.jdbc.sql]
            [sepal.accession.interface :as accession.i]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.contact.interface :as contact.i]
            [sepal.location.interface :as location.i]
            [sepal.material.interface :as material.i]
            [sepal.observation.interface :as observation.i]
            [sepal.observation.interface.activity :as observation.activity]
            [sepal.taxon.interface :as taxon.i]
            [sepal.user.interface :as user.i]))

(use-fixtures :once default-system-fixture)

;; A function rather than a top-level def: *db* is only bound once
;; default-system-fixture's `binding` wraps a running test, and a top-level
;; def evaluates at namespace load, well before that binding exists.
(defn- material-fixtures [db]
  {[::user.i/factory :key/user] {:db db}
   [::taxon.i/factory :key/taxon] {:db db}
   [::contact.i/factory :key/contact] {:db db}
   [::location.i/factory :key/location] {:db db}
   [::accession.i/factory :key/accession] {:db db
                                           :taxon (ig/ref :key/taxon)
                                           :contact (ig/ref :key/contact)}
   ;; location-id is a required pos-int? on CreateMaterial, so the factory
   ;; needs a location the same way every other caller supplies one.
   [::material.i/factory :key/material] {:db db
                                         :accession (ig/ref :key/accession)
                                         :location (ig/ref :key/location)}})

(deftest test-create-and-get
  (let [db *db*]
    (tf/testing "create! then get-by-id"
      (material-fixtures db)
      (fn [{:keys [user material]}]
        (let [created (observation.i/create!
                        db {:resource-type :material
                            :resource-id (:material/id material)
                            :type "phenology"
                            :value "flowering"
                            :observed-on "2026-03-14"
                            :observed-by "A volunteer"
                            :created-by (:user/id user)})]
          (is (match? {:observation/id pos-int?
                       :observation/resource-type :material
                       :observation/type "phenology"
                       :observation/value "flowering"
                       :observation/observed-on "2026-03-14"
                       :observation/observed-by "A volunteer"}
                      created))
          (is (match? {:observation/id (:observation/id created)}
                      (observation.i/get-by-id db (:observation/id created))))
          (is (nil? (observation.i/get-by-id db 999999)))
          (observation.i/delete! db (:observation/id created)))))))

(deftest test-a-value-from-another-type-is-refused
  (let [db *db*]
    (tf/testing "the composite foreign key rejects a mismatched pair"
      (material-fixtures db)
      (fn [{:keys [user material]}]
        ;; org.sqlite.SQLiteException, not a bare Exception: the point of this
        ;; test is that the database refuses the pair, so it must fail if the
        ;; refusal ever moved to spec coercion instead.
        (is (thrown? org.sqlite.SQLiteException
                     (observation.i/create!
                       db {:resource-type :material
                           :resource-id (:material/id material)
                           ;; `severe` belongs to pest and disease, never to
                           ;; phenology. The database is what refuses this.
                           :type "phenology"
                           :value "severe"
                           :observed-on "2026-03-14"
                           :created-by (:user/id user)})))))))

(deftest test-a-general-observation-needs-no-value
  (let [db *db*]
    (tf/testing "general carries prose and no value"
      (material-fixtures db)
      (fn [{:keys [user material]}]
        (let [created (observation.i/create!
                        db {:resource-type :material
                            :resource-id (:material/id material)
                            :type "general"
                            :observed-on "2026-03-14"
                            :note "Label needs replacing"
                            :created-by (:user/id user)})]
          (is (match? {:observation/type "general"
                       :observation/value nil
                       :observation/note "Label needs replacing"}
                      created))
          (observation.i/delete! db (:observation/id created)))))))

(deftest test-update
  (let [db *db*]
    (tf/testing "update!"
      (material-fixtures db)
      (fn [{:keys [user material]}]
        (let [created (observation.i/create!
                        db {:resource-type :material
                            :resource-id (:material/id material)
                            :type "condition"
                            :value "fair"
                            :observed-on "2026-03-14"
                            :created-by (:user/id user)})
              updated (observation.i/update!
                        db (:observation/id created) {:value "good"})]
          (is (= "good" (:observation/value updated)))
          (is (= (:observation/type created) (:observation/type updated)))
          (is (= (:observation/observed-on created) (:observation/observed-on updated)))
          (observation.i/delete! db (:observation/id created)))))))

(deftest test-get-for-resource-is-scoped-to-its-own-resource
  (let [db *db*]
    (tf/testing "get-for-resource"
      (material-fixtures db)
      (fn [{:keys [user material location]}]
        (let [on-material (observation.i/create!
                            db {:resource-type :material
                                :resource-id (:material/id material)
                                :type "condition"
                                :value "good"
                                :observed-on "2026-03-14"
                                :created-by (:user/id user)})
              on-location (observation.i/create!
                            db {:resource-type :location
                                :resource-id (:location/id location)
                                :type "pest"
                                :value "moderate"
                                :observed-on "2026-03-15"
                                :created-by (:user/id user)})]
          (is (match? [{:observation/id (:observation/id on-material)}]
                      (observation.i/get-for-resource
                        db :material (:material/id material))))
          (is (match? [{:observation/id (:observation/id on-location)}]
                      (observation.i/get-for-resource
                        db :location (:location/id location))))
          (is (= 1 (observation.i/count-for-resource
                     db :material (:material/id material))))
          (observation.i/delete! db (:observation/id on-material))
          (observation.i/delete! db (:observation/id on-location)))))))

(deftest test-get-for-resource-orders-newest-observed-first
  (let [db *db*]
    (tf/testing "get-for-resource"
      (material-fixtures db)
      (fn [{:keys [user material]}]
        (let [base {:resource-type :material
                    :resource-id (:material/id material)
                    :type "general"
                    :created-by (:user/id user)}
              earliest (observation.i/create!
                         db (assoc base :observed-on "2026-03-01" :note "earliest"))
              ;; Two observations on the same day: id is what breaks the tie,
              ;; since SQLite's datetime('now') has one-second resolution and
              ;; created_at cannot.
              same-day-first (observation.i/create!
                               db (assoc base :observed-on "2026-03-10" :note "same day, created first"))
              same-day-second (observation.i/create!
                                db (assoc base :observed-on "2026-03-10" :note "same day, created second"))
              latest (observation.i/create!
                       db (assoc base :observed-on "2026-03-15" :note "latest"))]
          (is (= [(:observation/id latest) (:observation/id same-day-second)
                  (:observation/id same-day-first) (:observation/id earliest)]
                 (mapv :observation/id
                       (observation.i/get-for-resource db :material (:material/id material)))))
          (doseq [o [earliest same-day-first same-day-second latest]]
            (observation.i/delete! db (:observation/id o))))))))

(deftest test-due-returns-only-rows-on-or-before-the-date
  (let [db *db*]
    (tf/testing "due"
      (material-fixtures db)
      (fn [{:keys [user material]}]
        (let [base {:resource-type :material
                    :resource-id (:material/id material)
                    :type "condition"
                    :value "fair"
                    :observed-on "2026-03-01"
                    :created-by (:user/id user)}
              overdue (observation.i/create!
                        db (assoc base :next-check-on "2026-03-10"))
              today (observation.i/create!
                      db (assoc base :next-check-on "2026-03-14"))
              later (observation.i/create!
                      db (assoc base :next-check-on "2026-04-01"))
              ;; No next-check-on at all. The row a `<=` with no null guard
              ;; would silently include or exclude depending on the dialect.
              never (observation.i/create! db base)
              results (observation.i/due db "2026-03-14")
              ids (set (map :observation/id results))]
          (is (contains? ids (:observation/id overdue)))
          (is (contains? ids (:observation/id today)))
          (is (not (contains? ids (:observation/id later))))
          (is (not (contains? ids (:observation/id never))))
          (is (= [(:observation/id overdue) (:observation/id today)]
                 (mapv :observation/id results))
              "oldest first")
          (doseq [o [overdue today later never]]
            (observation.i/delete! db (:observation/id o))))))))

(deftest test-delete-for-resource-clears-only-that-resource
  (let [db *db*]
    (tf/testing "delete-for-resource!"
      (material-fixtures db)
      (fn [{:keys [user material location]}]
        (let [on-material (observation.i/create!
                            db {:resource-type :material
                                :resource-id (:material/id material)
                                :type "general"
                                :observed-on "2026-03-14"
                                :note "goes away"
                                :created-by (:user/id user)})
              on-location (observation.i/create!
                            db {:resource-type :location
                                :resource-id (:location/id location)
                                :type "general"
                                :observed-on "2026-03-14"
                                :note "stays"
                                :created-by (:user/id user)})]
          (observation.i/delete-for-resource! db :material (:material/id material))
          (is (nil? (observation.i/get-by-id db (:observation/id on-material))))
          (is (some? (observation.i/get-by-id db (:observation/id on-location))))
          (observation.i/delete! db (:observation/id on-location)))))))

(deftest test-activity-names-the-subject-not-the-observation
  (let [db *db*]
    (tf/testing "an event points at the material, so the record's history shows it"
      (material-fixtures db)
      (fn [{:keys [user material]}]
        (try
          (let [created (observation.i/create!
                          db {:resource-type :material
                              :resource-id (:material/id material)
                              :type "phenology"
                              :value "flowering"
                              :observed-on "2026-03-14"
                              :created-by (:user/id user)})
                event (observation.activity/create!
                        db observation.activity/created (:user/id user) created)]
            (is (match? {:activity/type :observation/created
                         :activity/resource-type :material
                         :activity/resource-id (:material/id material)
                         :activity/data {:observation-id (:observation/id created)}}
                        event))
            (observation.i/delete! db (:observation/id created)))
          (finally
            ;; Clean up activity records before user fixture cleanup
            (next.jdbc.sql/delete! db :activity {:created_by (:user/id user)})))))))
