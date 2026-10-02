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
            [sepal.propagation.interface :as propagation.i]
            [sepal.tag.interface :as tag.i]
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

(defn- observation-page
  "Logs in and opens the observations tab of a material with three
  observations on one day, so the list's rows are siblings with nothing
  between them. Returns the rows' ids in the order the list shows them."
  [base-url db]
  (let [email (test.email/unique)
        password "TestPassword123!"
        user (user.i/create! db {:email email
                                 :password password
                                 :role :admin})
        {:keys [mat]} (create-record-fixtures db)]
    (doseq [note ["Alpha" "Beta" "Gamma"]]
      (observation.i/create! db {:resource-type :material
                                 :resource-id (:material/id mat)
                                 :type "general"
                                 :observed-on "2026-03-01"
                                 :note note
                                 :created-by (:user/id user)}))
    (login base-url email password)
    (pw/navigate (str base-url "/material/" (:material/id mat) "/observations/"))
    (pw/wait-for-selector "[data-observation-id]" 10000)
    (pw/evaluate "window.confirm = () => true")
    (pw/evaluate (str "Array.from(document.querySelectorAll('[data-observation-id]'))"
                      ".map(e => e.dataset.observationId)"))))

(defn- observation-row [id]
  (str "[data-observation-id=\"" id "\"]"))

(defn- open-editors
  "The ids of the observations whose inline edit form is showing."
  []
  (pw/evaluate (str "Array.from(document.querySelectorAll('[data-observation-id]'))"
                    ".filter(e => e.querySelector('form').checkVisibility())"
                    ".map(e => e.dataset.observationId)")))

(deftest ^:e2e an-open-editor-stays-on-its-row-when-another-is-deleted
  (testing "deleting the row above an open editor leaves that editor open on its row"
    (server/with-server
      (fn [started]
        (pw/with-browser
          (let [[first-id second-id third-id] (observation-page (server/server-url started)
                                                                (server/db started))
                second-row (observation-row second-id)]
            (pw/click (str second-row " button[aria-label=\"Edit observation\"]"))
            (pw/fill (str "#note-" second-id) "Unsaved edit")
            (pw/click (str (observation-row first-id) " button[aria-label=\"Delete observation\"]"))
            (pw/wait-for-hidden (observation-row first-id) 10000)
            (is (= [second-id] (open-editors))
                "the open editor is the second row's, and no other row's opened")
            (is (= "Unsaved edit" (pw/evaluate (str "document.querySelector('#note-" second-id "').value")))
                "and it still holds what was typed")
            (is (= [second-id third-id]
                   (pw/evaluate (str "Array.from(document.querySelectorAll('[data-observation-id]'))"
                                     ".map(e => e.dataset.observationId)")))
                "the remaining rows are the two that were not deleted")))))))

(deftest ^:e2e a-second-inline-edit-after-a-save
  (testing "after one row's inline edit saves, another row's editor opens and saves"
    (server/with-server
      (fn [started]
        (pw/with-browser
          (let [db (server/db started)
                [first-id second-id] (observation-page (server/server-url started) db)
                edit (fn [id text]
                       (let [row (observation-row id)]
                         (pw/click (str row " button[aria-label=\"Edit observation\"]"))
                         (pw/fill (str "#note-" id) text)
                         (pw/wait-for-enabled (str row " form button[type=submit]"))
                         (pw/click (str row " form button[type=submit]"))
                         (pw/wait-for-selector (str row " .spl-note-body:has-text(\"" text "\")") 10000)
                         (pw/wait-for-hidden (str row " form") 10000)))]
            (edit first-id "First saved")
            (is (= [] (open-editors)) "the first save closes its editor")
            (edit second-id "Second saved")
            (is (= [] (open-editors)) "the second save closes its editor")
            (is (= "Second saved"
                   (:observation/note (observation.i/get-by-id db (parse-long second-id))))
                "the second edit landed")))))))

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

