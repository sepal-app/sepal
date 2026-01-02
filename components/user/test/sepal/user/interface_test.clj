(ns sepal.user.interface-test
  (:require [clojure.test :as test :refer :all]
            [malli.core :as m]
            [malli.error :as me]
            [malli.generator :as mg]
            [matcher-combinators.test :refer [match? thrown-match?]]
            [next.jdbc.sql :as jdbc.sql]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db*
                                           default-system-fixture]]
            [sepal.database.interface :as db.i]
            [sepal.error.interface :as error.i]
            [sepal.user.interface :as user.i]
            [sepal.user.interface.spec :as user.spec]))

(use-fixtures :once default-system-fixture)

(deftest test-create!
  (let [db *db*]
    (tf/testing "user.i/create!"
      ;; Dissoc :id to let SQLite auto-generate it, avoiding collisions
      (let [data (dissoc (mg/generate user.spec/CreateUser) :id)
            user (user.i/create! db data)]
        (is (not (error.i/error? user)))
        (is (m/validate user.spec/User user))
        ;; test password not stored in the database as plain text
        (is (not= (:password data)
                  (-> (db.i/execute-one! db {:select :password
                                             :from :user
                                             :where [:= :id (:user/id user)]})
                      :password)))
        (jdbc.sql/delete! db :user {:id (:user/id user)})))

    (tf/testing "user.i/create! - with id"
      ;; Use a large ID range to avoid conflicts with auto-generated IDs
      (let [id (+ 1000000 (mg/generate [:int {:min 0 :max 999999}]))
            data (-> (mg/generate user.spec/CreateUser)
                     (assoc :id id))
            user (user.i/create! db data)]
        (is (not (error.i/error? user)))
        (is (m/validate user.spec/User user))
        (is (= id (:user/id user)))

        (jdbc.sql/delete! db :user {:id (:user/id user)})))

    (tf/testing "user.i/create! - with invalid email"
      (let [data {:email (mg/generate [:string {:min 10}])
                  :password (mg/generate user.spec/password)}]
        (is (thrown-match? clojure.lang.ExceptionInfo
                           (fn [exd]
                             (is (match? {:email ["invalid email"]}
                                         (-> exd :data :explain me/humanize))))
                           (user.i/create! db data)))))

    (tf/testing "user.i/create! - error: invalid id"
      (let [id (mg/generate neg-int?)
            data (-> (mg/generate user.spec/CreateUser)
                     (assoc :id id))]
        (is (thrown-match? clojure.lang.ExceptionInfo
                           (fn [exd]
                             (is (match? {:id ["should be a positive int"]}
                                         (-> exd :data :explain me/humanize))))
                           (user.i/create! db data)))))

    (tf/testing "user.i/create! - error: id=0"
      (let [id 0
            data (-> (mg/generate user.spec/CreateUser)
                     (assoc :id id))]
        (is (thrown-match? clojure.lang.ExceptionInfo
                           (fn [exd]
                             (is (match? {:id ["should be a positive int"]}
                                         (-> exd :data :explain me/humanize))))
                           (user.i/create! db data)))))))
