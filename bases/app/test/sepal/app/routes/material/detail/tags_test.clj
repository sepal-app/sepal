(ns sepal.app.routes.material.detail.tags-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [integrant.core :as ig]
            [next.jdbc.sql :as jdbc.sql]
            [peridot.core :as peri]
            [sepal.accession.interface :as accession.i]
            [sepal.activity.interface :as activity.i]
            [sepal.app.test :as app.test]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.location.interface :as location.i]
            [sepal.material.interface :as material.i]
            [sepal.tag.interface :as tag.i]
            [sepal.tag.interface.activity :as tag.activity]
            [sepal.taxon.interface :as taxon.i]
            [sepal.test.interface :as test.i]
            [sepal.user.interface :as user.i]))

(use-fixtures :once default-system-fixture)

(deftest test-adding-an-existing-tag-by-name
  (tf/testing "typing an existing tag's name links it"
    {[::user.i/factory :key/user] {:db *db* :password "testpassword123" :role :admin}
     [::taxon.i/factory :key/taxon] {:db *db*}
     [::accession.i/factory :key/accession] {:db *db* :taxon (ig/ref :key/taxon)}
     [::location.i/factory :key/location] {:db *db*}
     [::material.i/factory :key/material] {:db *db*
                                           :accession (ig/ref :key/accession)
                                           :location (ig/ref :key/location)}}
    (fn [{:keys [user material]}]
      (let [tag (tag.i/create! *db* {:name "Fruit"})
            sess (app.test/login (:user/email user) "testpassword123")
            id (:material/id material)
            url (format "/material/%s/tags/" id)
            {:keys [response] :as sess} (peri/request sess url)
            token (test.i/response-anti-forgery-token response)
            {:keys [response]} (peri/request sess url
                                             :request-method :post
                                             :params {:__anti-forgery-token token
                                                      :tag-name "fruit"})]
        (is (contains? #{200 303} (:status response)))
        (is (= [(:tag/id tag)] (mapv :tag/id (tag.i/get-for-resource *db* :material id))))
        (is (some #(= tag.activity/linked (:activity/type %))
                  (activity.i/get-by-resource *db* :resource-type :material :resource-id id)))
        (tag.i/untag! *db* (:tag/id tag) id :material)
        (jdbc.sql/delete! *db* :activity {:created_by (:user/id user)})
        (tag.i/delete! *db* (:tag/id tag))))))

(deftest test-adding-a-new-tag-by-typing-an-unmatched-name
  (tf/testing "a name with no existing tag creates one"
    {[::user.i/factory :key/user] {:db *db* :password "testpassword123" :role :admin}
     [::taxon.i/factory :key/taxon] {:db *db*}
     [::accession.i/factory :key/accession] {:db *db* :taxon (ig/ref :key/taxon)}
     [::location.i/factory :key/location] {:db *db*}
     [::material.i/factory :key/material] {:db *db*
                                           :accession (ig/ref :key/accession)
                                           :location (ig/ref :key/location)}}
    (fn [{:keys [user material]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            id (:material/id material)
            url (format "/material/%s/tags/" id)
            {:keys [response] :as sess} (peri/request sess url)
            token (test.i/response-anti-forgery-token response)
            {:keys [response]} (peri/request sess url
                                             :request-method :post
                                             :params {:__anti-forgery-token token
                                                      :tag-name "sand tolerent"})]
        (is (contains? #{200 303} (:status response)))
        (let [tag (tag.i/get-by-name *db* "sand tolerent")]
          (is (some? tag))
          (is (= [(:tag/id tag)] (mapv :tag/id (tag.i/get-for-resource *db* :material id))))
          (tag.i/untag! *db* (:tag/id tag) id :material)
          (jdbc.sql/delete! *db* :activity {:created_by (:user/id user)})
          (tag.i/delete! *db* (:tag/id tag)))))))

(deftest test-removing-a-tag
  (tf/testing "DELETE unlinks it and does not delete the tag itself"
    {[::user.i/factory :key/user] {:db *db* :password "testpassword123" :role :admin}
     [::taxon.i/factory :key/taxon] {:db *db*}
     [::accession.i/factory :key/accession] {:db *db* :taxon (ig/ref :key/taxon)}
     [::location.i/factory :key/location] {:db *db*}
     [::material.i/factory :key/material] {:db *db*
                                           :accession (ig/ref :key/accession)
                                           :location (ig/ref :key/location)}}
    (fn [{:keys [user material]}]
      (let [tag (tag.i/create! *db* {:name "MIBO"})
            id (:material/id material)
            sess (app.test/login (:user/email user) "testpassword123")]
        (tag.i/tag! *db* (:tag/id tag) id :material)
        (let [url (format "/material/%s/tags/" id)
              {:keys [response] :as sess} (peri/request sess url)
              token (test.i/response-anti-forgery-token response)
              {:keys [response]} (peri/request
                                   sess
                                   (format "/material/%s/tags/%s/" id (:tag/id tag))
                                   :request-method :delete
                                   :headers {"x-csrf-token" token})]
          (is (contains? #{200 303} (:status response)))
          (is (empty? (tag.i/get-for-resource *db* :material id)))
          (is (some? (tag.i/get-by-id *db* (:tag/id tag)))
              "the tag itself survives; only the link is gone")
          (jdbc.sql/delete! *db* :activity {:created_by (:user/id user)})
          (tag.i/delete! *db* (:tag/id tag)))))))

(deftest test-deleting-an-unlinked-tag-is-a-no-op
  (tf/testing "DELETE for a tag that exists but isn't linked here writes no activity event"
    {[::user.i/factory :key/user] {:db *db* :password "testpassword123" :role :admin}
     [::taxon.i/factory :key/taxon] {:db *db*}
     [::accession.i/factory :key/accession] {:db *db* :taxon (ig/ref :key/taxon)}
     [::location.i/factory :key/location] {:db *db*}
     [::material.i/factory :key/material] {:db *db*
                                           :accession (ig/ref :key/accession)
                                           :location (ig/ref :key/location)}}
    (fn [{:keys [user material]}]
      (let [tag (tag.i/create! *db* {:name "Unlinked"})
            sess (app.test/login (:user/email user) "testpassword123")
            id (:material/id material)
            url (format "/material/%s/tags/" id)
            {:keys [response] :as sess} (peri/request sess url)
            token (test.i/response-anti-forgery-token response)
            {:keys [response]} (peri/request
                                 sess
                                 (format "/material/%s/tags/%s/" id (:tag/id tag))
                                 :request-method :delete
                                 :headers {"x-csrf-token" token})]
        (is (contains? #{200 303} (:status response)))
        (is (empty? (activity.i/get-by-resource *db* :resource-type :material :resource-id id))
            "the tag was never linked here, so untag! is a true no-op: no unlinked event")
        (is (some? (tag.i/get-by-id *db* (:tag/id tag)))
            "and the tag itself is untouched")
        (tag.i/delete! *db* (:tag/id tag))))))

(deftest test-linking-an-already-linked-tag-writes-no-second-event
  (tf/testing "POST re-adding an already-linked tag is a no-op: one link, one event"
    {[::user.i/factory :key/user] {:db *db* :password "testpassword123" :role :admin}
     [::taxon.i/factory :key/taxon] {:db *db*}
     [::accession.i/factory :key/accession] {:db *db* :taxon (ig/ref :key/taxon)}
     [::location.i/factory :key/location] {:db *db*}
     [::material.i/factory :key/material] {:db *db*
                                           :accession (ig/ref :key/accession)
                                           :location (ig/ref :key/location)}}
    (fn [{:keys [user material]}]
      (let [tag (tag.i/create! *db* {:name "Bromeliad"})
            sess (app.test/login (:user/email user) "testpassword123")
            id (:material/id material)
            url (format "/material/%s/tags/" id)
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
        (is (= [(:tag/id tag)] (mapv :tag/id (tag.i/get-for-resource *db* :material id)))
            "still exactly one link row")
        (is (= 1 (count (filter #(= tag.activity/linked (:activity/type %))
                                (activity.i/get-by-resource *db* :resource-type :material :resource-id id))))
            "only the first POST wrote a linked event")
        (tag.i/untag! *db* (:tag/id tag) id :material)
        (jdbc.sql/delete! *db* :activity {:created_by (:user/id user)})
        (tag.i/delete! *db* (:tag/id tag))))))

(deftest test-a-reader-does-not-see-the-tab
  (tf/testing "readers are redirected to the general detail page"
    {[::user.i/factory :key/user] {:db *db* :password "testpassword123" :role :reader}
     [::taxon.i/factory :key/taxon] {:db *db*}
     [::accession.i/factory :key/accession] {:db *db* :taxon (ig/ref :key/taxon)}
     [::location.i/factory :key/location] {:db *db*}
     [::material.i/factory :key/material] {:db *db*
                                           :accession (ig/ref :key/accession)
                                           :location (ig/ref :key/location)}}
    (fn [{:keys [user material]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response]} (peri/request sess (format "/material/%s/tags/" (:material/id material)))]
        (is (<= 300 (:status response) 399)
            "require-permission-or-redirect issues a 3xx, not merely a non-200")
        (is (re-find #"^/material/\d+/$" (get-in response [:headers "Location"]))
            "and it redirects to this material's own detail route")))))

