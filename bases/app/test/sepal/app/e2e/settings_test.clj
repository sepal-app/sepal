(ns sepal.app.e2e.settings-test
  "End-to-end test for settings pages"
  (:require [clojure.test :refer [deftest is testing]]
            [malli.generator :as mg]
            [sepal.app.e2e.playwright :as pw]
            [sepal.app.e2e.server :as server]
            [sepal.user.interface :as user.i]
            [sepal.user.interface.spec :as user.spec]
            [zodiac.ext.sql :as z.sql]))

(deftest ^:e2e settings-flow
  (testing "Settings pages: login -> profile -> security -> organization"
    (server/with-server
      (fn [system]
        (let [base-url (server/server-url system)
              db (-> system :sepal.app.server/zodiac ::z.sql/db)
              ;; Create test user programmatically (registration is disabled)
              email (mg/generate user.spec/email)
              password "TestPassword123!"]
          ;; Create user in database
          (user.i/create! db {:email email
                              :password password
                              :role :admin})
          (pw/with-browser
            (testing "1. Login with test user"
              (pw/navigate (str base-url "/login"))
              (pw/wait-for-selector "input[name=\"email\"]" 10000)

              (pw/fill "input[name=\"email\"]" email)
              (pw/fill "input[name=\"password\"]" password)

              (pw/click "button:has-text(\"Login\")")

              ;; Wait for network to settle, then for redirect to activity page
              (pw/wait-for-load-state :networkidle)
              (pw/wait-for-url #"/activity" 60000)

              (is (re-find #"/activity" (pw/get-url))
                  "Should redirect to Activity page after login"))

            (testing "2. Navigate to settings"
              (pw/click "a:has-text(\"Settings\")")
              (pw/wait-for-url #"/settings")

              (is (re-find #"/settings" (pw/get-url))
                  "Should be on settings page"))

            (testing "3. Update profile settings"
              ;; Should be on profile page by default
              (pw/wait-for-selector "input[name=\"full-name\"]")

              ;; Fill profile form
              (pw/fill "input[name=\"full-name\"]" "Test User")
              (pw/fill "input[name=\"email\"]" "updated@example.com")

              ;; Wait for button to be enabled (Alpine.js form-state tracks dirty/valid)
              (pw/wait-for-enabled "button:has-text(\"Save changes\")")
              (pw/click "button:has-text(\"Save changes\")")
              (pw/wait-for-url #"/settings/profile")

              ;; Verify we're back on the profile page with updated values
              (pw/wait-for-selector "input[name=\"full-name\"]")
              (is (re-find #"/settings/profile" (pw/get-url))
                  "Should redirect back to profile page after update"))

            (testing "4. Update security settings (change password)"
              ;; Navigate to security page
              (pw/click "a:has-text(\"Security\")")
              (pw/wait-for-url #"/settings/security")
              (pw/wait-for-selector "input[name=\"current_password\"]")

              ;; Fill password change form
              (pw/fill "input[name=\"current_password\"]" "TestPassword123!")
              (pw/fill "input[name=\"new_password\"]" "NewPassword456!")
              (pw/fill "input[name=\"confirm_password\"]" "NewPassword456!")

              ;; Wait for button to be enabled and submit
              (pw/wait-for-enabled "button:has-text(\"Change password\")")
              (pw/click "button:has-text(\"Change password\")")
              (pw/wait-for-url #"/settings/security")

              ;; Verify we're back on the security page
              (pw/wait-for-selector "input[name=\"current_password\"]")
              (is (re-find #"/settings/security" (pw/get-url))
                  "Should redirect back to security page after password change"))

            (testing "5. Update organization settings"
              ;; Navigate to organization page
              (pw/click "a:has-text(\"General\")")
              (pw/wait-for-url #"/settings/organization")
              (pw/wait-for-selector "input[name=\"long_name\"]")

              ;; Fill organization form
              (pw/fill "input[name=\"long_name\"]" "Test Botanical Garden")
              (pw/fill "input[name=\"short_name\"]" "Test Garden")
              (pw/fill "input[name=\"abbreviation\"]" "TBG")
              (pw/fill "input[name=\"email\"]" "info@testgarden.org")
              (pw/fill "input[name=\"phone\"]" "555-123-4567")
              (pw/fill "input[name=\"website\"]" "https://testgarden.org")
              (pw/fill "input[name=\"address_street\"]" "123 Garden Lane")
              (pw/fill "input[name=\"address_city\"]" "Greenville")
              (pw/fill "input[name=\"address_postal_code\"]" "12345")
              (pw/fill "input[name=\"address_country\"]" "Canada")

              ;; Wait for button to be enabled and submit
              (pw/wait-for-enabled "button:has-text(\"Save changes\")")
              (pw/click "button:has-text(\"Save changes\")")
              (pw/wait-for-url #"/settings/organization")

              ;; Verify we're back on the organization page
              (pw/wait-for-selector "input[name=\"long_name\"]")
              (is (re-find #"/settings/organization" (pw/get-url))
                  "Should redirect back to organization page after update"))))))))
