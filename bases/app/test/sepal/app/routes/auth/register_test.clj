(ns sepal.app.routes.auth.register-test
  (:require [clojure.test :refer :all]
            [malli.generator :as mg]
            [matcher-combinators.test :refer [match?]]
            [peridot.core :as peri]
            [ring.middleware.session.store :as store]
            [sepal.app.test.system :refer [*app* *db* default-system-fixture *cookie-store*]]
            [sepal.test.interface :as test.i]
            [sepal.user.interface.spec :as user.spec]
            [sepal.validation.interface :as validation.i])
  (:import [org.jsoup Jsoup]))

(use-fixtures :once default-system-fixture)

(deftest register-test
  (let [_db *db*]
    (testing "get"
      (let [resp (*app* {:request-method :get
                         :uri "/register"})
            body (Jsoup/parse (:body resp))]
        (is (= 200 (:status resp)))
        (is (some? (-> body
                       (.selectFirst "input[name=email]")
                       (.selectFirst "input[type=email]"))))
        (is (some? (-> body
                       (.selectFirst "input[name=password]")
                       (.selectFirst "input[type=password]"))))
        (is (some? (-> body
                       (.selectFirst "input[name=confirm-password]")
                       (.selectFirst "input[type=password]"))))
        (is (some? (-> body
                       (.selectFirst "input[name=__anti-forgery-token]")
                       (.selectFirst "input[type=hidden]"))))))

    (testing "post"
      (let [{:keys [response] :as sess} (-> (peri/session *app*)
                                            (peri/request "/register"))
            token (test.i/response-anti-forgery-token response)
            params {:__anti-forgery-token token
                    :email (mg/generate user.spec/email)
                    :password (mg/generate user.spec/password)}
            {:keys [response]
             :as sess} (-> sess
                           (peri/request "/register"
                                         :request-method :post
                                         :params params)
                           ;; (peri/follow-redirect)
                           (peri/follow-redirect)
                           (peri/follow-redirect))
            ring-session (->> (test.i/ring-session-cookie sess)
                              (store/read-session *cookie-store*))]
        (is (match? {:status 200
                     :headers {"content-type" "text/html"}}
                    response)
            (:body response))
        ;; Test that the user is in the session
        (is (match? {:user/id int?
                     :user/email validation.i/email-re}
                    ring-session))
        ;; Eventually redirects to the activity page
        (is (= "Activity"
               (-> response
                   :body
                   (Jsoup/parse)
                   (.selectFirst ".breadcrumbs span")
                   (.text))))))))
