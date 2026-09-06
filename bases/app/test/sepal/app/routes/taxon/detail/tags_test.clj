(ns sepal.app.routes.taxon.detail.tags-test
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
        (is (= [(:tag/id tag)] (mapv :tag/id (tag.i/get-for-resource ctx *db* :taxon id))))
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
        (let [tag (tag.i/get-by-name ctx *db* "sand tolerent")]
          (is (some? tag))
          (is (= [(:tag/id tag)] (mapv :tag/id (tag.i/get-for-resource ctx *db* :taxon id))))
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
          (is (empty? (tag.i/get-for-resource ctx *db* :taxon id)))
          (is (some? (tag.i/get-by-id ctx *db* (:tag/id tag)))
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
        (is (some? (tag.i/get-by-id ctx *db* (:tag/id tag)))
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
        (is (= [(:tag/id tag)] (mapv :tag/id (tag.i/get-for-resource ctx *db* :taxon id)))
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

(deftest test-a-database-below-the-gate-hides-the-tag-ui
  ;; The floor CI leg doesn't actually exercise this: the test system migrates
  ;; to latest before start! regardless of the schema-version option, so `tag`
  ;; and `tag_link` are always present there. This is the real coverage for "a
  ;; database below the migration degrades instead of 500ing".
  ;;
  ;; The taxon must carry a real link before the gate is forced off. An
  ;; untagged taxon renders the same empty state whether the gate is checked or
  ;; skipped entirely, which proves nothing about the branch existing at all --
  ;; an ungated query against an empty result set looks identical to a gated
  ;; one. Writing the link first and asserting the tag's name is *absent* once
  ;; the gate reports "not available" is the only assertion that tells the two
  ;; paths apart.
  (tf/testing "no tab, no chips, no add form, and the POST refused"
    {[::user.i/factory :key/user] {:db *db* :password "testpassword123" :role :admin}
     [::taxon.i/factory :key/taxon] {:db *db*}}
    (fn [{:keys [user taxon]}]
      (let [tag (tag.i/create! *db* {:name "Fernaldia"})
            sess (app.test/login (:user/email user) "testpassword123")
            id (:taxon/id taxon)
            url (format "/taxon/%s/tags/" id)]
        (tag.i/tag! *db* (:tag/id tag) id :taxon)
        ;; Every "absent below the gate" assertion below is paired with the
        ;; same pattern asserted present above it, so a rename or a markup
        ;; change turns the test red rather than passing vacuously.
        (let [tab-href (re-pattern (format "href=\"/taxon/%s/tags/\"" id))
              rail-href #"href=\"/tag/\""
              {:keys [response] :as sess} (peri/request sess url)
              token (test.i/response-anti-forgery-token response)
              name-body (-> sess (peri/request (format "/taxon/%s/name/" id)) :response :body)]
          (is (re-find #"name=\"tag-name\"" (:body response))
              "the form is offered when the tables are there")
          (is (re-find #"Fernaldia" (:body response)))
          (is (re-find tab-href name-body))
          (is (re-find rail-href name-body))
          (with-redefs [db.i/at-least-version? (constantly false)]
            (let [{:keys [response]} (peri/request sess url)]
              (is (= 200 (:status response)))
              (is (not (re-find #"Fernaldia" (:body response)))
                  "the gate must filter out a link that really exists once it
                   reports the tables unavailable")
              (is (not (re-find #"name=\"tag-name\"" (:body response)))
                  "and offer no Add control that cannot store anything"))
            (let [{:keys [response]} (peri/request sess url
                                                   :request-method :post
                                                   :params {:__anti-forgery-token token
                                                            :tag-name "Ficus elastica"})]
              (is (= 404 (:status response))
                  "a direct POST must be refused, not left to fail in SQLite"))
            (let [{:keys [response]} (peri/request sess "/tag/")]
              (is (= 404 (:status response))
                  "and the whole Tags section is gone, not an empty list"))
            (let [body (-> sess (peri/request (format "/taxon/%s/name/" id)) :response :body)]
              (is (not (re-find tab-href body))
                  "no Tags tab in the record's section nav")
              (is (not (re-find rail-href body))
                  "and no Tags entry in the section rail"))))
        (is (= [(:tag/id tag)] (mapv :tag/id (tag.i/get-for-resource ctx *db* :taxon id)))
            "nothing above actually removed the link")
        (tag.i/untag! *db* (:tag/id tag) id :taxon)
        (jdbc.sql/delete! *db* :activity {:created_by (:user/id user)})
        (tag.i/delete! *db* (:tag/id tag))))))
