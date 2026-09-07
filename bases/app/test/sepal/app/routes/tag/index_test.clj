(ns sepal.app.routes.tag.index-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [next.jdbc.sql :as jdbc.sql]
            [peridot.core :as peri]
            [sepal.activity.interface :as activity.i]
            [sepal.app.test :as app.test]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.database.interface :as db.i]
            [sepal.tag.interface :as tag.i]
            [sepal.tag.interface.activity :as tag.activity]
            [sepal.taxon.interface :as taxon.i]
            [sepal.test.interface :as test.i]
            [sepal.user.interface :as user.i]))

(use-fixtures :once default-system-fixture)

;; The reads take a context so they can gate on the schema version; the test
;; database is at latest, so these assertions want the ungated answer.
(def ctx {:schema-version (db.i/latest-version)})

(deftest test-the-index-lists-tags-with-counts
  (tf/testing "an admin views the tag list"
    {[::user.i/factory :key/user] {:db *db* :password "testpassword123" :role :admin}}
    (fn [{:keys [user]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            tag (tag.i/create! *db* {:name "Fruit"})
            {:keys [response]} (peri/request sess "/tag/")]
        (is (= 200 (:status response)))
        (is (re-find #"Fruit" (:body response)))
        (tag.i/delete! *db* (:tag/id tag))))))

(deftest test-renaming-a-tag
  (tf/testing "PUT /tag/:id/ renames it"
    {[::user.i/factory :key/user] {:db *db* :password "testpassword123" :role :admin}}
    (fn [{:keys [user]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            tag (tag.i/create! *db* {:name "oct 16"})
            url (format "/tag/%s/" (:tag/id tag))
            {:keys [response] :as sess} (peri/request sess url)
            token (test.i/response-anti-forgery-token response)
            {:keys [response]} (peri/request sess url
                                             :request-method :post
                                             :params {:__anti-forgery-token token
                                                      :name "October block"
                                                      :description ""})]
        (is (contains? #{200 303} (:status response)))
        (is (= "October block" (:tag/name (tag.i/get-by-id ctx *db* (:tag/id tag)))))
        (is (some #(= tag.activity/updated (:activity/type %))
                  (activity.i/get-by-resource *db* :resource-type :tag :resource-id (:tag/id tag)))
            "the rename is recorded, like every other resource's update")
        (jdbc.sql/delete! *db* :activity {:created_by (:user/id user)})
        (tag.i/delete! *db* (:tag/id tag))))))

(deftest test-renaming-a-tag-onto-an-existing-name
  ;; tag.name is `unique collate nocase` and store.i/update! does not catch
  ;; SQLiteException, so this used to throw out of the handler as a 500 -- on
  ;; the very page that lists both names.
  (tf/testing "POST a name another tag already holds"
    {[::user.i/factory :key/user] {:db *db* :password "testpassword123" :role :admin}}
    (fn [{:keys [user]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            fruit (tag.i/create! *db* {:name "Fruit"})
            block (tag.i/create! *db* {:name "oct 16"})
            url (format "/tag/%s/" (:tag/id block))
            {:keys [response] :as sess} (peri/request sess url)
            token (test.i/response-anti-forgery-token response)
            {:keys [response]} (peri/request sess url
                                             :request-method :post
                                             :params {:__anti-forgery-token token
                                                      :name "Fruit"
                                                      :description ""})]
        (is (= 422 (:status response))
            "a validation error on the form, not a 500")
        (is (re-find #"already exists" (:body response))
            "and it says which field and why, rather than an empty 422")
        (is (= "oct 16" (:tag/name (tag.i/get-by-id ctx *db* (:tag/id block))))
            "the rename did not happen")
        (is (empty? (activity.i/get-by-resource *db* :resource-type :tag
                                                :resource-id (:tag/id block)))
            "and the rolled-back transaction left no updated event behind")
        (tag.i/delete! *db* (:tag/id block))
        (tag.i/delete! *db* (:tag/id fruit))))))

(deftest test-deleting-a-tag
  (tf/testing "DELETE removes it"
    {[::user.i/factory :key/user] {:db *db* :password "testpassword123" :role :admin}}
    (fn [{:keys [user]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            tag (tag.i/create! *db* {:name "sand tolerent"})
            url (format "/tag/%s/" (:tag/id tag))
            {:keys [response] :as sess} (peri/request sess url)
            token (test.i/response-anti-forgery-token response)
            {:keys [response]} (peri/request sess url
                                             :request-method :delete
                                             :headers {"x-csrf-token" token})]
        (is (contains? #{200 303} (:status response)))
        (is (nil? (tag.i/get-by-id ctx *db* (:tag/id tag))))
        (is (some #(= tag.activity/deleted (:activity/type %))
                  (activity.i/get-by-resource *db* :resource-type :tag :resource-id (:tag/id tag)))
            "the deletion is recorded; the row is gone, so the feed is the only
             place it is still named")
        (jdbc.sql/delete! *db* :activity {:created_by (:user/id user)})))))

(deftest test-a-failed-activity-write-rolls-the-delete-back
  ;; The handler deletes the tag and writes its activity event in one
  ;; transaction, and tag.i/delete! joins that transaction instead of opening
  ;; its own. Without that guard next.jdbc's *nested-tx* default of :allow
  ;; would run the inner one for real and commit the deletes before the
  ;; activity write was attempted, leaving the outer rollback nothing to undo
  ;; -- a tag gone with no record that it ever went.
  ;;
  ;; The tag needs a link on it too: the deletes are two statements, and only
  ;; asserting on the tag row would pass even if the tag_link delete had
  ;; committed separately.
  (tf/testing "the activity write throws"
    {[::user.i/factory :key/user] {:db *db* :password "testpassword123" :role :admin}
     [::taxon.i/factory :key/taxon] {:db *db*}}
    (fn [{:keys [user taxon]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            tag (tag.i/create! *db* {:name "cycad circle labels"})
            tag-id (:tag/id tag)
            url (format "/tag/%s/" tag-id)]
        (tag.i/tag! *db* tag-id (:taxon/id taxon) :taxon)
        (let [{:keys [response] :as sess} (peri/request sess url)
              token (test.i/response-anti-forgery-token response)]
          (with-redefs [tag.activity/create!
                        (fn [& _] (throw (ex-info "activity write failed" {})))]
            (peri/request sess url
                          :request-method :delete
                          :headers {"x-csrf-token" token}))
          (is (some? (tag.i/get-by-id ctx *db* tag-id))
              "the tag row must survive a rolled-back delete")
          (is (= [tag-id] (mapv :tag/id (tag.i/get-for-resource ctx *db* :taxon (:taxon/id taxon))))
              "and so must its link"))
        (tag.i/delete! *db* tag-id)
        (jdbc.sql/delete! *db* :activity {:created_by (:user/id user)})))))