(deftest ^:e2e media-link-picker-shows-the-saved-link
  (testing "Change link after a save is prefilled with the link just saved"
    (server/with-server
      (fn [started]
        (let [base-url (server/server-url started)
              db (server/db started)
              email (test.email/unique)
              password "TestPassword123!"
              user (user.i/create! db {:email email
                                       :password password
                                       :role :admin})
              {:keys [acc taxon]} (create-record-fixtures db)
              other (acc.i/create! db {:code "E2E-ACC2" :taxon-id (:taxon/id taxon)})
              media (media.i/create! db {:s3-bucket "b" :s3-key "media/e2e.jpg"
                                         :size-in-bytes 1 :media-type "image/jpeg"
                                         :title "e2e.jpg"
                                         :created-by (:user/id user)})
              root "#media-link-root"
              picker "#resource-id-input"]
          (media.i/link! db (:media/id media) (:accession/id acc) "accession")
          (pw/with-browser
            (login base-url email password)
            (pw/navigate (str base-url "/media/" (:media/id media) "/"))
            (pw/wait-for-selector (str root " .spl-chip:has-text(\"E2E-ACC\")") 10000)
            (pw/click (str root " button[aria-label=\"Change link\"]"))
            (pw/click picker)
            (pw/fill picker (:accession/code other))
            (pw/wait-for-attached (str "#resource-id-listbox [role=option]:has-text(\""
                                       (:accession/code other) "\")"))
            (pw/press "ArrowDown")
            (pw/press "Enter")
            (pw/click (str root " form button[type=submit]"))
            (pw/wait-for-selector (str root " .spl-chip:has-text(\"E2E-ACC2\")") 10000)
            (pw/click (str root " button[aria-label=\"Change link\"]"))
            (pw/wait-for-selector picker 10000)
            (is (str/includes? (pw/evaluate (str "document.querySelector('" picker "').value"))
                               "E2E-ACC2")
                "the picker holds the link just saved")))))))

(deftest ^:e2e media-link-picker-is-empty-after-unlinking
  (testing "Link after removing the link does not offer the removed one"
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
              root "#media-link-root"
              picker "#resource-id-input"]
          (media.i/link! db (:media/id media) (:accession/id acc) "accession")
          (pw/with-browser
            (login base-url email password)
            (pw/navigate (str base-url "/media/" (:media/id media) "/"))
            (pw/wait-for-selector (str root " .spl-chip:has-text(\"E2E-ACC\")") 10000)
            (pw/evaluate "window.confirm = () => true")
            (pw/click (str root " button[aria-label=\"Remove link\"]"))
            (pw/wait-for-selector (str root " button:has-text(\"Link\")") 10000)
            (pw/click (str root " button:has-text(\"Link\")"))
            (pw/select-option "#resource-type" "accession")
            (pw/wait-for-selector picker 10000)
            (is (= "" (pw/evaluate (str "document.querySelector('" picker "').value")))
                "the picker does not hold the removed link")))))))

(defn- pick
  "Choose the one option matching `text` in the <sepal-combobox> named
  `field`."
  [field text]
  (let [input (str "#" field "-input")]
    (pw/click input)
    (pw/fill input text)
    (pw/wait-for-attached (str "#" field "-listbox [role=option]:has-text(\"" text "\")"))
    (pw/press "ArrowDown")
    (pw/press "Enter")))

(deftest ^:e2e accession-taxon-saves-twice
  (testing "A second taxon picked after a save is saved too"
    (server/with-server
      (fn [started]
        (let [base-url (server/server-url started)
              db (server/db started)
              email (test.email/unique)
              password "TestPassword123!"
              _ (user.i/create! db {:email email
                                    :password password
                                    :role :admin})
              {:keys [acc]} (create-record-fixtures db)
              header ".spl-record-name"]
          (taxon.i/create! db {:name "Quercus robur" :rank "species"})
          (taxon.i/create! db {:name "Betula pendula" :rank "species"})
          (pw/with-browser
            (login base-url email password)
            (pw/navigate (str base-url "/accession/" (:accession/id acc) "/general/"))
            (pw/wait-for-selector (str header ":has-text(\"Acer palmatum\")") 10000)
            (pick "taxon-id" "Quercus robur")
            (pw/click "button:has-text(\"Save\")")
            (pw/wait-for-selector (str header ":has-text(\"Quercus robur\")") 10000)
            (pick "taxon-id" "Betula pendula")
            (pw/click "button:has-text(\"Save\")")
            (pw/wait-for-selector (str header ":has-text(\"Betula pendula\")") 10000)
            (is (str/includes? (pw/text-content header) "Betula pendula")
                "the header names the taxon saved second")
            (let [saved (acc.i/get-by-id db (:accession/id acc))]
              (is (= "Betula pendula"
                     (:taxon/name (taxon.i/get-by-id db (:accession/taxon-id saved))))
                  "the accession holds it"))))))))

