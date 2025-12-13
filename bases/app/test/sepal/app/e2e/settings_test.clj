(ns sepal.app.e2e.settings-test
  "End-to-end test for settings pages"
  (:require [clojure.test :refer [deftest is testing]]
            [malli.generator :as mg]
            [sepal.app.e2e.playwright :as pw]
            [sepal.app.e2e.server :as server]
            [sepal.user.interface.spec :as user.spec]))

(deftest ^:e2e settings-flow
  (testing "Settings pages: register -> profile -> security -> organization"
    (server/with-server
      (fn [system]
        (let [base-url (server/server-url system)]
          (pw/with-browser
            (testing "1. Register new user"
              (let [email (mg/generate user.spec/email)
                    password "TestPassword123!"]
                (pw/navigate (str base-url "/register"))
                (pw/wait-for-selector "input[name=\"email\"]" 10000)

                (pw/fill "input[name=\"email\"]" email)
                (pw/fill "input[name=\"password\"]" password)
                (pw/fill "input[name=\"confirm-password\"]" password)

                (pw/click "button:has-text(\"Create account\")")
                (pw/wait-for-url #"/activity")

                (is (re-find #"/activity" (pw/get-url))
                    "Should redirect to Activity page after registration")))

            (testing "2. Navigate to settings"
              (pw/click "a:has-text(\"Settings\")")
              (pw/wait-for-url #"/settings")

              (is (re-find #"/settings" (pw/get-url))
                  "Should be on settings page"))

            (testing "3. Update profile settings"
              ;; Should be on profile page by default
              (pw/wait-for-selector "input[name=\"full_name\"]")

              ;; Fill profile form
              (pw/fill "input[name=\"full_name\"]" "Test User")
              (pw/fill "input[name=\"email\"]" "updated@example.com")

              ;; Submit and wait for redirect back to profile
              (pw/click "button:has-text(\"Save changes\")")
              (pw/wait-for-url #"/settings/profile")

              ;; Verify we're back on the profile page with updated values
              (pw/wait-for-selector "input[name=\"full_name\"]")
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

              ;; Submit and wait for redirect
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

              ;; Submit and wait for redirect
              (pw/click "button:has-text(\"Save changes\")")
              (pw/wait-for-url #"/settings/organization")

              ;; Verify we're back on the organization page
              (pw/wait-for-selector "input[name=\"long_name\"]")
              (is (re-find #"/settings/organization" (pw/get-url))
                  "Should redirect back to organization page after update"))))))))
