(ns sepal.app.routes.contact.index-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [next.jdbc.sql :as jdbc.sql]
            [peridot.core :as peri]
            [sepal.app.test :as app.test]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.contact.interface :as contact.i]
            [sepal.user.interface :as user.i]))

(use-fixtures :once default-system-fixture)

(def ^:private password "testpassword123")

;; A function, not a top-level def: *db* is bound by the fixture at run time.
(defn- fixtures []
  {[::user.i/factory :key/user] {:db *db* :password password :role :editor}})

(def sortable-keys
  ["name" "business" "email" "city" "phone" "type" "country" "province" "postal-code" "created" "updated"])

(deftest test-every-sort-answers
  (tf/testing "every sortable column, both ways, is valid SQL"
    (fixtures)
    (fn [{:keys [user]}]
      (let [sess (app.test/login (:user/email user) password)]
        (doseq [k sortable-keys dir ["asc" "desc"]]
          (is (= 200 (:status (:response (peri/request sess "/contact/" :params {:sort k :dir dir}))))
              (str k " " dir)))))))

(deftest test-name-sort-ignores-case
  (tf/testing "lower-case names sort among the rest"
    (fixtures)
    (fn [{:keys [user]}]
      (jdbc.sql/insert! *db* :contact {:name "alpha"})
      (jdbc.sql/insert! *db* :contact {:name "Beta"})
      (try
        (let [sess (app.test/login (:user/email user) password)
              names (app.test/first-cells (app.test/parse-body (:response (peri/request sess "/contact/"
                                                                                        :params {:sort "name" :dir "asc"}))))]
          (is (contains? (set names) "alpha"))
          (is (< (.indexOf names "alpha") (.indexOf names "Beta"))))
        (finally
          (jdbc.sql/delete! *db* :contact {:name "alpha"})
          (jdbc.sql/delete! *db* :contact {:name "Beta"}))))))

(deftest test-next-page-keeps-the-sort
  (tf/testing "the prefetch row asks for the next page with the same sort"
    (assoc (fixtures)
           [::contact.i/factory :key/first] {:db *db*}
           [::contact.i/factory :key/second] {:db *db*})
    (fn [{:keys [user]}]
      (let [sess (app.test/login (:user/email user) password)
            body (app.test/parse-body (:response (peri/request sess "/contact/"
                                                               :params {:sort "name" :dir "asc" :page-size 1})))
            prefetch (.selectFirst body "tr.spl-prefetch")]
        (is (some? prefetch))
        (is (re-find #"sort=name" (.attr prefetch "hx-get")))))))
