(ns sepal.app.routes.location.picker-test
  (:require [cheshire.core :as json]
            [clojure.test :refer [deftest is use-fixtures]]
            [peridot.core :as peri]
            [sepal.app.test :as app.test]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.location.interface :as location.i]
            [sepal.user.interface :as user.i]))

(use-fixtures :once default-system-fixture)

(def ^:private password "testpassword123")

(defn- picker-results
  "What the material form's location field shows for `q`."
  [sess q]
  (-> sess
      (peri/request "/location/" :params {"q" q "page-size" "25"}
                    :headers {"accept" "application/json"})
      :response :body
      (json/parse-string true)))

(deftest test-the-picker-finds-a-location-by-name-and-by-code
  (tf/testing "every location a garden has must be reachable from the field
               that files material into it"
    {[::user.i/factory :key/user] {:db *db* :password password :role :editor}}
    (fn [{:keys [user]}]
      (let [made (doall (for [[code nm] [["ORCH" "Orchard"]
                                         ["GH" "Greenhouse"]
                                         ["GH-2" "Greenhouse 2"]
                                         ["B1" "Bed 1"]
                                         ["NURS" "Nursery"]]]
                          (location.i/create! *db* {:code code :name nm})))
            sess (app.test/login (:user/email user) password)]
        (try
          (doseq [{:location/keys [id code name]} made]
            (is (some #(= id (:id %)) (picker-results sess name))
                (str "searching the name " (pr-str name) " did not find it"))
            (is (some #(= id (:id %)) (picker-results sess code))
                (str "searching the code " (pr-str code) " did not find it")))
          (finally
            (doseq [{:location/keys [id]} made]
              (location.i/delete! *db* id))))))))

(deftest test-the-picker-offers-every-location-that-matches
  ;; The picker asked for ten for part of an afternoon, and a garden with more
  ;; locations than that matching what was typed never saw the rest — so the
  ;; location got created a second time, which nothing stops.
  (tf/testing "twenty locations sharing a word"
    {[::user.i/factory :key/user] {:db *db* :password password :role :editor}}
    (fn [{:keys [user]}]
      (let [made (doall (for [i (range 1 21)]
                          (location.i/create! *db* {:code (format "BED%02d" i)
                                                    :name (format "Bed %02d" i)})))
            sess (app.test/login (:user/email user) password)]
        (try
          (is (= 20 (count (picker-results sess "bed")))
              "every location matching what was typed should be offered")
          (finally
            (doseq [{:location/keys [id]} made] (location.i/delete! *db* id))))))))
