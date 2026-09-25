(ns sepal.app.e2e.happy-path-test
  "End-to-end e2e test for happy path user flow"
  (:require [clojure.test :refer [deftest is testing]]
            [sepal.app.e2e.playwright :as pw]
            [sepal.app.e2e.server :as server]
            [sepal.app.test.email :as test.email]
            [sepal.user.interface :as user.i]))

(deftest ^:e2e happy-path-flow
  ;; "Complete user flow: login -> create contact -> create taxa -> create accession -> create location -> create material"
  (testing "Server and browser setup"
    (server/with-server
      (fn [started]
        (let [base-url (server/server-url started)
              db (server/db started)
              ;; Create test user programmatically (registration is disabled)
              email (test.email/unique)
              password "TestPassword123!"]
          ;; Create user in database
          (user.i/create! db {:email email
                              :password password
                              :role :admin})
          (pw/with-browser
            (testing "1. Login with test user"
              (pw/navigate (str base-url "/login"))
              ;; Give extra time for first page load after server start
              (pw/wait-for-selector "input[name=\"email\"]" 10000)

              ;; Fill login form
              (pw/fill "input[name=\"email\"]" email)
              (pw/fill "input[name=\"password\"]" password)

              ;; Submit form
              (pw/click "button:has-text(\"Login\")")

              ;; Wait for network to settle, then for redirect to activity page
              (pw/wait-for-load-state :networkidle)
              (pw/wait-for-url #"/activity" 60000)

              (is (re-find #"/activity" (pw/get-url))
                  "Should redirect to Activity page after login"))

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
              ;; Rank is a plain <select>: it was only ever wrapped for looks.
              (pw/select-option "select[name=\"rank\"]" "genus")

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
              (pw/select-option "select[name=\"rank\"]" "species")
              ;; parent-id is a <sepal-combobox>: type in the field itself.
              (pw/click "#parent-id-input")
              (pw/fill "#parent-id-input" "Ros")
              (pw/wait-for-attached "#parent-id-listbox [role=option]:has-text(\"Rosa\")")
              (pw/press "ArrowDown")
              (pw/press "Enter")

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
              ;; taxon-id is a <sepal-combobox>: type in the field itself.
              (pw/click "#taxon-id-input")
              (pw/fill "#taxon-id-input" "Rosa")
              (pw/wait-for-attached "#taxon-id-listbox [role=option]:has-text(\"Rosa canina\")")
              (pw/press "ArrowDown")
              (pw/press "Enter")

              ;; The Collection tab holds wild-collection data, so it is only
              ;; available on a wild accession. Step 5.5 opens it.
              (pw/select-option "select#provenance-type" "wild")

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

              ;; A <sepal-combobox>: type in the field itself.
              (pw/click "#accession-id-input")
              (pw/fill "#accession-id-input" "ACC")
              (pw/wait-for-attached "#accession-id-listbox [role=option]:has-text(\"ACC-001\")")
              (pw/press "ArrowDown")
              (pw/press "Enter")
              (pw/wait-for-hidden "#accession-id-listbox")

              ;; Choosing the accession swaps in the suggested code, so the
              ;; code is typed after that swap lands, not before it.
              (pw/wait-for-selector "input[name=\"code\"][value=\"1\"]")
              (pw/fill "input[name=\"code\"]" "MAT-001")
              ;; location-id is a <sepal-combobox>: type in the field itself.
              (pw/click "#location-id-input")
              (pw/fill "#location-id-input" "Gre")
              (pw/wait-for-attached "#location-id-listbox [role=option]:has-text(\"Greenhouse A\")")
              (pw/press "ArrowDown")
              (pw/press "Enter")

              ;; Fill other fields
              (pw/fill "input[name=\"quantity\"]" "5")
              (pw/select-option "select[name=\"status\"]" "alive")

              (pw/select-option "select[name=\"type\"]" "seed")

              ;; Submit
              (pw/click "button:has-text(\"Save\")")

              (pw/wait-for-url #"/material/\d+")

              (is (pw/visible? "text=MAT-001")
                  "Should show material code"))))))))
