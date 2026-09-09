(ns sepal.app.routes.taxon.detail.tags-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [next.jdbc.sql :as jdbc.sql]
            [peridot.core :as peri]
            [sepal.activity.interface :as activity.i]
            [sepal.app.test :as app.test]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.tag.interface :as tag.i]
            [sepal.tag.interface.activity :as tag.activity]
            [sepal.taxon.interface :as taxon.i]
            [sepal.test.interface :as test.i]
            [sepal.user.interface :as user.i]))

(use-fixtures :once default-system-fixture)

(deftest test-adding-an-existing-tag-by-name
  (tf/testing "typing an existing tag's name links it"
    {[::user.i/factory :key/user] {:db *db* :password "testpassword123" :role :admin}
     [::taxon.i/factory :key/taxon] {:db *db*}}
    (fn [{:keys [user taxon]}]
      (let [tag (tag.i/create! *db* {:name "Fruit"})
            sess (app.test/login (:user/email user) "testpassword123")
            id (:taxon/id taxon)
            url (format "/taxon/%s/tags/" id)
            {:keys [response] :as sess} (peri/request sess url)
            token (test.i/response-anti-forgery-token response)
            {:keys [response]} (peri/request sess url
                                             :request-method :post
                                             :params {:__anti-forgery-token token
                                                      :tag-name "fruit"})]
        (is (contains? #{200 303} (:status response)))
        (is (= [(:tag/id tag)] (mapv :tag/id (tag.i/get-for-resource *db* :taxon id))))
        (is (some #(= tag.activity/linked (:activity/type %))
                  (activity.i/get-by-resource *db* :resource-type :taxon :resource-id id)))
        (tag.i/untag! *db* (:tag/id tag) id :taxon)
        (jdbc.sql/delete! *db* :activity {:created_by (:user/id user)})
        (tag.i/delete! *db* (:tag/id tag))))))

(deftest test-adding-a-new-tag-by-typing-an-unmatched-name
  (tf/testing "a name with no existing tag creates one"
    {[::user.i/factory :key/user] {:db *db* :password "testpassword123" :role :admin}
     [::taxon.i/factory :key/taxon] {:db *db*}}
    (fn [{:keys [user taxon]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            id (:taxon/id taxon)
            url (format "/taxon/%s/tags/" id)
            {:keys [response] :as sess} (peri/request sess url)
            token (test.i/response-anti-forgery-token response)
            {:keys [response]} (peri/request sess url
                                             :request-method :post
                                             :params {:__anti-forgery-token token
                                                      :tag-name "sand tolerent"})]
        (is (contains? #{200 303} (:status response)))
        (let [tag (tag.i/get-by-name *db* "sand tolerent")]
          (is (some? tag))
          (is (= [(:tag/id tag)] (mapv :tag/id (tag.i/get-for-resource *db* :taxon id))))
          (tag.i/untag! *db* (:tag/id tag) id :taxon)
          (jdbc.sql/delete! *db* :activity {:created_by (:user/id user)})
          (tag.i/delete! *db* (:tag/id tag)))))))

(deftest test-removing-a-tag
  (tf/testing "DELETE unlinks it and does not delete the tag itself"
    {[::user.i/factory :key/user] {:db *db* :password "testpassword123" :role :admin}
     [::taxon.i/factory :key/taxon] {:db *db*}}
    (fn [{:keys [user taxon]}]
      (let [tag (tag.i/create! *db* {:name "MIBO"})
            id (:taxon/id taxon)
            sess (app.test/login (:user/email user) "testpassword123")]
        (tag.i/tag! *db* (:tag/id tag) id :taxon)
        (let [url (format "/taxon/%s/tags/" id)
              {:keys [response] :as sess} (peri/request sess url)
              token (test.i/response-anti-forgery-token response)
              {:keys [response]} (peri/request
                                   sess
                                   (format "/taxon/%s/tags/%s/" id (:tag/id tag))
                                   :request-method :delete
                                   :headers {"x-csrf-token" token})]
          (is (contains? #{200 303} (:status response)))
          (is (empty? (tag.i/get-for-resource *db* :taxon id)))
          (is (some? (tag.i/get-by-id *db* (:tag/id tag)))
              "the tag itself survives; only the link is gone")
          (jdbc.sql/delete! *db* :activity {:created_by (:user/id user)})
          (tag.i/delete! *db* (:tag/id tag)))))))

(deftest test-deleting-an-unlinked-tag-is-a-no-op
  (tf/testing "DELETE for a tag that exists but isn't linked here writes no activity event"
    {[::user.i/factory :key/user] {:db *db* :password "testpassword123" :role :admin}
     [::taxon.i/factory :key/taxon] {:db *db*}}
    (fn [{:keys [user taxon]}]
      (let [tag (tag.i/create! *db* {:name "Unlinked"})
            sess (app.test/login (:user/email user) "testpassword123")
            id (:taxon/id taxon)
            url (format "/taxon/%s/tags/" id)
            {:keys [response] :as sess} (peri/request sess url)
            token (test.i/response-anti-forgery-token response)
            {:keys [response]} (peri/request
                                 sess
                                 (format "/taxon/%s/tags/%s/" id (:tag/id tag))
                                 :request-method :delete
                                 :headers {"x-csrf-token" token})]
        (is (contains? #{200 303} (:status response)))
        (is (empty? (activity.i/get-by-resource *db* :resource-type :taxon :resource-id id))
            "the tag was never linked here, so untag! is a true no-op: no unlinked event")
        (is (some? (tag.i/get-by-id *db* (:tag/id tag)))
            "and the tag itself is untouched")
        (tag.i/delete! *db* (:tag/id tag))))))

(deftest test-linking-an-already-linked-tag-writes-no-second-event
  (tf/testing "POST re-adding an already-linked tag is a no-op: one link, one event"
    {[::user.i/factory :key/user] {:db *db* :password "testpassword123" :role :admin}
     [::taxon.i/factory :key/taxon] {:db *db*}}
    (fn [{:keys [user taxon]}]
      (let [tag (tag.i/create! *db* {:name "Bromeliad"})
            sess (app.test/login (:user/email user) "testpassword123")
            id (:taxon/id taxon)
            url (format "/taxon/%s/tags/" id)
            {:keys [response] :as sess} (peri/request sess url)
            token (test.i/response-anti-forgery-token response)
            {:keys [response] :as sess} (peri/request sess url
                                                      :request-method :post
                                                      :params {:__anti-forgery-token token
                                                               :tag-name "Bromeliad"})
            first-status (:status response)
            {:keys [response]} (peri/request sess url
                                             :request-method :post
                                             :params {:__anti-forgery-token token
                                                      :tag-name "Bromeliad"})]
        (is (contains? #{200 303} first-status))
        (is (contains? #{200 303} (:status response)))
        (is (= [(:tag/id tag)] (mapv :tag/id (tag.i/get-for-resource *db* :taxon id)))
            "still exactly one link row")
        (is (= 1 (count (filter #(= tag.activity/linked (:activity/type %))
                                (activity.i/get-by-resource *db* :resource-type :taxon :resource-id id))))
            "only the first POST wrote a linked event")
        (tag.i/untag! *db* (:tag/id tag) id :taxon)
        (jdbc.sql/delete! *db* :activity {:created_by (:user/id user)})
        (tag.i/delete! *db* (:tag/id tag))))))

(deftest test-a-reader-does-not-see-the-tab
  (tf/testing "readers are redirected to the general detail page"
    {[::user.i/factory :key/user] {:db *db* :password "testpassword123" :role :reader}
     [::taxon.i/factory :key/taxon] {:db *db*}}
    (fn [{:keys [user taxon]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response]} (peri/request sess (format "/taxon/%s/tags/" (:taxon/id taxon)))]
        (is (<= 300 (:status response) 399)
            "require-permission-or-redirect issues a 3xx, not merely a non-200")
        (is (re-find #"^/taxon/\d+/$" (get-in response [:headers "Location"]))
            "and it redirects to this taxon's own detail route")))))
