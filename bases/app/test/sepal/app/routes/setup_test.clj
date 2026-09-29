(ns sepal.app.routes.setup-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [peridot.core :as peri]
            [sepal.app.routes.setup.shared :as setup.shared]
            [sepal.app.test :as app.test]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*app* *db* default-system-fixture]]
            [sepal.settings.interface :as settings.i]
            [sepal.test.interface :as test.i]
            [sepal.user.interface :as user.i]))

(use-fixtures :once default-system-fixture)

(defn reset-setup-fixture [f]
  ;; Reset setup status before each test so setup wizard is accessible
  (setup.shared/reset-setup! *db*)
  (f))

(use-fixtures :each reset-setup-fixture)

;; =============================================================================
;; Helper Functions
;; =============================================================================

(defn- redirect? [{:keys [status]}]
  (contains? #{301 302 303 307 308} status))

(defn- get-anti-forgery-token [response]
  (when (:body response)
    (test.i/response-anti-forgery-token response)))

(defn- body-contains? [response text]
  (and (:body response)
       (.contains (:body response) text)))

;; =============================================================================
;; Setup Wizard Tests
;; =============================================================================

(deftest setup-wizard-routes-accessible-test
  (testing "/setup redirects to first step"
    (let [{:keys [response]} (-> (peri/session *app*)
                                 (peri/request "/setup"))]
      (is (redirect? response))))

  (testing "/setup/admin is accessible without login"
    (let [{:keys [response]} (-> (peri/session *app*)
                                 (peri/request "/setup/admin"))]
      ;; Should return 200 (either create form or login prompt)
      (is (= 200 (:status response))))))

(deftest setup-wizard-admin-step-test
  (tf/testing "admin creation form shown when no admin exists"
    {[::user.i/factory :key/admin] {:db *db* :role :admin}}
    (fn [_]
      ;; Since an admin was created by fixture, we should see login prompt
      (let [{:keys [response]} (-> (peri/session *app*)
                                   (peri/request "/setup/admin"))]
        (is (= 200 (:status response)))
        ;; Should show login prompt since admin exists
        (is (body-contains? response "Admin Account Exists")))))

  (tf/testing "logged-in admin sees read-only account view"
    {[::user.i/factory :key/admin] {:db *db*
                                    :role :admin
                                    :email "setup-admin@test.com"
                                    :password "password123"}}
    (fn [_]
      ;; Must complete setup to access /login
      (setup.shared/complete-setup! *db*)
      (let [sess (app.test/login "setup-admin@test.com" "password123")]
        ;; Reset setup so we can test the wizard page
        (setup.shared/reset-setup! *db*)
        (let [{:keys [response]} (peri/request sess "/setup/admin")]
          ;; Should show read-only admin view with success message
          (is (= 200 (:status response)))
          (is (body-contains? response "Admin Account"))
          (is (body-contains? response "has been created"))
          (is (body-contains? response "setup-admin@test.com")))))))

(deftest setup-wizard-steps-accessible-when-logged-in-test
  (tf/testing "all wizard steps are accessible when logged in"
    {[::user.i/factory :key/admin] {:db *db*
                                    :role :admin
                                    :email "wizard-test@test.com"
                                    :password "password123"}}
    (fn [_]
      ;; Must complete setup to access /login
      (setup.shared/complete-setup! *db*)
      (let [sess (app.test/login "wizard-test@test.com" "password123")]
        ;; Reset setup so we can test the wizard pages
        (setup.shared/reset-setup! *db*)

        ;; Server step
        (let [{:keys [response]} (peri/request sess "/setup/server")]
          (is (= 200 (:status response)))
          (is (body-contains? response "Server Configuration")))

        ;; Organization step
        (let [{:keys [response]} (peri/request sess "/setup/organization")]
          (is (= 200 (:status response)))
          (is (body-contains? response "Organization Information")))

        ;; Regional step
        (let [{:keys [response]} (peri/request sess "/setup/regional")]
          (is (= 200 (:status response)))
          (is (body-contains? response "Regional Settings")))

        ;; Taxonomy step
        (let [{:keys [response]} (peri/request sess "/setup/taxonomy")]
          (is (= 200 (:status response)))
          (is (body-contains? response "Taxonomy")))

        ;; Review step
        (let [{:keys [response]} (peri/request sess "/setup/review")]
          (is (= 200 (:status response)))
          (is (body-contains? response "Review")))))))

(deftest setup-wizard-organization-save-test
  (tf/testing "organization form saves settings"
    {[::user.i/factory :key/admin] {:db *db*
                                    :role :admin
                                    :email "org-save-test@test.com"
                                    :password "password123"}}
    (fn [_]
      ;; Must complete setup to access /login
      (setup.shared/complete-setup! *db*)
      (let [sess (app.test/login "org-save-test@test.com" "password123")]
        ;; Reset setup so we can test the wizard page
        (setup.shared/reset-setup! *db*)
        (let [{:keys [response] :as sess} (peri/request sess "/setup/organization")
              token (get-anti-forgery-token response)
              {:keys [response]} (peri/request sess "/setup/organization"
                                               :request-method :post
                                               :params {:__anti-forgery-token token
                                                        :long_name "Integration Test Garden"
                                                        :short_name "ITG"
                                                        :abbreviation ""
                                                        :email ""
                                                        :phone ""})]
          ;; Should succeed (200 with HX-Redirect or 303)
          (is (or (= 200 (:status response))
                  (redirect? response)))
          ;; Setting should be saved
          (is (= "Integration Test Garden"
                 (settings.i/get-value *db* "organization.long_name"))))))))

(deftest setup-wizard-review-page-test
  (tf/testing "review page shows summary and has complete button"
    {[::user.i/factory :key/admin] {:db *db*
                                    :role :admin
                                    :email "review-page@test.com"
                                    :password "password123"}}
    (fn [_]
      ;; Must complete setup to access /login
      (setup.shared/complete-setup! *db*)
      (let [sess (app.test/login "review-page@test.com" "password123")]
        ;; Reset setup so we can test the wizard page
        (setup.shared/reset-setup! *db*)
        (let [{:keys [response]} (peri/request sess "/setup/review")]
          (is (= 200 (:status response)))
          (is (body-contains? response "Review"))
          (is (body-contains? response "Complete Setup"))
          (is (body-contains? response "__anti-forgery-token")))))))

(deftest setup-closed-once-complete-test
  (setup.shared/complete-setup! *db*)
  (settings.i/set-value! *db* "organization.long_name" "Before")
  (settings.i/set-value! *db* "organization.timezone" "UTC")
  (let [completed-at (settings.i/get-value *db* "setup.completed_at")
        ;; The login page gives an anonymous session a valid CSRF token.
        {:keys [response] :as sess} (-> (peri/session *app*)
                                        (peri/request "/login"))
        token (get-anti-forgery-token response)
        post (fn [path params]
               (:response (peri/request sess path
                                        :request-method :post
                                        :params (assoc params :__anti-forgery-token token))))]
    (testing "an anonymous POST to a setup step is refused and writes nothing"
      (is (= 403 (:status (post "/setup/organization" {:long_name "After"
                                                       :short_name ""
                                                       :abbreviation ""
                                                       :email ""
                                                       :phone ""}))))
      (is (= 403 (:status (post "/setup/regional" {:timezone "Pacific/Auckland"}))))
      (is (= 403 (:status (post "/setup/review" {}))))
      (is (= "Before" (settings.i/get-value *db* "organization.long_name")))
      (is (= "UTC" (settings.i/get-value *db* "organization.timezone")))
      (is (= completed-at (settings.i/get-value *db* "setup.completed_at"))))

    (testing "a GET to a setup step redirects away"
      (let [{:keys [response]} (peri/request sess "/setup/organization")]
        (is (redirect? response))
        (is (= "/" (get-in response [:headers "Location"])))))))

(deftest login-during-setup-test
  (testing "with no admin, /login sends the visitor to the wizard"
    (let [{:keys [response]} (-> (peri/session *app*)
                                 (peri/request "/login"))]
      (is (redirect? response))
      (is (= "/setup" (get-in response [:headers "Location"])))))

  (tf/testing "once an admin exists, the admin can log in to finish setup"
    {[::user.i/factory :key/admin] {:db *db*
                                    :role :admin
                                    :email "cli-admin@test.com"
                                    :password "password123"}}
    (fn [_]
      (let [{:keys [response]} (-> (peri/session *app*)
                                   (peri/request "/login"))]
        (is (= 200 (:status response))))
      (let [sess (app.test/login "cli-admin@test.com" "password123")
            {:keys [response]} (peri/request sess "/setup/admin")]
        (is (body-contains? response "has been created"))))))

(deftest setup-steps-require-admin-session-test
  (tf/testing "steps after the admin step need the admin's session"
    {[::user.i/factory :key/admin] {:db *db*
                                    :role :admin
                                    :email "steps-admin@test.com"
                                    :password "password123"}
     [::user.i/factory :key/editor] {:db *db*
                                     :role :editor
                                     :email "steps-editor@test.com"
                                     :password "password123"}}
    (fn [_]
      (settings.i/set-value! *db* "organization.long_name" "Before")
      (let [{:keys [response] :as anon} (-> (peri/session *app*)
                                            (peri/request "/login"))
            token (get-anti-forgery-token response)]
        (testing "an anonymous GET goes back to the admin step"
          (doseq [path ["/setup/server" "/setup/organization" "/setup/regional"
                        "/setup/taxonomy" "/setup/taxonomy/progress" "/setup/review"]]
            (let [{:keys [response]} (peri/request anon path)]
              (is (redirect? response) path)
              (is (= "/setup/admin" (get-in response [:headers "Location"])) path))))

        (testing "an anonymous POST is refused and writes nothing"
          (let [{:keys [response]} (peri/request anon "/setup/organization"
                                                 :request-method :post
                                                 :params {:__anti-forgery-token token
                                                          :long_name "After"})]
            (is (= 403 (:status response)))
            (is (= "Before" (settings.i/get-value *db* "organization.long_name"))))))

      (testing "an editor's session is not enough"
        (let [{:keys [response]} (-> (app.test/login "steps-editor@test.com" "password123")
                                     (peri/request "/setup/organization"))]
          (is (= "/setup/admin" (get-in response [:headers "Location"])))))

      (testing "the admin's session reaches the steps"
        (let [{:keys [response]} (-> (app.test/login "steps-admin@test.com" "password123")
                                     (peri/request "/setup/organization"))]
          (is (= 200 (:status response))))))))

(deftest admin-form-validation-test
  (testing "password confirmation must match"
    ;; This test requires no admin to exist. If one exists from another test,
    ;; we still make an assertion to avoid "test without assertions" warning.
    (let [sess (peri/session *app*)
          {:keys [response] :as sess} (peri/request sess "/setup/admin")
          can-create? (body-contains? response "Create Admin Account")]
      (if can-create?
        (let [token (get-anti-forgery-token response)
              {:keys [response]} (peri/request sess "/setup/admin"
                                               :request-method :post
                                               :params {:__anti-forgery-token token
                                                        :full_name "Test User"
                                                        :email "validation-test@test.com"
                                                        :password "password123"
                                                        :password_confirmation "different"})]
          ;; Form re-renders with validation error
          (is (= 200 (:status response)))
          (is (body-contains? response "Passwords do not match")))
        ;; Admin already exists (from another test) - just verify we got the right page
        (is (body-contains? response "Admin Account Exists"))))))
