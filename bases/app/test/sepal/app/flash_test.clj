(ns sepal.app.flash-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [sepal.app.flash :as flash]
            [sepal.app.flash.category :as category]
            [sepal.app.middleware :as middleware]))

(deftest banner-oob-structure-test
  (testing "has correct OOB attributes"
    (let [result (flash/banner-oob [{:text "Test" :category category/success}])]
      (is (= "flash-container" (get-in result [1 :id])))
      (is (= "beforeend" (get-in result [1 :hx-swap-oob])))))

  (testing "renders nil content for empty messages"
    (let [result (flash/banner-oob [])]
      (is (= "flash-container" (get-in result [1 :id])))
      (is (nil? (get result 2))))))

(deftest banner-message-auto-dismiss-test
  (testing "success messages have auto-dismiss"
    (let [result (flash/banner-message {:text "Test" :category category/success})
          x-init (get-in result [1 :x-init])]
      (is (string? x-init))
      (is (str/includes? x-init "setTimeout"))))

  (testing "error messages do not have auto-dismiss"
    (let [result (flash/banner-message {:text "Error" :category category/error})
          x-init (get-in result [1 :x-init])]
      (is (nil? x-init))))

  (testing "warning messages have auto-dismiss"
    (let [result (flash/banner-message {:text "Warning" :category category/warning})
          x-init (get-in result [1 :x-init])]
      (is (string? x-init))
      (is (str/includes? x-init "setTimeout")))))

(deftest banner-message-transitions-test
  (testing "has fade-out transitions"
    (let [result (flash/banner-message {:text "Test" :category category/success})
          attrs (get result 1)]
      (is (= "transition ease-in duration-300" (:x-transition:leave attrs)))
      (is (= "opacity-100" (:x-transition:leave-start attrs)))
      (is (= "opacity-0" (:x-transition:leave-end attrs))))))

(deftest add-message-test
  (testing "adds message to response flash"
    (let [response {}
          result (flash/success response "Test message")]
      (is (= [{:text "Test message" :category category/success}]
             (get-in result [:flash :messages])))))

  (testing "appends multiple messages"
    (let [response {}
          result (-> response
                     (flash/success "First")
                     (flash/error "Second"))]
      (is (= [{:text "First" :category category/success}
              {:text "Second" :category category/error}]
             (get-in result [:flash :messages]))))))

(deftest test-a-redirect-carries-the-flash-forward
  (testing "a flash survives exactly one request and a redirect renders
            nothing, so without this the message dies on the hop. Saving an
            accession redirects to /accession/12/, which redirects again to
            /accession/12/general/."
    (let [incoming [{:text "Saved" :category category/success}]
          redirect-to-general (fn [_] {:status 302
                                       :headers {"Location" "/accession/12/general/"}})
          handler (middleware/wrap-flash-messages redirect-to-general)
          response (handler {:flash {:messages incoming}})]
      (is (= incoming (get-in response [:flash :messages]))))))

(deftest test-a-redirect-with-its-own-flash-keeps-it
  (testing "a failure redirect must not have its message replaced by whatever
            was already in the session"
    (let [own [{:text "Could not save" :category category/error}]
          handler (middleware/wrap-flash-messages
                    (fn [_] {:status 302
                             :headers {"Location" "/x"}
                             :flash {:messages own}}))
          response (handler {:flash {:messages [{:text "Stale" :category category/success}]}})]
      (is (= own (get-in response [:flash :messages]))))))

(deftest test-a-rendered-page-does-not-carry-the-flash-on
  (testing "only a redirect forwards it — a 200 rendered the banner itself,
            and forwarding would show it twice"
    (let [handler (middleware/wrap-flash-messages
                    (fn [_] {:status 200
                             :headers {"content-type" "text/html"}
                             :body "<html></html>"}))
          response (handler {:flash {:messages [{:text "Saved" :category category/success}]}})]
      (is (nil? (get-in response [:flash :messages]))))))
