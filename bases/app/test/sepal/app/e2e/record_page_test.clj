(ns sepal.app.e2e.record-page-test
  "E2E coverage for plan 046: the collapsible sections, the pinned record-page
  footer, and the visible panel scrollbars."
  (:require [clojure.test :refer [deftest is testing]]
            [malli.generator :as mg]
            [sepal.accession.interface :as acc.i]
            [sepal.app.e2e.playwright :as pw]
            [sepal.app.e2e.server :as server]
            [sepal.location.interface :as loc.i]
            [sepal.material.interface :as mat.i]
            [sepal.taxon.interface :as taxon.i]
            [sepal.user.interface :as user.i]
            [sepal.user.interface.spec :as user.spec]))

(defn- create-record-fixtures
  "A location, a taxon, an accession and a material, through the interfaces."
  [db]
  (let [loc (loc.i/create! db {:code "E2E-L1" :name "E2e block"})
        taxon (taxon.i/create! db {:name "Acer palmatum" :rank "species"})
        acc (acc.i/create! db {:code "E2E-ACC" :taxon-id (:taxon/id taxon)})
        mat (mat.i/create! db {:code "E2E-M1"
                               :accession-id (:accession/id acc)
                               :location-id (:location/id loc)
                               :type :plant
                               :status :alive
                               :quantity 1})]
    {:loc loc :taxon taxon :acc acc :mat mat}))

(deftest ^:e2e record-page-layout
  (testing "collapse toggles, footer pins, and panel scrollbars render"
    (server/with-server
      (fn [started]
        (let [base-url (server/server-url started)
              db (server/db started)
              email (mg/generate user.spec/email)
              password "TestPassword123!"]
          (user.i/create! db {:email email
                              :password password
                              :role :admin})
          (let [{:keys [mat]} (create-record-fixtures db)]
            (pw/with-browser
              (pw/navigate (str base-url "/login"))
              (pw/wait-for-selector "input[name=\"email\"]" 10000)
              (pw/fill "input[name=\"email\"]" email)
              (pw/fill "input[name=\"password\"]" password)
              (pw/click "button:has-text(\"Login\")")
              (pw/wait-for-url #"/activity" 60000)

              (pw/navigate (str base-url "/material/" (:material/id mat)
                                "/general/"))
              (pw/wait-for-selector ".spl-collapse-title")

              (testing "1. the collapse checkbox covers its header, and clicking toggles"
                ;; The old checkbox covered only the first 16px of the header,
                ;; so the click a user makes — at the header's centre — hit the
                ;; span and did nothing.
                (is (some? (pw/evaluate
                             "(() => { const t = document.querySelector('.spl-collapse-title'); const r = t.getBoundingClientRect(); const e = document.elementFromPoint(r.left + r.width / 2, r.top + r.height / 2); return e && e.type === 'checkbox' ? true : null; })()"))
                    "the header's centre should hit-test to the checkbox")
                (let [summary ".spl-collapse-title:has-text(\"Summary\") ~ .spl-collapse-content"
                      checkbox ".spl-collapse:has(.spl-collapse-title:has-text(\"Summary\")) > input[type=checkbox]"]
                  (pw/click-force checkbox)
                  (pw/wait-for-hidden summary)
                  (is (not (pw/visible? summary))
                      "a click should hide the section content")
                  (pw/click-force checkbox)
                  (pw/wait-for-selector summary)
                  (is (pw/visible? summary)
                      "a second click should reopen it")))

              (testing "2. the record-page footer stays inside the viewport"
                (pw/set-viewport-size 1280 500)
                (pw/wait-for-load-state :networkidle)
                (let [footer (pw/bounding-box ".spl-form-footer")]
                  (is (some? footer) "the action bar should be rendered")
                  (is (<= (+ (:y footer) (:height footer)) 500)
                      "the action bar's bottom edge should sit within the viewport"))
                (pw/set-viewport-size 1280 720))

              (testing "3. the panel declares a stable scrollbar gutter"
                (is (= "stable"
                       (pw/evaluate
                         "getComputedStyle(document.querySelector('.spl-detail-panel')).scrollbarGutter"))
                    "a styled scrollbar replaces the macOS overlay that hides itself")))))))))
