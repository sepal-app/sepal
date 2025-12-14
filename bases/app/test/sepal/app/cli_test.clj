(ns sepal.app.cli-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.user.interface :as user.i]))

(use-fixtures :once default-system-fixture)

(deftest create-user-test
  (testing "creates user with valid data"
    (let [email "cli-test@example.com"
          _ (user.i/create! *db* {:email email
                                  :password "password123"
                                  :role :editor})
          user (user.i/get-by-email *db* email)]
      (is (some? user))
      (is (= email (:user/email user)))
      (is (= :editor (:user/role user)))))

  (testing "role is required and must be valid"
    (is (thrown? Exception
                 (user.i/create! *db* {:email "no-role@example.com"
                                       :password "password123"})))))

(deftest get-all-users-test
  (testing "returns all users"
    (let [_ (user.i/create! *db* {:email "user1@example.com"
                                  :password "password123"
                                  :role :admin})
          _ (user.i/create! *db* {:email "user2@example.com"
                                  :password "password123"
                                  :role :reader})
          users (user.i/get-all *db*)]
      (is (>= (count users) 2))
      (is (every? :user/role users)))))