(def ^:private vernacular-rows
  "How many vernacular name rows the taxon form shows, read a frame after
  whatever was just clicked, so Alpine has rendered it."
  (str "new Promise(r => requestAnimationFrame(() => r("
       "document.querySelectorAll('[data-section=vernacular-names] "
       "input[name=vernacular-name-name]').length)))"))

(deftest ^:e2e taxon-vernacular-rows-after-a-save
  (testing "A row added after a save can be deleted"
    (server/with-server
      (fn [started]
        (let [base-url (server/server-url started)
              db (server/db started)
              email (test.email/unique)
              password "TestPassword123!"
              _ (user.i/create! db {:email email
                                    :password password
                                    :role :admin})
              {:keys [taxon]} (create-record-fixtures db)
              section "[data-section=vernacular-names]"]
          (pw/with-browser
            (login base-url email password)
            (pw/navigate (str base-url "/taxon/" (:taxon/id taxon) "/name/"))
            (pw/wait-for-selector (str section " input[name=vernacular-name-name]") 10000)
            (pw/fill (str section " input[name=vernacular-name-name]") "Japanese maple")
            (pw/click "button:has-text(\"Save\")")
            ;; The fieldset's x-data carries the saved names, so it changes.
            (pw/wait-for-attached (str section "[x-data*=\"Japanese maple\"]") 10000)
            (is (= 1 (pw/evaluate vernacular-rows)) "the saved name is one row")
            (pw/click (str section " button[aria-label=\"Add vernacular name\"]"))
            (is (= 2 (pw/evaluate vernacular-rows)) "Add gives a second row")
            (pw/evaluate (str "document.querySelectorAll('" section
                              " button[aria-label=Delete]')[1].click()"))
            (is (= 1 (pw/evaluate vernacular-rows)) "Delete removes it")))))))

(deftest ^:e2e propagation-form-after-its-method-changes
  (testing "The form still reacts and saves after a save that changed the method"
    (server/with-server
      (fn [started]
        (let [base-url (server/server-url started)
              db (server/db started)
              email (test.email/unique)
              password "TestPassword123!"
              _ (user.i/create! db {:email email
                                    :password password
                                    :role :admin})
              {:keys [acc]} (create-record-fixtures db)
              prop (propagation.i/create! db {:type :seed
                                              :parent-accession-id (:accession/id acc)})
              id (:propagation/id prop)
              rootstock "#rootstock-taxon-id"]
          (pw/with-browser
            (login base-url email password)
            (pw/navigate (str base-url "/propagation/" id "/"))
            (pw/wait-for-selector "select#type" 10000)
            (pw/select-option "select#type" "graft")
            (pw/wait-for-selector rootstock 10000)
            (pw/click "button:has-text(\"Save\")")
            ;; The method is in the wrapper's x-data, so the save changes it.
            (pw/wait-for-attached "[x-data*=\"graft\"]" 10000)
            (pw/select-option "select#type" "cutting")
            (pw/wait-for-hidden rootstock 10000)
            (is (not (pw/visible? rootstock)) "the rootstock hides for a cutting")
            (pw/click "button:has-text(\"Save\")")
            (pw/wait-for-attached "[x-data*=\"cutting\"]" 10000)
            (is (= :cutting (:propagation/type (propagation.i/get-by-id db id)))
                "the second save landed")))))))

