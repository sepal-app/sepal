(ns sepal.app.routes.location.index-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [integrant.core :as ig]
            [peridot.core :as peri]
            [sepal.accession.interface :as accession.i]
            [sepal.app.test :as app.test]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.location.interface :as location.i]
            [sepal.material.interface :as material.i]
            [sepal.taxon.interface :as taxon.i]
            [sepal.user.interface :as user.i]))

(use-fixtures :once default-system-fixture)

(def ^:private password "testpassword123")

;; A function, not a top-level def: *db* is bound by the fixture at run time.
(defn- fixtures []
  {[::user.i/factory :key/user] {:db *db* :password password :role :editor}
   [::location.i/factory :key/location] {:db *db*}})

(def sortable-keys ["name" "code" "description" "status" "material" "created" "updated"])

(deftest test-every-sort-answers
  (tf/testing "every sortable column, both ways, is valid SQL"
    (fixtures)
    (fn [{:keys [user]}]
      (let [sess (app.test/login (:user/email user) password)]
        (doseq [k sortable-keys dir ["asc" "desc"]]
          (is (= 200 (:status (:response (peri/request sess "/location/" :params {:sort k :dir dir}))))
              (str k " " dir)))))))

(deftest test-parent-is-not-sortable
  (tf/testing "the parent path has no header link"
    (fixtures)
    (fn [{:keys [user]}]
      (let [sess (app.test/login (:user/email user) password)
            body (app.test/parse-body (:response (peri/request sess "/location/")))]
        (is (nil? (.selectFirst body "th:contains(Parent) a")))))))

(deftest test-next-page-keeps-the-sort
  (tf/testing "the prefetch row asks for the next page with the same sort"
    (assoc (fixtures)
           [::location.i/factory :key/second] {:db *db*})
    (fn [{:keys [user]}]
      (let [sess (app.test/login (:user/email user) password)
            body (app.test/parse-body (:response (peri/request sess "/location/"
                                                               :params {:sort "code" :dir "asc" :page-size 1})))
            prefetch (.selectFirst body "tr.spl-prefetch")]
        (is (some? prefetch))
        (is (re-find #"sort=code" (.attr prefetch "hx-get")))))))

(deftest test-material-count-column-shows-when-chosen
  (tf/testing "the material count header renders once the user turns it on"
    (fixtures)
    (fn [{:keys [user]}]
      (user.i/set-list-columns! *db* (:user/id user) :location {:material true})
      (let [sess (app.test/login (:user/email user) password)
            body (app.test/parse-body (:response (peri/request sess "/location/")))
            headers (set (map #(.text %) (.select body "thead th")))]
        (is (contains? headers "Material"))))))

(deftest test-joining-filter-with-material-column
  (tf/testing "a filter that joins, with the material column visible and sorted"
    (assoc (fixtures)
           [::taxon.i/factory :key/taxon] {:db *db*}
           [::accession.i/factory :key/accession] {:db *db* :taxon (ig/ref :key/taxon)}
           [::material.i/factory :key/material] {:db *db*
                                                 :accession (ig/ref :key/accession)
                                                 :location (ig/ref :key/location)
                                                 :data {:type :seed}})
    (fn [{:keys [user]}]
      (user.i/set-list-columns! *db* (:user/id user) :location {:material true})
      (let [sess (app.test/login (:user/email user) password)
            response (:response (peri/request sess "/location/" :params {:q "material.type:seed" :sort "material" :dir "asc"}))]
        (is (= 200 (:status response)))
        (is (some #(.startsWith ^String % "Material")
                  (map #(.text %) (.select (app.test/parse-body response) "thead th"))))))))
