(ns sepal.app.routes.accession.detail.tags-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [integrant.core :as ig]
            [next.jdbc.sql :as jdbc.sql]
            [peridot.core :as peri]
            [sepal.accession.interface :as accession.i]
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

(deftest test-adding-an-existing-tag-by-name
  (tf/testing "typing an existing tag's name links it"
    {[::user.i/factory :key/user] {:db *db* :password "testpassword123" :role :admin}
     [::taxon.i/factory :key/taxon] {:db *db*}
     [::accession.i/factory :key/accession] {:db *db* :taxon (ig/ref :key/taxon)}}
    (fn [{:keys [user accession]}]
      (let [tag (tag.i/create! *db* {:name "Fruit"})
            sess (app.test/login (:user/email user) "testpassword123")
            id (:accession/id accession)
            url (format "/accession/%s/tags/" id)
            {:keys [response] :as sess} (peri/request sess url)
            token (test.i/response-anti-forgery-token response)
            {:keys [response]} (peri/request sess url
                                             :request-method :post
                                             :params {:__anti-forgery-token token
                                                      :tag-name "fruit"})]
        (is (contains? #{200 303} (:status response)))
        (is (= [(:tag/id tag)] (mapv :tag/id (tag.i/get-for-resource ctx *db* :accession id))))
        (is (some #(= tag.activity/linked (:activity/type %))
                  (activity.i/get-by-resource *db* :resource-type :accession :resource-id id)))
        (tag.i/untag! *db* (:tag/id tag) id :accession)
        (jdbc.sql/delete! *db* :activity {:created_by (:user/id user)})
        (tag.i/delete! *db* (:tag/id tag))))))

(deftest test-adding-a-new-tag-by-typing-an-unmatched-name
  (tf/testing "a name with no existing tag creates one"
    {[::user.i/factory :key/user] {:db *db* :password "testpassword123" :role :admin}
     [::taxon.i/factory :key/taxon] {:db *db*}
     [::accession.i/factory :key/accession] {:db *db* :taxon (ig/ref :key/taxon)}}
    (fn [{:keys [user accession]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            id (:accession/id accession)
            url (format "/accession/%s/tags/" id)
            {:keys [response] :as sess} (peri/request sess url)
            token (test.i/response-anti-forgery-token response)
            {:keys [response]} (peri/request sess url
                                             :request-method :post
                                             :params {:__anti-forgery-token token
                                                      :tag-name "sand tolerent"})]
        (is (contains? #{200 303} (:status response)))
        (let [tag (tag.i/get-by-name ctx *db* "sand tolerent")]
          (is (some? tag))
          (is (= [(:tag/id tag)] (mapv :tag/id (tag.i/get-for-resource ctx *db* :accession id))))
          (tag.i/untag! *db* (:tag/id tag) id :accession)
          (jdbc.sql/delete! *db* :activity {:created_by (:user/id user)})
          (tag.i/delete! *db* (:tag/id tag)))))))

(deftest test-removing-a-tag
  (tf/testing "DELETE unlinks it and does not delete the tag itself"
    {[::user.i/factory :key/user] {:db *db* :password "testpassword123" :role :admin}
     [::taxon.i/factory :key/taxon] {:db *db*}
     [::accession.i/factory :key/accession] {:db *db* :taxon (ig/ref :key/taxon)}}
    (fn [{:keys [user accession]}]
      (let [tag (tag.i/create! *db* {:name "MIBO"})
            id (:accession/id accession)
            sess (app.test/login (:user/email user) "testpassword123")]
        (tag.i/tag! *db* (:tag/id tag) id :accession)
        (let [url (format "/accession/%s/tags/" id)
              {:keys [response] :as sess} (peri/request sess url)
              token (test.i/response-anti-forgery-token response)
              {:keys [response]} (peri/request
                                   sess
                                   (format "/accession/%s/tags/%s/" id (:tag/id tag))
                                   :request-method :delete
                                   :headers {"x-csrf-token" token})]
          (is (contains? #{200 303} (:status response)))
          (is (empty? (tag.i/get-for-resource ctx *db* :accession id)))
          (is (some? (tag.i/get-by-id ctx *db* (:tag/id tag)))
              "the tag itself survives; only the link is gone")
          (jdbc.sql/delete! *db* :activity {:created_by (:user/id user)})
          (tag.i/delete! *db* (:tag/id tag)))))))

(deftest test-deleting-an-unlinked-tag-is-a-no-op
  (tf/testing "DELETE for a tag that exists but isn't linked here writes no activity event"
    {[::user.i/factory :key/user] {:db *db* :password "testpassword123" :role :admin}
     [::taxon.i/factory :key/taxon] {:db *db*}
     [::accession.i/factory :key/accession] {:db *db* :taxon (ig/ref :key/taxon)}}
    (fn [{:keys [user accession]}]
      (let [tag (tag.i/create! *db* {:name "Unlinked"})
            sess (app.test/login (:user/email user) "testpassword123")
            id (:accession/id accession)
            url (format "/accession/%s/tags/" id)
            {:keys [response] :as sess} (peri/request sess url)
            token (test.i/response-anti-forgery-token response)
            {:keys [response]} (peri/request
                                 sess
                                 (format "/accession/%s/tags/%s/" id (:tag/id tag))
                                 :request-method :delete
                                 :headers {"x-csrf-token" token})]
        (is (contains? #{200 303} (:status response)))
        (is (empty? (activity.i/get-by-resource *db* :resource-type :accession :resource-id id))
            "the tag was never linked here, so untag! is a true no-op: no unlinked event")
        (is (some? (tag.i/get-by-id ctx *db* (:tag/id tag)))
            "and the tag itself is untouched")
        (tag.i/delete! *db* (:tag/id tag))))))

(deftest test-linking-an-already-linked-tag-writes-no-second-event
  (tf/testing "POST re-adding an already-linked tag is a no-op: one link, one event"
    {[::user.i/factory :key/user] {:db *db* :password "testpassword123" :role :admin}
     [::taxon.i/factory :key/taxon] {:db *db*}
     [::accession.i/factory :key/accession] {:db *db* :taxon (ig/ref :key/taxon)}}
    (fn [{:keys [user accession]}]
      (let [tag (tag.i/create! *db* {:name "Bromeliad"})
            sess (app.test/login (:user/email user) "testpassword123")
            id (:accession/id accession)
            url (format "/accession/%s/tags/" id)
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
        (is (= [(:tag/id tag)] (mapv :tag/id (tag.i/get-for-resource ctx *db* :accession id)))
            "still exactly one link row")
        (is (= 1 (count (filter #(= tag.activity/linked (:activity/type %))
                                (activity.i/get-by-resource *db* :resource-type :accession :resource-id id))))
            "only the first POST wrote a linked event")
        (tag.i/untag! *db* (:tag/id tag) id :accession)
        (jdbc.sql/delete! *db* :activity {:created_by (:user/id user)})
        (tag.i/delete! *db* (:tag/id tag))))))

(deftest test-a-reader-does-not-see-the-tab
  (tf/testing "readers are redirected to the general detail page"
    {[::user.i/factory :key/user] {:db *db* :password "testpassword123" :role :reader}
     [::taxon.i/factory :key/taxon] {:db *db*}
     [::accession.i/factory :key/accession] {:db *db* :taxon (ig/ref :key/taxon)}}
    (fn [{:keys [user accession]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response]} (peri/request sess (format "/accession/%s/tags/" (:accession/id accession)))]
        (is (not= 200 (:status response)))))))

(deftest test-a-database-below-the-gate-hides-the-tag-ui
  ;; See the long note on the taxon version of this test: the floor CI leg does
  ;; not exercise the gate, so it is forced here, and the accession has to
  ;; carry a real link first or the empty result proves nothing.
  (tf/testing "no tab, no chips, no add form, and the POST refused"
    {[::user.i/factory :key/user] {:db *db* :password "testpassword123" :role :admin}
     [::taxon.i/factory :key/taxon] {:db *db*}
     [::accession.i/factory :key/accession] {:db *db* :taxon (ig/ref :key/taxon)}}
    (fn [{:keys [user accession]}]
      (let [tag (tag.i/create! *db* {:name "Fernaldia"})
            sess (app.test/login (:user/email user) "testpassword123")
            id (:accession/id accession)
            url (format "/accession/%s/tags/" id)
            tab-href (re-pattern (format "href=\"/accession/%s/tags/\"" id))]
        (tag.i/tag! *db* (:tag/id tag) id :accession)
        (let [{:keys [response] :as sess} (peri/request sess url)
              token (test.i/response-anti-forgery-token response)
              general-body (-> sess (peri/request (format "/accession/%s/general/" id)) :response :body)]
          (is (re-find #"Fernaldia" (:body response)))
          (is (re-find tab-href general-body))
          (with-redefs [db.i/at-least-version? (constantly false)]
            (let [{:keys [response]} (peri/request sess url)]
              (is (= 200 (:status response)))
              (is (not (re-find #"Fernaldia" (:body response)))
                  "the gate must filter out a link that really exists")
              (is (not (re-find #"name=\"tag-name\"" (:body response)))))
            (let [{:keys [response]} (peri/request sess url
                                                   :request-method :post
                                                   :params {:__anti-forgery-token token
                                                            :tag-name "Ficus elastica"})]
              (is (= 404 (:status response))))
            (let [body (-> sess (peri/request (format "/accession/%s/general/" id)) :response :body)]
              (is (not (re-find tab-href body))
                  "no Tags tab in the record's section nav"))))
        (is (= [(:tag/id tag)] (mapv :tag/id (tag.i/get-for-resource ctx *db* :accession id)))
            "nothing above actually removed the link")
        (tag.i/untag! *db* (:tag/id tag) id :accession)
        (jdbc.sql/delete! *db* :activity {:created_by (:user/id user)})
        (tag.i/delete! *db* (:tag/id tag))))))
