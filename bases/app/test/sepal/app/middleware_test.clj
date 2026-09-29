(ns sepal.app.middleware-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [sepal.app.authorization :as authz]
            [sepal.app.flash :as flash]
            [sepal.app.middleware :as middleware]
            [sepal.i18n.interface :as i18n]))

(defn- ok-handler [_request]
  {:status 200 :body "OK"})

(deftest require-permission-test
  (testing "allows user with permission"
    (let [handler ((middleware/require-permission authz/organization-view) ok-handler)
          request {:viewer {:user/role :admin}}]
      (is (= 200 (:status (handler request))))))

  (testing "rejects user without permission"
    (let [handler ((middleware/require-permission authz/organization-view) ok-handler)
          request {:viewer {:user/role :reader}}]
      (is (= 403 (:status (handler request))))))

  (testing "returns 403 when viewer has no role"
    (let [handler ((middleware/require-permission authz/organization-view) ok-handler)]
      (is (= 403 (:status (handler {:viewer {}})))))))

(deftest require-permission-redirect-test
  (let [handler ((middleware/require-permission authz/organization-view "/fallback/") ok-handler)]
    (testing "a GET without the permission redirects"
      (let [response (handler {:viewer {:user/role :reader} :request-method :get})]
        (is (= 302 (:status response)))
        (is (= "/fallback/" (get-in response [:headers "Location"])))))

    (testing "a POST without the permission gets 403, not a redirect"
      (is (= 403 (:status (handler {:viewer {:user/role :reader} :request-method :post})))))))

(deftest require-access-compile-test
  (let [compile (:compile middleware/require-access)]
    (testing ":public compiles to no middleware"
      (is (nil? (compile {:permission :public} nil))))

    (testing "a route that declares no permission is refused"
      (let [handler ((compile {} nil) ok-handler)]
        (is (= 403 (:status (handler {:request-method :get :uri "/x"}))))))

    (testing "a declared permission compiles to middleware"
      (is (fn? (compile {:permission authz/organization-view} nil))))))

(deftest forbidden-response-htmx-test
  (testing "regular request gets plain forbidden"
    (let [handler ((middleware/require-permission authz/organization-view) ok-handler)
          request {:viewer {:user/role :reader}
                   :htmx-request? false}
          response (handler request)]
      (is (= 403 (:status response)))
      (is (= "text/html" (get-in response [:headers "Content-Type"])))
      (is (not (.contains (:body response) "alert")))))

  (testing "HTMX request gets alert fragment"
    (let [handler ((middleware/require-permission authz/organization-view) ok-handler)
          request {:viewer {:user/role :reader}
                   :htmx-request? true}
          response (handler request)]
      (is (= 403 (:status response)))
      (is (.contains (:body response) "spl-alert spl-alert--danger"))
      (is (not (.contains (:body response) "alert-error"))))))

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

(def ^:private es
  (i18n/parse-catalog "es" "msgid \"Profile\"\nmsgstr \"Perfil\"\n"))

(deftest locale-renders-hiccup-inside-its-binding
  ;; zodiac renders a returned vector after the whole middleware stack has
  ;; returned, and Chassis realises a lazy seq only then. A tr inside a `for`
  ;; would run with no catalog bound and come out English.
  (i18n/load-catalogs! {"es" es})
  (try
    (let [handler (middleware/locale
                    (fn [_] [:ul (for [_ [1]] [:li (i18n/tr "Profile")])]))
          response (handler {:headers {"accept-language" "es"}})]
      (is (string? (:body response)) "rendered here, not left to zodiac")
      (is (str/includes? (:body response) "<li>Perfil</li>")))
    (testing "a response that is not hiccup passes through"
      (let [handler (middleware/locale (fn [_] {:status 204}))]
        (is (= {:status 204} (handler {:headers {}})))))
    (finally
      (i18n/load-catalogs! {}))))
