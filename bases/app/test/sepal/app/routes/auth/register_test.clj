(ns sepal.app.routes.auth.register-test
  (:require [clojure.test :refer :all]
            [malli.generator :as mg]
            [matcher-combinators.test :refer [match?]]
            [peridot.core :as peri]
            [ring.middleware.session.store :as store]
            [sepal.app.test.system :refer [*app* *db* *system* default-system-fixture]]
            [sepal.user.interface :as user.i]
            [sepal.user.interface.spec :as user.spec])
  (:import [org.jsoup Jsoup]))

(use-fixtures :once default-system-fixture)

(deftest register-test
  (let [db *db*]
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
      (let [sess (-> (peri/session *app*)
                     (peri/request "/register"))
            token (-> sess
                      :response
                      :body
                      Jsoup/parse
                      (.selectFirst "input[name=__anti-forgery-token]")
                      (.attr "value"))
            params {:__anti-forgery-token token
                    :email (mg/generate user.spec/email)
                    :password (mg/generate user.spec/password)}
            resp (-> sess
                     (peri/request "/register"
                                   :request-method :post
                                   :params params)
                     :response)
            ring-session (get-in sess [:cookie-jar "localhost" "ring-session" :value])
            store (:sepal.app.ring/cookie-store *system*)
            ;; store (:sepal.app.session-store)
            ]

        (tap> (str "s: " (-> sess :cookie-jar)))
        (tap> (str "ring-session: " ring-session))
        (tap> (str "store: " store))

        (is (match? {:status 303
                     :headers {"Location" "/"}}
                    resp)
            (:body resp))
        ;; (is (= (:email params) (-> resp :session :user/email)))

        (tap> (str "session vals: " (store/read-session store ring-session)))
        (tap> (user.i/exists? db (-> resp :session :user/email)))
        (is (some? (user.i/verify-password db (:email params) (:password params))))))

;; TODO: test errors like invalid password
    ))
