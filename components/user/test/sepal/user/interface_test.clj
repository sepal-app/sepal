(ns sepal.user.interface-test
  (:require [clojure.test :as test :refer :all]
            [integrant.core :as ig]
            [malli.core :as m]
            [malli.error :as me]
            [malli.generator :as mg]
            [matcher-combinators.test :refer [match?]]
            [next.jdbc.sql :as jdbc.sql]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db*
                                           default-system-fixture]]
            [sepal.database.interface :as db.i]
            [sepal.error.interface :as error.i]
            [sepal.organization.interface :as org.i]
            [sepal.user.interface :as user.i]
            [sepal.user.interface.spec :as user.spec]))

(use-fixtures :once default-system-fixture)

(deftest test-get-by-id
  (let [db *db*]
    (tf/testing "user.i/get-by-id"
      {[::org.i/factory :key/org] {:db *db*}
       [::user.i/factory :key/user] {:db *db*
                                     :org (ig/ref :key/org)}
       [::org.i/organization-user-factory :key/org-user] {:db *db*
                                                          :org (ig/ref :key/org)
                                                          :user (ig/ref :key/user)
                                                          :role "owner"}}
      (fn [{:keys [org]}]
        (is (match? org
                    (org.i/get-by-id db (:organization/id org))))))))

(deftest test-create!
  (let [db *db*]
    (tf/testing "user.i/create!"
      (let [data (mg/generate user.spec/CreateUser)
            user (user.i/create! db data)]
        (is (not (error.i/error? user)))
        (is (m/validate user.spec/User user))
        ;; test password not stored in the database as plain text
        (is (not= (:password data)
                  (-> (db.i/execute-one! db {:select :password
                                             :from :public.user
                                             :where [:= :id (:id data)]})
                      :password)))
        (jdbc.sql/delete! db :public.user {:id (:user/id user)})))

    (tf/testing "user.i/create! - with id"
      (let [id (mg/generate pos-int?)
            data (-> (mg/generate user.spec/CreateUser)
                     (assoc :id id))
            user (user.i/create! db data)]
        (is (not (error.i/error? user)))
        (is (m/validate user.spec/User user))
        (is (= id (:user/id user)))

        (jdbc.sql/delete! db :public.user {:id (:user/id user)})))

    (tf/testing "user.i/create! - with invalid email"
      (let [data {:email (mg/generate [:string {:min 1}])
                  :password  (mg/generate user.spec/password)}
            result (user.i/create! db data)
            errors (-> result error.i/data :explain me/humanize)]
        (is (match? {:email ["invalid email"]}
                    errors))))

    (tf/testing "org.i/create! - error: invalid id"
      (let [id (mg/generate neg-int?)
            data (-> (mg/generate user.spec/CreateUser)
                     (assoc :id id))
            result (user.i/create! db  data)
            errors (-> result error.i/data :explain me/humanize)]
        (is (match? {:id ["should be a positive int"]}
                    errors))))

    (tf/testing "org.i/create! - error: id=0"
      (let [id 0
            data (-> (mg/generate user.spec/CreateUser)
                     (assoc :id id))
            result (user.i/create! db  data)

            errors (-> result error.i/data :explain me/humanize)]
        (is (match? {:id ["should be a positive int"]}
                    errors))))))
