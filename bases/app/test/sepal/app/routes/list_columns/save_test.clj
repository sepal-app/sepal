(ns sepal.app.routes.list-columns.save-test
  (:require [clojure.data.json :as json]
            [clojure.test :refer [deftest is use-fixtures]]
            [peridot.core :as peri]
            [sepal.app.test :as app.test]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.test.interface :as test.i]
            [sepal.user.interface :as user.i]))

(use-fixtures :once default-system-fixture)

(defn- fixtures []
  {[::user.i/factory :key/user] {:db *db* :password "testpassword123" :role :reader}})

(defn- post [sess path params & {:keys [current-url]}]
  (let [{:keys [response]} (peri/request sess "/settings/profile")
        token (test.i/response-anti-forgery-token response)]
    (-> sess
        (peri/header "X-CSRF-Token" token)
        (peri/header "hx-request" "true")
        (cond-> current-url (peri/header "hx-current-url" current-url))
        (peri/request path :request-method :post :params params)
        :response)))

(defn- location [response]
  (some-> (get-in response [:headers "HX-Location"]) (json/read-str :key-fn keyword)))

(deftest test-reader-saves-their-choice
  (tf/testing "every offered column is stored, checked or not"
    (fixtures)
    (fn [{:keys [user]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            response (post sess "/lists/accession/columns"
                           {:offered ["provenance" "supplier"] :shown ["supplier"]}
                           :current-url "http://localhost/accession/?q=quer&page=3")]
        (is (= 200 (:status response)))
        (is (= {:path "/accession/?q=quer" :target "#list-container"
                :select "#list-container" :swap "outerHTML"}
               (location response))
            "the list reloads without its page")
        (is (= {:accession {:provenance false :supplier true}}
               (:user/list-columns (user.i/get-by-id *db* (:user/id user)))))))))

(deftest test-hiding-the-sorted-column-drops-the-sort
  (tf/testing "decision: hiding the sorted column clears the sort"
    (fixtures)
    (fn [{:keys [user]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            response (post sess "/lists/accession/columns"
                           {:offered ["provenance" "received"] :shown ["received"]}
                           :current-url "http://localhost/accession/?sort=provenance&dir=asc")]
        (is (= "/accession/" (:path (location response))))))))

(deftest test-reset
  (tf/testing "reset removes the list's entry"
    (fixtures)
    (fn [{:keys [user]}]
      (user.i/set-list-columns! *db* (:user/id user) :accession {:provenance false})
      (let [sess (app.test/login (:user/email user) "testpassword123")]
        (post sess "/lists/accession/columns" {:offered ["provenance"] :reset "1"}
              :current-url "http://localhost/accession/")
        (is (empty? (:user/list-columns (user.i/get-by-id *db* (:user/id user)))))))))

(deftest test-rejects
  (tf/testing "an unknown list or a malformed key"
    (fixtures)
    (fn [{:keys [user]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")]
        (is (= 404 (:status (post sess "/lists/nope/columns" {:offered ["a"]}))))
        (is (= 400 (:status (post sess "/lists/accession/columns" {:offered ["Bad Key"]}))))))))

(deftest test-current-url-host-is-ignored
  (tf/testing "only the path and query of HX-Current-URL are used"
    (fixtures)
    (fn [{:keys [user]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            response (post sess "/lists/accession/columns" {:offered ["provenance"]}
                           :current-url "https://evil.example/accession/")]
        (is (= "/accession/" (:path (location response))))))))
