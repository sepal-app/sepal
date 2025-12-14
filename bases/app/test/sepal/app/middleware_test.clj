(ns sepal.app.middleware-test
  (:require [clojure.test :refer [deftest is testing]]
            [sepal.app.authorization :as authz]
            [sepal.app.middleware :as middleware]))

(defn- ok-handler [_request]
  {:status 200 :body "OK"})

(deftest require-role-test
  (testing "allows matching role"
    (let [handler ((middleware/require-role :admin) ok-handler)
          request {:viewer {:user/role :admin}}]
      (is (= 200 (:status (handler request))))))

  (testing "allows any of multiple roles"
    (let [handler ((middleware/require-role :admin :editor) ok-handler)]
      (is (= 200 (:status (handler {:viewer {:user/role :admin}}))))
      (is (= 200 (:status (handler {:viewer {:user/role :editor}}))))))

  (testing "returns 403 for non-matching role"
    (let [handler ((middleware/require-role :admin) ok-handler)
          request {:viewer {:user/role :reader}}]
      (is (= 403 (:status (handler request))))))

  (testing "returns 403 when viewer has no role"
    (let [handler ((middleware/require-role :admin) ok-handler)
          request {:viewer {}}]
      (is (= 403 (:status (handler request)))))))

(deftest require-admin-test
  (testing "allows admin"
    (let [handler (middleware/require-admin ok-handler)
          request {:viewer {:user/role :admin}}]
      (is (= 200 (:status (handler request))))))

  (testing "rejects editor"
    (let [handler (middleware/require-admin ok-handler)
          request {:viewer {:user/role :editor}}]
      (is (= 403 (:status (handler request))))))

  (testing "rejects reader"
    (let [handler (middleware/require-admin ok-handler)
          request {:viewer {:user/role :reader}}]
      (is (= 403 (:status (handler request)))))))

(deftest require-editor-or-admin-test
  (testing "allows admin"
    (let [handler (middleware/require-editor-or-admin ok-handler)
          request {:viewer {:user/role :admin}}]
      (is (= 200 (:status (handler request))))))

  (testing "allows editor"
    (let [handler (middleware/require-editor-or-admin ok-handler)
          request {:viewer {:user/role :editor}}]
      (is (= 200 (:status (handler request))))))

  (testing "rejects reader"
    (let [handler (middleware/require-editor-or-admin ok-handler)
          request {:viewer {:user/role :reader}}]
      (is (= 403 (:status (handler request)))))))

(deftest require-permission-test
  (testing "allows user with permission"
    (let [handler ((middleware/require-permission authz/organization-view) ok-handler)
          request {:viewer {:user/role :admin}}]
      (is (= 200 (:status (handler request))))))

  (testing "rejects user without permission"
    (let [handler ((middleware/require-permission authz/organization-view) ok-handler)
          request {:viewer {:user/role :reader}}]
      (is (= 403 (:status (handler request)))))))

(deftest forbidden-response-htmx-test
  (testing "regular request gets plain forbidden"
    (let [handler ((middleware/require-role :admin) ok-handler)
          request {:viewer {:user/role :reader}
                   :htmx-request? false}
          response (handler request)]
      (is (= 403 (:status response)))
      (is (= "text/html" (get-in response [:headers "Content-Type"])))
      (is (not (.contains (:body response) "alert")))))

  (testing "HTMX request gets alert fragment"
    (let [handler ((middleware/require-role :admin) ok-handler)
          request {:viewer {:user/role :reader}
                   :htmx-request? true}
          response (handler request)]
      (is (= 403 (:status response)))
      (is (.contains (:body response) "alert")))))
