(ns sepal.app.middleware-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [sepal.app.authorization :as authz]
            [sepal.app.flash :as flash]
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

(deftest wrap-flash-messages-htmx-partial-test
  (testing "injects OOB flash for HTMX partial responses"
    (let [handler (fn [_] (-> {:status 200
                               :headers {"Content-Type" "text/html"}
                               :body "<div>content</div>"}
                              (flash/success "Done!")))
          wrapped (middleware/wrap-flash-messages handler)
          response (wrapped {:htmx-request? true})]
      (is (str/includes? (:body response) "flash-container"))
      (is (str/includes? (:body response) "hx-swap-oob"))
      (is (str/includes? (:body response) "Done!"))
      (is (nil? (get-in response [:flash :messages]))))))

(deftest wrap-flash-messages-htmx-redirect-test
  (testing "leaves flash in session for HX-Redirect responses"
    (let [handler (fn [_] (-> {:status 200
                               :headers {"Content-Type" "text/html"
                                         "HX-Redirect" "/somewhere"}
                               :body ""}
                              (flash/success "Redirecting!")))
          wrapped (middleware/wrap-flash-messages handler)
          response (wrapped {:htmx-request? true})]
      (is (not (str/includes? (or (:body response) "") "flash-container")))
      (is (= "Redirecting!" (get-in response [:flash :messages 0 :text]))))))

(deftest wrap-flash-messages-htmx-location-test
  (testing "leaves flash in session for HX-Location responses"
    (let [handler (fn [_] (-> {:status 200
                               :headers {"Content-Type" "text/html"
                                         "HX-Location" "/somewhere"}
                               :body ""}
                              (flash/success "Navigating!")))
          wrapped (middleware/wrap-flash-messages handler)
          response (wrapped {:htmx-request? true})]
      (is (= "Navigating!" (get-in response [:flash :messages 0 :text]))))))

(deftest wrap-flash-messages-regular-redirect-test
  (testing "leaves flash in session for regular redirects"
    (let [handler (fn [_] (-> {:status 303
                               :headers {"Location" "/somewhere"}
                               :body ""}
                              (flash/success "Saved!")))
          wrapped (middleware/wrap-flash-messages handler)
          response (wrapped {:htmx-request? false})]
      (is (= "Saved!" (get-in response [:flash :messages 0 :text]))))))

(deftest wrap-flash-messages-non-html-response-test
  (testing "does not inject into non-HTML responses"
    (let [handler (fn [_] (-> {:status 200
                               :headers {"Content-Type" "application/json"}
                               :body "{\"status\": \"ok\"}"}
                              (flash/success "Done!")))
          wrapped (middleware/wrap-flash-messages handler)
          response (wrapped {:htmx-request? true})]
      (is (= "{\"status\": \"ok\"}" (:body response)))
      (is (= "Done!" (get-in response [:flash :messages 0 :text]))))))

(deftest wrap-flash-messages-non-string-body-test
  (testing "does not inject into responses with non-string bodies"
    (let [input-stream (java.io.ByteArrayInputStream. (.getBytes "<html>"))
          handler (fn [_] (-> {:status 200
                               :headers {"Content-Type" "text/html"}
                               :body input-stream}
                              (flash/success "Done!")))
          wrapped (middleware/wrap-flash-messages handler)
          response (wrapped {:htmx-request? true})]
      (is (= input-stream (:body response)))
      (is (= "Done!" (get-in response [:flash :messages 0 :text]))))))

(deftest wrap-flash-messages-no-flash-test
  (testing "passes through responses without flash messages"
    (let [handler (fn [_] {:status 200
                           :headers {"Content-Type" "text/html"}
                           :body "<div>content</div>"})
          wrapped (middleware/wrap-flash-messages handler)
          response (wrapped {:htmx-request? true})]
      (is (= "<div>content</div>" (:body response))))))