(deftest ^:e2e propagation-parent-plant-follows-a-new-parent
  (testing "Choosing another parent accession on an edit offers its plants"
    (server/with-server
      (fn [started]
        (let [base-url (server/server-url started)
              db (server/db started)
              email (test.email/unique)
              password "TestPassword123!"
              _ (user.i/create! db {:email email
                                    :password password
                                    :role :admin})
              {:keys [acc loc taxon]} (create-record-fixtures db)
              acc2 (acc.i/create! db {:code "E2E-ACC2" :taxon-id (:taxon/id taxon)})
              mat2 (mat.i/create! db {:code "E2E-M2"
                                      :accession-id (:accession/id acc2)
                                      :location-id (:location/id loc)
                                      :type :plant
                                      :status :alive
                                      :quantity 1})
              prop (propagation.i/create! db {:type :seed
                                              :parent-accession-id (:accession/id acc)})
              id (:propagation/id prop)]
          (pw/with-browser
            (login base-url email password)
            (pw/navigate (str base-url "/propagation/" id "/"))
            (pw/wait-for-selector "#parent-material-id-input" 10000)
            (pick "parent-accession-id" "E2E-ACC2")
            ;; The plant picker is fetched for the new accession and swapped
            ;; in, so it only offers E2E-M2 once that request has landed.
            (pick "parent-material-id" "E2E-M2")
            (pw/click "button:has-text(\"Save\")")
            (pw/wait-for-selector ".spl-panel-code:has-text(\"E2E-M2\")" 10000)
            (is (str/includes? (pw/text-content ".spl-panel-code") "E2E-ACC2")
                "the panel names the new parent")
            (is (= (:material/id mat2)
                   (:propagation/parent-material-id (propagation.i/get-by-id db id)))
                "the propagation holds the plant")))))))

(deftest ^:e2e location-rename-updates-the-page-in-place
  (testing "the header, breadcrumb and title show the new name with no navigation"
    (server/with-server
      (fn [started]
        (let [base-url (server/server-url started)
              db (server/db started)
              email (test.email/unique)
              password "TestPassword123!"
              _ (user.i/create! db {:email email
                                    :password password
                                    :role :admin})
              {:keys [loc]} (create-record-fixtures db)]
          (pw/with-browser
            (login base-url email password)
            (pw/navigate (str base-url "/location/" (:location/id loc) "/general/"))
            (pw/wait-for-selector ".spl-record-name:has-text(\"E2e block\")" 10000)
            ;; A navigation builds a new document, which drops this.
            (pw/evaluate "window.sameDocument = true")
            (pw/fill "input[name=\"name\"]" "Renamed block")
            (pw/click "button:has-text(\"Save\")")
            (pw/wait-for-selector ".spl-record-name:has-text(\"Renamed block\")" 10000)
            (is (str/includes? (pw/text-content ".spl-crumbs-current") "Renamed block")
                "the breadcrumb shows the new name")
            (is (str/includes? (pw/evaluate "document.title") "Renamed block")
                "the document title shows the new name")
            (is (= "Renamed block" (:location/name (loc.i/get-by-id db (:location/id loc))))
                "the location holds it")
            (is (true? (pw/evaluate "window.sameDocument === true"))
                "the page was not reloaded")
            (is (= 1 (pw/evaluate "performance.getEntriesByType('navigation').length"))
                "there was one navigation")))))))

(deftest ^:e2e tag-removal-leaves-the-other-chip
  (testing "removing one of two tags keeps the other"
    (server/with-server
      (fn [started]
        (let [base-url (server/server-url started)
              db (server/db started)
              email (test.email/unique)
              password "TestPassword123!"
              _ (user.i/create! db {:email email
                                    :password password
                                    :role :admin})
              {:keys [mat]} (create-record-fixtures db)]
          (doseq [name ["alpha" "beta"]]
            (tag.i/tag! db (:tag/id (tag.i/create! db {:name name}))
                        (:material/id mat) :material))
          (pw/with-browser
            (login base-url email password)
            (pw/navigate (str base-url "/material/" (:material/id mat) "/tags/"))
            (pw/wait-for-selector ".spl-chip:has-text(\"alpha\")" 10000)
            (pw/evaluate "window.confirm = () => true")
            (pw/click "button[aria-label=\"Remove tag alpha\"]")
            (pw/wait-for-hidden ".spl-chip:has-text(\"alpha\")" 10000)
            (is (not (pw/visible? ".spl-chip:has-text(\"alpha\")")) "alpha is gone")
            (is (pw/visible? ".spl-chip:has-text(\"beta\")") "beta is still there")
            (is (= ["beta"] (mapv :tag/name (tag.i/get-for-resource db :material (:material/id mat))))
                "and is the only tag the material holds")))))))
