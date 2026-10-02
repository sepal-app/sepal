(ns sepal.app.e2e.record-page-test
  "E2E coverage for the collapsible sections, the pinned record-page footer, and
  the visible panel scrollbars."
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [sepal.accession.interface :as acc.i]
            [sepal.app.e2e.playwright :as pw]
            [sepal.app.e2e.server :as server]
            [sepal.app.test.email :as test.email]
            [sepal.location.interface :as loc.i]
            [sepal.material.interface :as mat.i]
            [sepal.media.interface :as media.i]
            [sepal.observation.interface :as observation.i]
            [sepal.taxon.interface :as taxon.i]
            [sepal.user.interface :as user.i]))

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
              email (test.email/unique)
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

(defn- login [base-url email password]
  (pw/navigate (str base-url "/login"))
  (pw/wait-for-selector "input[name=\"email\"]" 10000)
  (pw/fill "input[name=\"email\"]" email)
  (pw/fill "input[name=\"password\"]" password)
  (pw/click "button:has-text(\"Login\")")
  (pw/wait-for-url #"/activity" 60000))

(def ^:private list-pane
  "The element the observations list scrolls in."
  "document.querySelector('.spl-record-body')")

(deftest ^:e2e observation-writes-morph-the-page-in-place
  (testing "a save keeps scroll and focus, closes the edit, and updates the panel"
    (server/with-server
      (fn [started]
        (let [base-url (server/server-url started)
              db (server/db started)
              email (test.email/unique)
              password "TestPassword123!"
              user (user.i/create! db {:email email
                                       :password password
                                       :role :admin})
              {:keys [mat]} (create-record-fixtures db)
              ;; Note 30 is the newest, so it is first in the list and Note 1
              ;; is last.
              observations (into {}
                                 (for [n (range 1 31)]
                                   [n (observation.i/create!
                                        db {:resource-type :material
                                            :resource-id (:material/id mat)
                                            :type "general"
                                            :observed-on (format "2026-03-%02d" n)
                                            :note (str "Note " n)
                                            :created-by (:user/id user)})]))
              item #(str "[data-observation-id=\"" (:observation/id (observations %)) "\"]")]
          (pw/with-browser
            (login base-url email password)
            (pw/navigate (str base-url "/material/" (:material/id mat) "/observations/"))
            (pw/wait-for-selector (item 1) 10000)
            (pw/evaluate "window.confirm = () => true")
            (pw/evaluate (str list-pane ".scrollTop = " list-pane ".scrollHeight"))
            (let [before (pw/evaluate (str list-pane ".scrollTop"))]
              (is (pos? before) "the list is long enough to scroll")

              (pw/click (str (item 1) " button[aria-label=\"Edit observation\"]"))
              (pw/fill (str "#note-" (:observation/id (observations 1))) "Edited note")
              (pw/wait-for-enabled (str (item 1) " form button[type=submit]"))
              ;; Focus a control the save leaves alone. A click on Save would
              ;; put focus on the button, which the closing form hides, and
              ;; the browser then moves focus to the body.
              (pw/evaluate (str "document.querySelector('" (item 2) " button[aria-label=\"Edit observation\"]').focus({preventScroll: true})"))
              (pw/evaluate (str "document.querySelector('" (item 1) " form').requestSubmit()"))
              (pw/wait-for-selector (str (item 1) " .spl-note-body:has-text(\"Edited note\")") 10000)

              (testing "1. the pane keeps its scroll position"
                (is (<= (Math/abs (- before (pw/evaluate (str list-pane ".scrollTop")))) 50)))

              (testing "2. focus on a control the save leaves alone survives the morph"
                (is (true? (pw/evaluate "document.activeElement !== document.body")))
                (is (true? (pw/evaluate (str "document.activeElement === document.querySelector('" (item 2) " button[aria-label=\"Edit observation\"]')")))))

              (testing "3. the edited item's inline form is closed"
                (pw/wait-for-hidden (str (item 1) " form") 10000)
                (is (not (pw/visible? (str (item 1) " form"))))))

            (testing "4. deleting the first observation leaves the second"
              (pw/evaluate (str list-pane ".scrollTop = 0"))
              (let [{first-note :observation/note} (observations 30)
                    first-id (:observation/id (observations 30))
                    row (str "[data-observation-id=\"" first-id "\"]")]
                (pw/click (str row " button[aria-label=\"Delete observation\"]"))
                (pw/wait-for-hidden row 10000)
                (is (not (str/includes? (pw/evaluate "document.querySelector('#observations-list').innerText")
                                        first-note)))
                (is (str/includes? (pw/evaluate "document.querySelector('#observations-list').innerText")
                                   "Note 29"))))

            (testing "5. a new observation reaches the panel, and the form clears"
              (pw/fill "#observation-form textarea[name=note]" "Panel check")
              (pw/wait-for-enabled "#observation-form button[type=submit]")
              (pw/click "#observation-form button[type=submit]")
              (pw/wait-for-attached "#detail-panel-content :text(\"Panel check\")" 10000)
              (is (= "" (pw/evaluate "document.querySelector('#observation-form textarea[name=note]').value"))
                  "the form resets itself on form-saved")
              (is (true? (pw/evaluate "document.querySelector('#observation-form button[type=submit]').disabled"))
                  "and its button disables again"))))))))

(deftest ^:e2e media-link-editor-closes-after-a-save
  (testing "linking shows the chip and hides the form"
    (server/with-server
      (fn [started]
        (let [base-url (server/server-url started)
              db (server/db started)
              email (test.email/unique)
              password "TestPassword123!"
              user (user.i/create! db {:email email
                                       :password password
                                       :role :admin})
              {:keys [acc]} (create-record-fixtures db)
              media (media.i/create! db {:s3-bucket "b" :s3-key "media/e2e.jpg"
                                         :size-in-bytes 1 :media-type "image/jpeg"
                                         :title "e2e.jpg"
                                         :created-by (:user/id user)})
              root "#media-link-root"]
          (pw/with-browser
            (login base-url email password)
            (pw/navigate (str base-url "/media/" (:media/id media) "/"))
            (pw/wait-for-selector (str root " button:has-text(\"Link\")") 10000)
            (pw/click (str root " button:has-text(\"Link\")"))
            (pw/select-option "#resource-type" "accession")
            (pw/click "#resource-id-input")
            (pw/fill "#resource-id-input" (:accession/code acc))
            (pw/wait-for-attached (str "#resource-id-listbox [role=option]:has-text(\""
                                       (:accession/code acc) "\")"))
            (pw/press "ArrowDown")
            (pw/press "Enter")
            (pw/click (str root " form button[type=submit]"))
            (pw/wait-for-selector (str root " .spl-chip:has-text(\"" (:accession/code acc) "\")") 10000)
            (is (pw/visible? (str root " .spl-chip")) "the chip shows the accession")
            (pw/wait-for-hidden (str root " form") 10000)
            (is (not (pw/visible? (str root " form"))) "the edit form is hidden")))))))
