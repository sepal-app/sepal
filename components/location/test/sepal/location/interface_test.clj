(ns sepal.location.interface-test
  (:require [clojure.test :as test :refer :all]
            [integrant.core :as ig]
            [malli.core :as m]
            [malli.generator :as mg]
            [matcher-combinators.test :refer [match?]]
            [next.jdbc.sql :as jdbc.sql]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db*
                                           default-system-fixture]]
            [sepal.database.interface :as db.i]
            [sepal.error.interface :as err.i]
            [sepal.location.interface :as loc.i]
            [sepal.location.interface.spec :as loc.spec]))

(use-fixtures :once default-system-fixture)

(deftest test-create
  (tf/testing "loc.i/create!"
    (let [db *db*
          ;; A generated parent id names no row, and the foreign key would
          ;; refuse it.
          data (dissoc (mg/generate loc.spec/CreateLocation) :parent-id)
          result (loc.i/create! db data)]
      (is (not (err.i/error? result)) (err.i/data result))
      (is (m/validate loc.spec/Location result))
      #_(is (match? {:location/organization-id (:organization/id org)}
                    result))
      (jdbc.sql/delete! db :location {:id (:location/id result)}))))

(deftest test-update
  (let [db *db*]
    (tf/testing "loc.i/update!"
      {[::loc.i/factory :key/loc] {:db db}}
      (fn [{:keys [loc]}]
        (let [code (mg/generate loc.spec/code)
              result (loc.i/update! db
                                    (:location/id loc)
                                    {:code code})]
          (is (not (err.i/error? result)) (err.i/data result))
          (is (m/validate loc.spec/Location result))
          (is (match? {:location/code code}
                      result)))))))

(deftest test-delete
  (let [db *db*]
    (tf/testing "loc.i/delete!"
      {[::loc.i/factory :key/loc] {:db db}}
      (fn [{:keys [loc]}]
        (let [id (:location/id loc)]
          (is (some? (loc.i/get-by-id db id)))
          (loc.i/delete! db id)
          (is (nil? (loc.i/get-by-id db id))))))))

(defn- tree
  "Zone › Orchard › Row, plus a top-level Nursery. A function, because *db* is
  bound by the fixture, after this namespace loads."
  []
  {[::loc.i/factory :key/zone] {:db *db*}
   [::loc.i/factory :key/orchard] {:db *db* :parent (ig/ref :key/zone)}
   [::loc.i/factory :key/row] {:db *db* :parent (ig/ref :key/orchard)}
   [::loc.i/factory :key/nursery] {:db *db*}})

(defn- ids-of [stmt]
  (->> (db.i/execute! *db* stmt) (map (comp first vals)) set))

(deftest test-subtree
  (tf/testing "a location's subtree is itself and everything under it"
    (tree)
    (fn [{:keys [zone orchard row nursery]}]
      (is (= #{(:location/id zone) (:location/id orchard) (:location/id row)}
             (ids-of (loc.i/subtree [:= :l.id (:location/id zone)]))))
      (is (= #{(:location/id row)}
             (ids-of (loc.i/subtree [:= :l.id (:location/id row)]))))
      (testing "several matching roots, each id once"
        (let [rows (db.i/execute! *db* (loc.i/subtree
                                         [:in :l.id [(:location/id orchard)
                                                     (:location/id row)
                                                     (:location/id nursery)]]))]
          (is (= 3 (count rows)))
          (is (= #{(:location/id orchard) (:location/id row) (:location/id nursery)}
                 (set (map (comp first vals) rows)))))))))

(deftest test-paths
  (tf/testing "each location's ancestors, root first, ending with itself"
    (tree)
    (fn [{:keys [zone orchard row nursery]}]
      (let [paths (loc.i/paths *db* #{(:location/id row) (:location/id nursery)})]
        (is (= [(:location/id zone) (:location/id orchard) (:location/id row)]
               (mapv :location/id (get paths (:location/id row)))))
        (is (= [(:location/id nursery)]
               (mapv :location/id (get paths (:location/id nursery)))))
        (is (= {} (loc.i/paths *db* #{})))))))

(deftest test-children
  (tf/testing "direct children only"
    (tree)
    (fn [{:keys [zone orchard row]}]
      (is (= 1 (loc.i/count-children *db* (:location/id zone))))
      (is (= [(:location/id orchard)]
             (mapv :location/id (loc.i/list-children *db* (:location/id zone)))))
      (is (= 0 (loc.i/count-children *db* (:location/id row))))
      (loc.i/set-status! *db* (:location/id orchard) :archived)
      (is (= 1 (loc.i/count-children *db* (:location/id zone))))
      (is (= 0 (loc.i/count-children *db* (:location/id zone) :active))))))

(deftest test-update-refuses-a-cycle
  (tf/testing "a location can't sit inside itself or anything below it"
    (tree)
    (fn [{:keys [zone orchard row nursery]}]
      (doseq [[label parent] [["itself" zone] ["its child" orchard] ["its grandchild" row]]]
        (is (thrown-with-msg? clojure.lang.ExceptionInfo #"inside itself"
                              (loc.i/update! *db* (:location/id zone) {:parent-id (:location/id parent)}))
            label))
      (is (nil? (:location/parent-id (loc.i/get-by-id *db* (:location/id zone))))
          "and nothing changed")
      (testing "a move elsewhere is allowed"
        (is (= (:location/id nursery)
               (:location/parent-id (loc.i/update! *db* (:location/id row)
                                                   {:parent-id (:location/id nursery)})))))
      (testing "and so is clearing the parent"
        (is (nil? (:location/parent-id (loc.i/update! *db* (:location/id row)
                                                      {:parent-id nil}))))))))
