(ns sepal.material.interface-test
  (:require [clojure.test :as test :refer :all]
            [integrant.core :as ig]
            [malli.core :as m]
            [malli.generator :as mg]
            [matcher-combinators.test :refer [match?]]
            [next.jdbc.sql :as jdbc.sql]
            [sepal.accession.interface :as acc.i]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db*
                                           default-system-fixture]]
            [sepal.error.interface :as err.i]
            [sepal.location.interface :as loc.i]
            [sepal.material.interface :as mat.i]
            [sepal.material.interface.spec :as mat.spec]
            [sepal.taxon.interface :as taxon.i]))

(use-fixtures :once default-system-fixture)

(deftest test-get-by-id
  (let [db *db*]
    (tf/testing "material.i/get-by-id"
      {[::taxon.i/factory :key/taxon] {:db db}
       [::acc.i/factory :key/acc] {:db db
                                   :taxon (ig/ref :key/taxon)}
       [::loc.i/factory :key/loc] {:db db}
       [::mat.i/factory :key/mat] {:db db
                                   :accession (ig/ref :key/acc)
                                   :location (ig/ref :key/loc)}}

      (fn [{:keys [mat]}]
        (is (match? mat (mat.i/get-by-id db (:material/id mat))))))))

(deftest test-create
  (let [db *db*]
    (tf/testing "material.i/create!"
      {[::taxon.i/factory :key/taxon] {:db db}
       [::acc.i/factory :key/acc] {:db db
                                   :taxon (ig/ref :key/taxon)}
       [::loc.i/factory :key/loc] {:db db}}
      (fn [{:keys [acc loc]}]
        (let [db *db*
              data (-> (mg/generate mat.spec/CreateMaterial)
                       (assoc :accession-id (:accession/id acc))
                       (assoc :location-id (:location/id loc)))
              ;; The schema CHECK forbids a positive quantity on a non-current
              ;; lot (dead, transferred, other), which the generator otherwise
              ;; produces.
              data (if (contains? #{:alive :dormant :unknown} (:status data))
                     data
                     (assoc data :quantity 0))
              result (mat.i/create! db data)]
          (is (not (err.i/error? result)) (err.i/data result))
          (is (m/validate mat.spec/Material result))
          (is (match? {:material/accession-id (:accession/id acc)
                       :material/location-id (:location/id loc)}
                      result))
          (jdbc.sql/delete! db :material {:id (:material/id result)}))))))

(deftest test-count-by-accession-id
  (let [db *db*]
    (tf/testing "count-by-accession-id returns 0 for accession with no materials"
      {[::taxon.i/factory :key/taxon] {:db db}
       [::acc.i/factory :key/acc] {:db db :taxon (ig/ref :key/taxon)}}
      (fn [{:keys [acc]}]
        (is (= 0 (mat.i/count-by-accession-id db (:accession/id acc))))))

    (tf/testing "count-by-accession-id returns correct count"
      {[::taxon.i/factory :key/taxon] {:db db}
       [::acc.i/factory :key/acc] {:db db :taxon (ig/ref :key/taxon)}
       [::loc.i/factory :key/loc] {:db db}
       [::mat.i/factory :key/mat1] {:db db
                                    :accession (ig/ref :key/acc)
                                    :location (ig/ref :key/loc)}
       [::mat.i/factory :key/mat2] {:db db
                                    :accession (ig/ref :key/acc)
                                    :location (ig/ref :key/loc)}}
      (fn [{:keys [acc]}]
        (is (= 2 (mat.i/count-by-accession-id db (:accession/id acc))))))))

(deftest test-count-by-location-id
  (let [db *db*]
    (tf/testing "count-by-location-id returns 0 for location with no materials"
      {[::loc.i/factory :key/loc] {:db db}}
      (fn [{:keys [loc]}]
        (is (= 0 (mat.i/count-by-location-id db (:location/id loc))))))

    (tf/testing "count-by-location-id returns correct count"
      {[::taxon.i/factory :key/taxon] {:db db}
       [::acc.i/factory :key/acc] {:db db :taxon (ig/ref :key/taxon)}
       [::loc.i/factory :key/loc] {:db db}
       [::mat.i/factory :key/mat1] {:db db
                                    :accession (ig/ref :key/acc)
                                    :location (ig/ref :key/loc)}
       [::mat.i/factory :key/mat2] {:db db
                                    :accession (ig/ref :key/acc)
                                    :location (ig/ref :key/loc)}}
      (fn [{:keys [loc]}]
        (is (= 2 (mat.i/count-by-location-id db (:location/id loc))))))))

(deftest test-count-by-taxon-id
  (let [db *db*]
    (tf/testing "count-by-taxon-id returns 0 for taxon with no materials"
      {[::taxon.i/factory :key/taxon] {:db db}}
      (fn [{:keys [taxon]}]
        (is (= 0 (mat.i/count-by-taxon-id db (:taxon/id taxon))))))

    (tf/testing "count-by-taxon-id returns correct count via accession"
      {[::taxon.i/factory :key/taxon] {:db db}
       [::acc.i/factory :key/acc] {:db db :taxon (ig/ref :key/taxon)}
       [::loc.i/factory :key/loc] {:db db}
       [::mat.i/factory :key/mat1] {:db db
                                    :accession (ig/ref :key/acc)
                                    :location (ig/ref :key/loc)}
       [::mat.i/factory :key/mat2] {:db db
                                    :accession (ig/ref :key/acc)
                                    :location (ig/ref :key/loc)}}
      (fn [{:keys [taxon]}]
        (is (= 2 (mat.i/count-by-taxon-id db (:taxon/id taxon))))))))

(deftest test-quantity-zero-accepted-and-round-trips
  (let [db *db*]
    (tf/testing "a dead plant with quantity 0 passes the spec and round-trips"
      {[::taxon.i/factory :key/taxon] {:db db}
       [::acc.i/factory :key/acc] {:db db :taxon (ig/ref :key/taxon)}
       [::loc.i/factory :key/loc] {:db db}}
      (fn [{:keys [acc loc]}]
        (let [data (-> (mg/generate mat.spec/CreateMaterial)
                       (assoc :accession-id (:accession/id acc)
                              :location-id (:location/id loc)
                              :status :dead
                              :quantity 0))
              result (mat.i/create! db data)]
          (is (not (err.i/error? result)) (err.i/data result))
          (is (m/validate mat.spec/Material result))
          (is (= 0 (:material/quantity result)))
          (is (= :dead (:material/status result)))
          (jdbc.sql/delete! db :material {:id (:material/id result)}))))))

(deftest test-negative-quantity-rejected
  (is (not (m/validate mat.spec/CreateMaterial
                       (-> (mg/generate mat.spec/CreateMaterial)
                           (assoc :quantity -1))))
      "quantity -1 must not pass the spec"))

(deftest test-every-bauble-status-validates
  (doseq [status [:alive :dead :dormant :transferred :other :unknown]]
    (testing (str "status " status)
      (is (m/validate mat.spec/CreateMaterial
                      (-> (mg/generate mat.spec/CreateMaterial)
                          (assoc :status status)))))))

(deftest test-update-location-appends-change
  (let [db *db*]
    (tf/testing "moving material records exactly one change row with both locations"
      {[::taxon.i/factory :key/taxon] {:db db}
       [::acc.i/factory :key/acc] {:db db :taxon (ig/ref :key/taxon)}
       [::loc.i/factory :key/loc1] {:db db}
       [::loc.i/factory :key/loc2] {:db db}
       [::mat.i/factory :key/mat] {:db db
                                   :accession (ig/ref :key/acc)
                                   :location (ig/ref :key/loc1)}}
      (fn [{:keys [mat loc1 loc2]}]
        (let [updated (mat.i/update! db (:material/id mat)
                                     {:location-id (:location/id loc2)
                                      :reason "transferred"})]
          (is (= (:location/id loc2) (:material/location-id updated)))
          (let [changes (mat.i/list-by-material-id db (:material/id mat))]
            (is (= 1 (count changes)))
            (is (match? {:material-change/from-location-id (:location/id loc1)
                         :material-change/to-location-id (:location/id loc2)
                         :material-change/quantity 0
                         :material-change/reason "transferred"}
                        (first changes))))
          ;; The factory halt deletes loc2 before this material, so it must
          ;; stop referencing it first or the FK refuses the delete.
          (jdbc.sql/delete! db :material {:id (:material/id mat)})))

      (tf/testing "updating something that is not a move or quantity change appends nothing"
        {[::taxon.i/factory :key/taxon] {:db db}
         [::acc.i/factory :key/acc] {:db db :taxon (ig/ref :key/taxon)}
         [::loc.i/factory :key/loc] {:db db}
         [::mat.i/factory :key/mat] {:db db
                                     :accession (ig/ref :key/acc)
                                     :location (ig/ref :key/loc)}}
        (fn [{:keys [mat]}]
          (mat.i/update! db (:material/id mat) {:code "NEWCODE"})
          (is (= 0 (count (mat.i/list-by-material-id db (:material/id mat))))))))))

(deftest test-update-quantity-appends-change
  (let [db *db*]
    (tf/testing "a quantity change records the signed delta with no locations"
      {[::taxon.i/factory :key/taxon] {:db db}
       [::acc.i/factory :key/acc] {:db db :taxon (ig/ref :key/taxon)}
       [::loc.i/factory :key/loc] {:db db}}
      (fn [{:keys [acc loc]}]
        (let [mat (mat.i/create! db {:code "Q1"
                                     :accession-id (:accession/id acc)
                                     :location-id (:location/id loc)
                                     :type :plant
                                     :status :alive
                                     :quantity 2})
              _ (mat.i/update! db (:material/id mat)
                               {:quantity 3
                                :reason "distributed"})
              changes (mat.i/list-by-material-id db (:material/id mat))]
          (is (= 1 (count changes)))
          (is (match? {:material-change/from-location-id nil
                       :material-change/to-location-id nil
                       :material-change/quantity 1
                       :material-change/reason "distributed"}
                      (first changes)))
          (jdbc.sql/delete! db :material {:id (:material/id mat)}))))))

(deftest test-update-rolls-back-when-change-row-cannot-be-written
  (let [db *db*]
    (tf/testing "a change row that fails to insert rolls the update back with it"
      {[::taxon.i/factory :key/taxon] {:db db}
       [::acc.i/factory :key/acc] {:db db :taxon (ig/ref :key/taxon)}
       [::loc.i/factory :key/loc1] {:db db}
       [::loc.i/factory :key/loc2] {:db db}
       [::mat.i/factory :key/mat] {:db db
                                   :accession (ig/ref :key/acc)
                                   :location (ig/ref :key/loc1)}}
      (fn [{:keys [mat loc1 loc2]}]
        (is (thrown? org.sqlite.SQLiteException
                     (mat.i/update! db (:material/id mat)
                                    {:location-id (:location/id loc2)
                                     :reason "notareason"}))
            "an unknown reason must be refused by the foreign key")
        (is (= (:location/id loc1)
               (:material/location-id (mat.i/get-by-id db (:material/id mat))))
            "the material update must roll back with the failed change row")
        (is (= 0 (count (mat.i/list-by-material-id db (:material/id mat)))))))))

(deftest test-change-deltas-reconstruct-current-quantity
  (let [db *db*]
    (tf/testing "summing the deltas equals the current quantity"
      {[::taxon.i/factory :key/taxon] {:db db}
       [::acc.i/factory :key/acc] {:db db :taxon (ig/ref :key/taxon)}
       [::loc.i/factory :key/loc1] {:db db}
       [::loc.i/factory :key/loc2] {:db db}}
      (fn [{:keys [acc loc1 loc2]}]
        (let [mat (mat.i/create! db {:code "R1"
                                     :accession-id (:accession/id acc)
                                     :location-id (:location/id loc1)
                                     :type :plant
                                     :status :alive
                                     :quantity 2})
              id (:material/id mat)
              _ (mat.i/create-change! db {:material-id id
                                          :from-location-id nil
                                          :to-location-id (:location/id loc1)
                                          :quantity 2})
              _ (mat.i/update! db id {:quantity 5})
              _ (mat.i/update! db id {:location-id (:location/id loc2)})
              _ (mat.i/update! db id {:quantity 0 :status :dead})
              current (mat.i/get-by-id db id)
              changes (mat.i/list-by-material-id db id)]
          (is (= 0 (:material/quantity current)))
          (is (= 4 (count changes)))
          (is (= 0 (reduce + (map :material-change/quantity changes)))
              "creation +2, increase +3, move 0, death -5")
          (jdbc.sql/delete! db :material {:id id}))))))

(deftest test-create-change-with-nullable-locations
  (let [db *db*]
    (tf/testing "a creation (from null) and a removal (to null) both store and render"
      {[::taxon.i/factory :key/taxon] {:db db}
       [::acc.i/factory :key/acc] {:db db :taxon (ig/ref :key/taxon)}
       [::loc.i/factory :key/loc] {:db db}
       [::mat.i/factory :key/mat] {:db db
                                   :accession (ig/ref :key/acc)
                                   :location (ig/ref :key/loc)}}
      (fn [{:keys [mat loc]}]
        (let [id (:material/id mat)
              creation (mat.i/create-change! db {:material-id id
                                                 :from-location-id nil
                                                 :to-location-id (:location/id loc)
                                                 :quantity 2})
              removal (mat.i/create-change! db {:material-id id
                                                :from-location-id (:location/id loc)
                                                :to-location-id nil
                                                :quantity -2})]
          (is (m/validate mat.spec/MaterialChange creation))
          (is (m/validate mat.spec/MaterialChange removal))
          (let [changes (mat.i/list-by-material-id db id)]
            (is (= 2 (count changes)))
            (is (match? [{:material-change/from-location-id (:location/id loc)
                          :material-change/to-location-id nil}
                         {:material-change/from-location-id nil
                          :material-change/to-location-id (:location/id loc)}]
                        changes)
                "newest first")))))))

(deftest test-list-reasons
  (let [reasons (mat.i/list-reasons *db*)]
    (is (= 15 (count reasons)))
    (is (= (set (map :material-change-reason/code reasons))
           #{"dead" "discarded" "discarded_weedy" "lost" "stolen"
             "winter_kill" "summer_kill" "error_correction"
             "distributed" "deleted" "did_not_germinate"
             "discarded_seedling" "given_away" "transferred" "other"}))
    (is (contains? (set (map :material-change-reason/label reasons))
                   "Winter kill"))))
