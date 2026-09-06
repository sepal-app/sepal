(ns sepal.tag.interface.activity-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [matcher-combinators.test :refer [match?]]
            [next.jdbc.sql :as jdbc.sql]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.tag.interface :as tag.i]
            [sepal.tag.interface.activity :as tag.activity]
            [sepal.user.interface :as user.i]))

(use-fixtures :once default-system-fixture)

(deftest tag-activity
  (tf/testing "created, deleted, linked and unlinked events"
    {[::user.i/factory :key/user] {:db *db*}}
    (fn [{:keys [user]}]
      (try
        (let [tag (tag.i/create! *db* {:name "Fruit"})
              created (tag.activity/create! *db* tag.activity/created (:user/id user) tag)
              linked (tag.activity/create-link! *db* tag.activity/linked (:user/id user)
                                                tag :accession 42)]
          (is (match? {:activity/type tag.activity/created
                       :activity/data {:tag-id (:tag/id tag) :name "Fruit"}
                       :activity/created-by (:user/id user)}
                      created))
          (is (match? {:activity/type tag.activity/linked
                       :activity/data {:tag-id (:tag/id tag)
                                       :name "Fruit"
                                       :resource-type "accession"
                                       :resource-id 42}}
                      linked))
          (tag.i/delete! *db* (:tag/id tag)))
        (finally
          (jdbc.sql/delete! *db* :activity {:created_by (:user/id user)}))))))
