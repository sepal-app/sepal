(ns sepal.app.routes.taxon.create-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is use-fixtures]]
            [next.jdbc.sql :as jdbc.sql]
            [peridot.core :as peri]
            [sepal.app.test :as app.test]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.test.interface :as test.i]
            [sepal.user.interface :as user.i])
  (:import [org.jsoup Jsoup]))

(use-fixtures :once default-system-fixture)

(deftest test-create-taxon-validation-errors
  (tf/testing "POST with invalid data returns 422 with OOB error elements"
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :editor}}
    (fn [{:keys [user]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response] :as sess} (-> sess
                                            (peri/request "/taxon/new/"))
            create-token (test.i/response-anti-forgery-token response)
            {:keys [response]} (-> sess
                                   (peri/request "/taxon/new/"
                                                 :request-method :post
                                                 :params {:__anti-forgery-token create-token
                                                          :name ""
                                                          :author ""
                                                          :rank ""
                                                          :parent-id ""}))]
        (is (= 422 (:status response))
            (str "Expected 422, got " (:status response) " with body: " (:body response)))

        (is (= "text/html" (get-in response [:headers "Content-Type"]))
            "Should return text/html content type for HTMX OOB swap")

        (let [body (Jsoup/parse ^String (:body response))]
          (let [oob-elements (.select body "[hx-swap-oob]")]
            (is (pos? (.size oob-elements))
                "Should have elements with hx-swap-oob attribute"))

          (is (some? (.selectFirst body "#name-errors"))
              "Should have error list for name field"))))))

(deftest test-create-taxon-form-has-htmx-attributes
  (tf/testing "Form has HTMX attributes for OOB error swapping"
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :editor}}
    (fn [{:keys [user]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response]} (-> sess
                                   (peri/request "/taxon/new/"))
            body (Jsoup/parse ^String (:body response))
            form (.selectFirst body "form#taxon-form")]
        (is (some? (.attr form "hx-post"))
            "Form should have hx-post attribute")

        (is (= "none" (.attr form "hx-swap"))
            "Form should have hx-swap='none' for OOB error updates")))))

(deftest test-create-taxon-form-has-error-containers
  (tf/testing "Form fields have error containers with correct IDs for OOB targeting"
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :editor}}
    (fn [{:keys [user]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response]} (-> sess
                                   (peri/request "/taxon/new/"))
            body (Jsoup/parse ^String (:body response))]
        (is (some? (.selectFirst body "#name-errors"))
            "Name field should have error container with id name-errors")))))

(deftest test-create-a-cultivar-through-the-form
  (tf/testing "the rank select offers cultivar, grex and group, and posting one saves"
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :editor}}
    (fn [{:keys [user]}]
      ;; A successful POST logs an activity row against this ephemeral user.
      ;; activity.created_by deliberately has no ON DELETE CASCADE: activity is
      ;; an audit trail, and production never hard-deletes a user —
      ;; user.i/archive! sets status to :archived instead. The only hard
      ;; delete in the codebase is the test factory's own teardown
      ;; (user/core.clj:153), so this cleanup is a concession to that
      ;; fixture, not a workaround for a missing constraint. Same as
      ;; sepal.taxon.interface.activity-test.
      (try
        (let [sess (app.test/login (:user/email user) "testpassword123")
              {:keys [response] :as sess} (-> sess (peri/request "/taxon/new/"))
              token (test.i/response-anti-forgery-token response)
              body (Jsoup/parse ^String (:body response))
              options (->> (.select body "select[name=rank] option")
                           (map #(.val %))
                           (remove str/blank?)
                           set)]
          (is (= 36 (count options))
              (str "expected 36 rank options, got " (count options)))
          (is (every? options ["cultivar" "grex" "group" "aggregate"])
              "the ICNCP categories and aggregate must be selectable")

          (let [{:keys [response]} (-> sess
                                       (peri/request "/taxon/new/"
                                                     :request-method :post
                                                     :params {:__anti-forgery-token token
                                                              :name "Acer palmatum 'Sango-kaku'"
                                                              :author ""
                                                              :rank "cultivar"
                                                              :parent-id ""}))]
            (is (contains? #{200 204 302} (:status response))
                (str "expected a redirect or success, got " (:status response)
                     " with body: " (:body response)))

            (let [saved (jdbc.sql/find-by-keys *db* :taxon {:name "Acer palmatum 'Sango-kaku'"})]
              (is (= 1 (count saved)) "the POST persisted exactly one taxon")
              (is (= "cultivar" (:taxon/rank (first saved)))
                  "the rank landed as cultivar, not silently dropped or coerced"))))
        (finally
          (jdbc.sql/delete! *db* :activity {:created_by (:user/id user)}))))))
