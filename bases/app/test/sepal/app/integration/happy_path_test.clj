(ns sepal.app.integration.happy-path-test
  "End-to-end integration test for happy path user flow"
  (:require [clojure.test :refer [deftest is testing]]
            [malli.generator :as mg]
            [sepal.app.integration.playwright :as pw]
            [sepal.app.integration.server :as server]
            [sepal.user.interface.spec :as user.spec]))

(deftest ^:integration happy-path-flow
  "Complete user flow: register -> create contact -> create taxa -> create accession -> create location -> create material"
  (testing "Server and browser setup"
    (server/with-server
      (fn [_system]
        (pw/with-browser
          (testing "1. Register new user"
            (let [email (mg/generate user.spec/email)
                  password "TestPassword123!"]
              (pw/navigate "http://localhost:3000/register")
              ;; Give extra time for first page load after server start
              (pw/wait-for-selector "input[name=\"email\"]" 10000)

              ;; Fill registration form
              (pw/fill "input[name=\"email\"]" email)
              (pw/fill "input[name=\"password\"]" password)
              (pw/fill "input[name=\"confirm-password\"]" password)

              ;; Submit form
              (pw/click "button:has-text(\"Register\")")

              ;; Wait for redirect to activity page
              (pw/wait-for-url #"/activity")

              (is (re-find #"/activity" (pw/get-url))
                  "Should redirect to Activity page after registration")))

          (testing "2. Create new contact"
            (pw/navigate "http://localhost:3000/contact/new/")
            (pw/wait-for-selector "input[name=\"name\"]")

            ;; Fill contact form
            (pw/fill "input[name=\"name\"]" "Test Contact")
            (pw/fill "input[name=\"email\"]" "contact@example.com")
            (pw/fill "input[name=\"phone\"]" "555-1234")

            ;; Submit
            (pw/click "button:has-text(\"Save\")")

            ;; Wait for redirect to contact detail
            (pw/wait-for-url #"/contact/\d+")

            (is (pw/visible? "text=Test Contact")
                "Should show contact name on detail page"))

          (testing "3. Create parent taxon"
            (pw/navigate "http://localhost:3000/taxon/new/")
            (pw/wait-for-selector "input[name=\"name\"]")

            ;; Fill taxon form
            (pw/fill "input[name=\"name\"]" "Rosa")
            ;; Select rank from dropdown (regular select element)
            (pw/click "select[name=\"rank\"]")
            (pw/click "option[value=\"genus\"]")

            ;; Submit
            (pw/click "button:has-text(\"Save\")")

            ;; Wait for redirect
            (pw/wait-for-url #"/taxon/\d+")

            (is (pw/visible? "text=Rosa")
                "Should show parent taxon name"))

          (testing "4. Create child taxon with parent"
            (pw/navigate "http://localhost:3000/taxon/new/")
            (pw/wait-for-selector "input[name=\"name\"]")

            ;; Fill child taxon form
            (pw/fill "input[name=\"name\"]" "Rosa canina")
            ;; Select rank
            (pw/click "select[name=\"rank\"]")
            (pw/click "option[value=\"species\"]")

            ;; Select parent taxon via SlimSelect
            (pw/click ".ss-main")  ;; Open SlimSelect dropdown
            (pw/fill ".ss-search input" "Rosa")  ;; Type search query
            (pw/wait-for-selector ".ss-option")  ;; Wait for results
            (pw/click ".ss-option")  ;; Click first option

            ;; Submit
            (pw/click "button:has-text(\"Save\")")

            (pw/wait-for-url #"/taxon/\d+")

            (is (pw/visible? "text=Rosa canina")
                "Should show child taxon name"))

          (testing "5. Create accession with taxon"
            (pw/navigate "http://localhost:3000/accession/new/")
            (pw/wait-for-selector "input[name=\"code\"]")

            ;; Fill accession form
            (pw/fill "input[name=\"code\"]" "ACC-001")

            ;; Select taxon via SlimSelect
            (pw/click "select#taxon-id + .ss-main")
            (pw/fill ".ss-search input" "Rosa canina")
            (pw/wait-for-selector ".ss-option")
            (pw/click ".ss-option")

            ;; Submit
            (pw/click "button:has-text(\"Save\")")

            (pw/wait-for-url #"/accession/\d+")

            (is (pw/visible? "text=ACC-001")
                "Should show accession code"))

          (testing "6. Create location"
            (pw/navigate "http://localhost:3000/location/new/")
            (pw/wait-for-selector "input[name=\"name\"]")

            ;; Fill location form
            (pw/fill "input[name=\"name\"]" "Greenhouse A")
            (pw/fill "input[name=\"code\"]" "GH-A")
            (pw/fill "textarea[name=\"description\"]" "Main greenhouse")

            ;; Submit
            (pw/click "button:has-text(\"Save\")")

            (pw/wait-for-url #"/location/\d+")

            (is (pw/visible? "text=Greenhouse A")
                "Should show location name"))

          (testing "7. Create material with accession and location"
            (pw/navigate "http://localhost:3000/material/new/")
            (pw/wait-for-selector "input[name=\"code\"]")

            ;; Fill material form
            (pw/fill "input[name=\"code\"]" "MAT-001")

            ;; Select accession via SlimSelect
            (pw/click "select[name=\"accession-id\"] + .ss-main")
            (pw/fill ".ss-search input" "ACC")
            (pw/wait-for-selector ".ss-option")
            (pw/click ".ss-option")

            ;; Select location via SlimSelect
            (pw/click "select[name=\"location-id\"] + .ss-main")
            (pw/fill ".ss-search input" "Greenhouse")
            (pw/wait-for-selector ".ss-option")
            (pw/click ".ss-option")

            ;; Fill other fields
            (pw/fill "input[name=\"quantity\"]" "5")
            ;; Select status and type from regular dropdowns
            (pw/click "select[name=\"status\"]")
            (pw/click "option[value=\"active\"]")
            (pw/click "select[name=\"type\"]")
            (pw/click "option[value=\"seed\"]")

            ;; Submit
            (pw/click "button:has-text(\"Save\")")

            (pw/wait-for-url #"/material/\d+")

            (is (pw/visible? "text=MAT-001")
                "Should show material code")))))))
