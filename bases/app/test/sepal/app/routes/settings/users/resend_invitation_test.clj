(ns sepal.app.routes.settings.users.resend-invitation-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [peridot.core :as peri]
            [sepal.app.test :as app.test]
            [sepal.app.test.system :refer [*db* *mail-client* default-system-fixture]]
            [sepal.test.interface :as test.i]
            [sepal.user.interface :as user.i])
  (:import [org.jsoup Jsoup]))

(use-fixtures :once default-system-fixture)

(defn- banner
  "The out-of-band flash the response carries, as [classes text]."
  [body]
  (let [doc (Jsoup/parse ^String body)]
    (when-let [el (.selectFirst doc ".spl-banner")]
      [(.className el) (.text el)])))

(defn- invited-user! []
  (let [email (str "invited-" (random-uuid) "@test.com")]
    (user.i/create! *db* {:email email
                          :password "irrelevantpassword"
                          :role :reader
                          :status :invited})
    (user.i/get-by-email *db* email)))

(deftest test-resending-says-so
  (testing "the button posts with hx-swap=none, so a redirect left the message
            in the session for a page load that never happened and resending
            looked like it did nothing"
    (let [admin-email (str "admin-" (random-uuid) "@test.com")
          _ (user.i/create! *db* {:email admin-email
                                  :password "testpassword123"
                                  :role :admin})
          user (invited-user!)
          sess (app.test/login admin-email "testpassword123")
          ;; The button sends the token through hx-vals, so the post needs it.
          {:keys [response] :as sess} (-> sess (peri/request "/settings/users/invite"))
          token (test.i/response-anti-forgery-token response)
          before (count @(:sent-messages *mail-client*))
          {:keys [response]} (-> sess
                                 (peri/request (str "/settings/users/" (:user/id user)
                                                    "/resend-invitation")
                                               :request-method :post
                                               ;; The button is an hx-post, and
                                               ;; the banner is swapped in out
                                               ;; of band — which the
                                               ;; middleware only does for an
                                               ;; htmx request.
                                               :headers {"hx-request" "true"}
                                               :params {:__anti-forgery-token token}))
          [classes text] (banner (:body response))]
      (is (= 200 (:status response)))
      (is (some? classes) "the response carries a banner to swap in")
      (is (re-find #"spl-banner--ok" (str classes))
          (str "a success banner, got " classes))
      (is (re-find (re-pattern (:user/email user)) (str text))
          (str "naming who it went to, got " (pr-str text)))
      (is (= (inc before) (count @(:sent-messages *mail-client*)))
          "and an email really went"))))

(deftest test-resending-to-an-active-user-refuses
  (testing "only an invitation can be resent"
    (let [admin-email (str "admin-" (random-uuid) "@test.com")
          _ (user.i/create! *db* {:email admin-email
                                  :password "testpassword123"
                                  :role :admin})
          active-email (str "active-" (random-uuid) "@test.com")
          _ (user.i/create! *db* {:email active-email
                                  :password "testpassword123"
                                  :role :reader})
          active (user.i/get-by-email *db* active-email)
          sess (app.test/login admin-email "testpassword123")
          {:keys [response] :as sess} (-> sess (peri/request "/settings/users/invite"))
          token (test.i/response-anti-forgery-token response)
          {:keys [response]} (-> sess
                                 (peri/request (str "/settings/users/" (:user/id active)
                                                    "/resend-invitation")
                                               :request-method :post
                                               ;; The button is an hx-post, and
                                               ;; the banner is swapped in out
                                               ;; of band — which the
                                               ;; middleware only does for an
                                               ;; htmx request.
                                               :headers {"hx-request" "true"}
                                               :params {:__anti-forgery-token token}))
          [classes _] (banner (:body response))]
      (is (= 200 (:status response)))
      (is (re-find #"spl-banner--danger" (str classes))
          (str "a danger banner, got " classes)))))
