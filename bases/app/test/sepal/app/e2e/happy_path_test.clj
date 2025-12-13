(ns sepal.app.e2e.happy-path-test
  "End-to-end e2e test for happy path user flow"
  (:require [clojure.test :refer [deftest is testing]]
            [malli.generator :as mg]
            [sepal.app.e2e.playwright :as pw]
            [sepal.app.e2e.server :as server]
            [sepal.user.interface.spec :as user.spec]))

(deftest ^:e2e happy-path-flow
  ;; "Complete user flow: register -> create contact -> create taxa -> create accession -> create location -> create material"
  (testing "Server and browser setup"
    (server/with-server
      (fn [system]
        (let [base-url (server/server-url system)]
          (pw/with-browser
            (testing "1. Register new user"
              (let [email (mg/generate user.spec/email)
                    password "TestPassword123!"]
                (pw/navigate (str base-url "/register"))
                ;; Give extra time for first page load after server start
                (pw/wait-for-selector "input[name=\"email\"]" 10000)

                ;; Fill registration form
                (pw/fill "input[name=\"email\"]" email)
                (pw/fill "input[name=\"password\"]" password)
                (pw/fill "input[name=\"confirm-password\"]" password)

                ;; Submit form
                (pw/click "button:has-text(\"Create account\")")

                ;; Wait for redirect to activity page
                (pw/wait-for-url #"/activity")

                (is (re-find #"/activity" (pw/get-url))
                    "Should redirect to Activity page after registration")))

            (testing "2. Create new contact"
              (pw/navigate (str base-url "/contact/new/"))
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
              (pw/navigate (str base-url "/taxon/new/"))
              (pw/wait-for-selector "input[name=\"name\"]")

              ;; Fill taxon form
              (pw/fill "input[name=\"name\"]" "Rosa")
              ;; Select rank from SlimSelect dropdown
              (pw/click "select[name=\"rank\"] + .ss-main") ;; Open SlimSelect
              (pw/wait-for-selector ".ss-content.ss-open")
              (pw/click ".ss-content.ss-open .ss-option:has-text(\"genus\")") ;; Select genus option

              ;; Submit
              (pw/click "button:has-text(\"Save\")")

              ;; Wait for redirect
              (pw/wait-for-url #"/taxon/\d+")

              (is (pw/visible? "text=Rosa")
                  "Should show parent taxon name"))

            (testing "4. Create child taxon with parent"
              (pw/navigate (str base-url "/taxon/new/"))
              (pw/wait-for-selector "input[name=\"name\"]")

              ;; Fill child taxon form
              (pw/fill "input[name=\"name\"]" "Rosa canina")
              ;; Select rank from SlimSelect dropdown
              (pw/click "select[name=\"rank\"] + .ss-main")
              (pw/wait-for-selector ".ss-content.ss-open")
              (pw/click ".ss-content.ss-open .ss-option:has-text(\"species\")")
              (pw/wait-for-hidden ".ss-content.ss-open")

              ;; Select parent taxon via SlimSelect (uses async search, min 2 chars)
              (pw/click "select#parent-id + .ss-main") ;; Open SlimSelect dropdown
              (pw/wait-for-selector ".ss-content.ss-open")
              (pw/fill ".ss-content.ss-open .ss-search input" "Ros") ;; Type partial search (3 chars triggers search)
              (pw/wait-for-attached ".ss-content.ss-open .ss-option:has-text(\"Rosa\")") ;; Wait for AJAX search results
              (pw/press "ArrowDown") ;; Navigate to first result
              (pw/press "Tab") ;; Select it and move to next field
              (pw/wait-for-hidden ".ss-content.ss-open")

              ;; Submit
              (pw/click "button:has-text(\"Save\")")

              (pw/wait-for-url #"/taxon/\d+")

              (is (pw/visible? "text=Rosa canina")
                  "Should show child taxon name"))

            (testing "5. Create accession with taxon"
              (pw/navigate (str base-url "/accession/new/"))
              (pw/wait-for-selector "input[name=\"code\"]")

              ;; Fill accession form
              (pw/fill "input[name=\"code\"]" "ACC-001")

              ;; Select taxon via SlimSelect (async search)
              (pw/click "select#taxon-id + .ss-main")
              (pw/wait-for-selector ".ss-content.ss-open")
              (pw/fill ".ss-content.ss-open .ss-search input" "Rosa")
              (pw/wait-for-attached ".ss-content.ss-open .ss-option:has-text(\"Rosa canina\")") ;; Wait for AJAX
              (pw/press "ArrowDown")
              (pw/press "Tab")
              (pw/wait-for-hidden ".ss-content.ss-open")

              ;; Submit
              (pw/click "button:has-text(\"Save\")")

              (pw/wait-for-url #"/accession/\d+")

              (is (pw/visible? "text=ACC-001")
                  "Should show accession code"))

            (testing "5.5 Add collection data to accession"
              ;; Click on Collection tab
              (pw/click "text=Collection")
              (pw/wait-for-url #"/accession/\d+/collection/")
              (pw/wait-for-selector "input[name=\"collector\"]")

              ;; Fill collection form
              (pw/fill "input[name=\"collector\"]" "Dr. Jane Botanist")
              (pw/fill "input[name=\"collected-date\"]" "2024-06-15")
              (pw/fill "input[name=\"country\"]" "Canada")
              (pw/fill "input[name=\"province\"]" "British Columbia")
              (pw/fill "input[name=\"locality\"]" "Stanley Park")
              (pw/fill "textarea[name=\"habitat\"]" "Temperate rainforest understory")
              (pw/fill "input[name=\"lat\"]" "49.3017")
              (pw/fill "input[name=\"lng\"]" "-123.1417")
              (pw/fill "input[name=\"elevation\"]" "25")

              ;; Submit
              (pw/click "button:has-text(\"Save\")")

              ;; Wait for page to reload after save
              (pw/wait-for-selector "input[name=\"collector\"][value=\"Dr. Jane Botanist\"]")

              (is (pw/visible? "input[name=\"collector\"][value=\"Dr. Jane Botanist\"]")
                  "Should show saved collector name"))

            (testing "6. Create location"
              (pw/navigate (str base-url "/location/new/"))
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
              (pw/navigate (str base-url "/material/new/"))
              (pw/wait-for-selector "input[name=\"code\"]")

              ;; Fill material form
              (pw/fill "input[name=\"code\"]" "MAT-001")

              ;; Select accession via SlimSelect (async search)
              (pw/click "select#accession-id + .ss-main")
              (pw/wait-for-selector ".ss-content.ss-open")
              (pw/fill ".ss-content.ss-open .ss-search input" "ACC")
              (pw/wait-for-attached ".ss-content.ss-open .ss-option:has-text(\"ACC-001\")") ;; Wait for AJAX
              (pw/press "ArrowDown")
              (pw/press "Tab")

              ;; Wait for accession dropdown to fully close
              (pw/wait-for-hidden ".ss-content.ss-open")

              ;; Select location via SlimSelect (async search)
              (pw/click "select#location-id + .ss-main")
              (pw/wait-for-selector ".ss-content.ss-open")
              (pw/fill ".ss-content.ss-open .ss-search input" "Gre")
              (pw/wait-for-attached ".ss-content.ss-open .ss-option:has-text(\"Greenhouse A\")") ;; Extra time for AJAX
              (pw/press "ArrowDown")
              (pw/press "Tab")
              (pw/wait-for-hidden ".ss-content.ss-open")

              ;; Fill other fields
              (pw/fill "input[name=\"quantity\"]" "5")
              ;; Select status and type from SlimSelect dropdowns
              (pw/click "select[name=\"status\"] + .ss-main")
              (pw/wait-for-selector ".ss-content.ss-open")
              (pw/fill ".ss-content.ss-open .ss-search input" "active")
              (pw/press ".ss-content.ss-open .ss-search input" "Tab")
              (pw/wait-for-hidden ".ss-content.ss-open")

              (pw/click "select[name=\"type\"] + .ss-main")
              (pw/wait-for-selector ".ss-content.ss-open")
              (pw/fill ".ss-content.ss-open .ss-search input" "seed")
              (pw/press ".ss-content.ss-open .ss-search input" "Tab")
              (pw/wait-for-hidden ".ss-content.ss-open")

              ;; Submit
              (pw/click "button:has-text(\"Save\")")

              (pw/wait-for-url #"/material/\d+")

              (is (pw/visible? "text=MAT-001")
                  "Should show material code"))))))))
