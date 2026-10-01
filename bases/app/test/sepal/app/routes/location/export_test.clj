(ns sepal.app.routes.location.export-test
  "The CSV exports name a location's parent and path, in columns after every
  existing one."
  (:require [clojure.data.csv :as data.csv]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [integrant.core :as ig]
            [peridot.core :as peri]
            [sepal.accession.interface :as accession.i]
            [sepal.app.test :as app.test]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.location.interface :as location.i]
            [sepal.material.interface :as material.i]
            [sepal.observation.interface :as observation.i]
            [sepal.propagation.interface :as propagation.i]
            [sepal.taxon.interface :as taxon.i]
            [sepal.user.interface :as user.i]))

(use-fixtures :once default-system-fixture)

(def ^:private password "testpassword123")

(def ^:private row-path "EXP orchard › EXP row")

(defn- fixtures
  "EXP orchard › EXP row, with material in the row. A function, because *db*
  is bound by the fixture, after this namespace loads."
  []
  {[::user.i/factory :key/user] {:db *db* :password password :role :editor}
   [::taxon.i/factory :key/taxon] {:db *db*}
   [::accession.i/factory :key/accession] {:db *db* :taxon (ig/ref :key/taxon)}
   [::location.i/factory :key/orchard] {:db *db* :data {:code "EXPO" :name "EXP orchard"}}
   [::location.i/factory :key/row] {:db *db* :parent (ig/ref :key/orchard)
                                    :data {:code "EXPO-R1" :name "EXP row"}}
   [::material.i/factory :key/material] {:db *db*
                                         :accession (ig/ref :key/accession)
                                         :location (ig/ref :key/row)}})

(defn- export
  "The CSV at `path` for `params`, as [header rows]."
  [sess path params]
  (let [{:keys [response]} (peri/request sess path :params params)
        [header & rows] (data.csv/read-csv (:body response))]
    [header rows]))

(deftest test-location-export-names-the-parent-code
  (tf/testing "parent_code, last"
    (fixtures)
    (fn [{:keys [user orchard row]}]
      (let [sess (app.test/login (:user/email user) password)
            [header rows] (export sess "/location/export/" {"q" "code:EXPO"})
            by-code (into {} (map (fn [r] [(nth r (.indexOf header "location_code")) (last r)]))
                          rows)]
        (is (= "parent_code" (last header)))
        (is (= (:location/code orchard) (by-code (:location/code row))))
        (is (= "" (by-code (:location/code orchard))))))))

(deftest test-material-export-names-the-path
  (tf/testing "location_path, last, with and without the optional groups"
    (fixtures)
    (fn [{:keys [user orchard]}]
      (let [sess (app.test/login (:user/email user) password)
            q {"q" (str "location.id:" (:location/id orchard))}]
        (doseq [params [q (assoc q "include_taxon" "false" "include_accession" "false")]]
          (testing (pr-str params)
            (let [[header rows] (export sess "/material/export/" params)]
              (is (= "location_path" (last header)))
              (is (= [row-path] (map last rows))))))))))

(deftest test-propagation-export-names-the-path
  (tf/testing "location_path, last"
    (assoc (fixtures)
           [::propagation.i/factory :key/propagation] {:db *db* :accession (ig/ref :key/accession)})
    (fn [{:keys [user row propagation]}]
      (propagation.i/update! *db* (:propagation/id propagation) {:location-id (:location/id row)})
      (let [sess (app.test/login (:user/email user) password)]
        (try
          (let [[header rows] (export sess "/propagation/export/"
                                      {"q" (str "location.id:" (:location/id row))})]
            (is (= "location_path" (last header)))
            (is (= [row-path] (map last rows))))
          (finally
            ;; The row's halt doesn't wait for the propagation.
            (propagation.i/update! *db* (:propagation/id propagation) {:location-id nil})))))))

(deftest test-observation-export-names-the-path
  (tf/testing "location_path, last; empty for a material subject"
    (fixtures)
    (fn [{:keys [user row material]}]
      (let [on-row (observation.i/create! *db* {:resource-type "location"
                                                :resource-id (:location/id row)
                                                :type "general"
                                                :observed-on "2026-01-01"
                                                :note "EXP on row"})
            on-material (observation.i/create! *db* {:resource-type "material"
                                                     :resource-id (:material/id material)
                                                     :type "general"
                                                     :observed-on "2026-01-01"
                                                     :note "EXP on material"})
            sess (app.test/login (:user/email user) password)]
        (try
          (let [[header rows] (export sess "/observation/export/" {"q" "note:EXP"})
                by-note (into {} (map (fn [r] [(nth r (.indexOf header "note")) (last r)])) rows)]
            (is (= "location_path" (last header)))
            (is (= row-path (by-note "EXP on row")))
            (is (= "" (by-note "EXP on material"))))
          (finally
            (observation.i/delete! *db* (:observation/id on-row))
            (observation.i/delete! *db* (:observation/id on-material))))))))
