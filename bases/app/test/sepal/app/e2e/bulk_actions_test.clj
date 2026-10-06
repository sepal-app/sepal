(ns sepal.app.e2e.bulk-actions-test
  "E2E coverage for selecting list rows and acting on them."
  (:require [clojure.test :refer [deftest is testing]]
            [sepal.accession.interface :as acc.i]
            [sepal.app.e2e.playwright :as pw]
            [sepal.app.e2e.server :as server]
            [sepal.app.test.email :as test.email]
            [sepal.location.interface :as location.i]
            [sepal.material.interface :as material.i]
            [sepal.tag.interface :as tag.i]
            [sepal.taxon.interface :as taxon.i]
            [sepal.user.interface :as user.i]))

(defn- login! [base-url email password]
  (pw/navigate (str base-url "/login"))
  (pw/wait-for-selector "input[name=\"email\"]" 10000)
  (pw/fill "input[name=\"email\"]" email)
  (pw/fill "input[name=\"password\"]" password)
  (pw/click "button:has-text(\"Login\")")
  (pw/wait-for-url #"/activity" 60000))

(defn- row-box
  "The checkbox hit area of the nth data row, from 1. The paging rows between
  data rows have no checkbox, so tr:nth-child would miscount."
  [n]
  (str ":nth-match(td.spl-col--select label, " n ")"))

(defn- row-id
  "The material id in the nth data row, from 1."
  [n]
  (parse-long (pw/evaluate (str "document.querySelectorAll('input[data-select-row]')[" (dec n) "].value"))))

(defn- pick-location!
  "Choose `code`, which matches one location, in the Move dialog's location
  picker with the mouse, after checking the option is on top at its centre
  and so not clipped by the dialog."
  [code]
  (let [option (str "#location-id-listbox [role=option]:has-text(\"" code "\")")]
    (pw/fill "#location-id-input" code)
    ;; Until the list narrows, `code` may be an option further down a longer one.
    (pw/wait-for-hidden "#location-id-listbox [role=option] >> nth=1" 5000)
    (pw/wait-for-selector option 5000)
    (let [{:keys [x y width height]} (pw/bounding-box option)
          hit (pw/evaluate (format "document.elementFromPoint(%s, %s)?.closest('[role=option]')?.textContent ?? ''"
                                   (+ x (/ width 2)) (+ y (/ height 2))))]
      (is (re-find (re-pattern code) hit) (str "the " code " option is visible where it is drawn")))
    (pw/click option)
    (is (= code (pw/evaluate "document.getElementById('location-id-input').value.split(' ')[0]")))))

(defn- checked-count []
  (pw/evaluate "document.querySelectorAll('input[data-select-row]:checked').length"))

(defn- scroll-to-end! []
  (pw/evaluate "document.querySelector('.spl-table-scroll').scrollTo(0, 1e6)")
  ;; The end marker arrives with the second, and last, page.
  (pw/wait-for-selector "#table-rows tr.spl-end" 10000)
  (pw/evaluate "document.querySelector('.spl-table-scroll').scrollTo(0, 1e6)"))

(deftest ^:e2e bulk-actions
  (server/with-server
    (fn [started]
      (let [base-url (server/server-url started)
            db (server/db started)
            email (test.email/unique)
            password "TestPassword123!"
            taxon (taxon.i/create! db {:name "Quercus alba" :rank "species"})
            bed (location.i/create! db {:code "BED4" :name "Bed 4"})
            bed5 (location.i/create! db {:code "BED5" :name "Bed 5"})
            gone (location.i/create! db {:code "GONE" :name "Gone"})
            ;; Enough beds to fill the picker's list to its full height.
            _ (doseq [i (range 10 20)]
                (location.i/create! db {:code (str "BED" i) :name (str "Bed " i)}))
            ;; More than a page, so infinite scroll has something to append.
            material-ids (vec (for [i (range 30)]
                                (let [acc (acc.i/create! db {:code (format "E2E.%04d" i)
                                                             :taxon-id (:taxon/id taxon)})]
                                  (:material/id (material.i/create! db {:code "1"
                                                                        :accession-id (:accession/id acc)
                                                                        :location-id (:location/id bed)
                                                                        :quantity 2
                                                                        :status :alive
                                                                        :type :plant})))))
            dead? #(= :dead (:material/status (material.i/get-by-id db %)))]
        (user.i/create! db {:email email :password password :role :editor})
        (pw/with-browser
          (login! base-url email password)
          (pw/navigate (str base-url "/material/"))
          (pw/wait-for-selector "table.spl-table input[data-select-row]")

          (testing "ticking shows the bar; scrolling keeps the selection"
            (pw/click (row-box 1))
            (pw/click (row-box 2))
            (pw/wait-for-selector ".spl-bulk-bar:has-text(\"2 selected\")")
            (is (not (pw/visible? "#list-toolbar")))
            (is (not (pw/visible? ".spl-panel")) "the checkbox didn't open the panel")
            (scroll-to-end!)
            (is (= 2 (checked-count)))
            (pw/click (row-box 30))
            (pw/wait-for-selector ".spl-bulk-bar:has-text(\"3 selected\")"))

          (testing "the header box is mixed, then ticks every loaded row"
            (is (true? (pw/evaluate "document.querySelector('thead .spl-col--select input').indeterminate")))
            (pw/click "thead .spl-col--select input")
            (pw/wait-for-selector ".spl-bulk-bar:has-text(\"30 selected\")")
            (is (= 30 (checked-count))))

          (testing "Clear brings the toolbar back"
            (pw/click ".spl-bulk-bar button:has-text(\"Clear\")")
            (pw/wait-for-selector "#list-toolbar" 5000)
            (is (zero? (checked-count))))

          (testing "a sort clears the selection"
            (pw/click (row-box 1))
            (pw/wait-for-selector ".spl-bulk-bar:has-text(\"1 selected\")")
            (pw/click "th:not(.spl-col--picker) a.spl-th-sort >> nth=0")
            (pw/wait-for-url #"sort=" 10000)
            (pw/wait-for-load-state :networkidle)
            (pw/wait-for-selector "#list-toolbar" 5000)
            (is (zero? (checked-count))))

          (testing "clicking the row still opens the panel"
            (pw/type-text "#q" "E2E.000")
            (pw/wait-for-url #"q=E2E\.000" 10000)
            (pw/wait-for-load-state :networkidle)
            (pw/click "#table-rows tr:has(input[data-select-row]) >> nth=0 >> td:nth-child(3)")
            (pw/wait-for-selector ".spl-panel" 5000))

          (testing "mark two materials dead"
            (pw/press "Escape")
            (pw/wait-for-hidden ".spl-panel" 5000)
            (pw/click (row-box 1))
            (pw/click (row-box 2))
            (pw/click ".spl-bulk-bar button:has-text(\"Change status\")")
            (pw/wait-for-selector "#bulk-material-status[open]" 5000)
            (pw/select-option "#status-bulk-status" "dead")
            (is (= "dead" (pw/evaluate "document.getElementById('reason-bulk-status').value")))
            (pw/click "#bulk-material-status button[type=submit]")
            (pw/wait-for-selector ".spl-banner:has-text(\"Changed 2 materials\")" 10000)
            (pw/wait-for-hidden ".spl-bulk-bar" 5000)
            (is (= 2 (count (filter dead? material-ids)))))

          (testing "after an action the dialog is reset and focus is on the search"
            (pw/wait-for-selector "#list-toolbar" 5000)
            (is (= "q" (pw/evaluate "document.activeElement.id")))
            (is (= "alive" (pw/evaluate "document.getElementById('status-bulk-status').value")))
            (is (= "" (pw/evaluate "document.getElementById('reason-bulk-status').value"))))

          (testing "a failure shows in the open dialog, and is gone when it reopens"
            (pw/wait-for-load-state :networkidle)
            (pw/click (row-box 3))
            (pw/click ".spl-bulk-bar button:has-text(\"Move\")")
            (pw/wait-for-selector "#bulk-material-move[open]" 5000)
            (pick-location! "GONE")
            (location.i/delete! db (:location/id gone))
            (pw/click "#bulk-material-move button[type=submit]")
            (pw/wait-for-selector "#bulk-material-move .spl-bulk-error:has-text(\"Choose a location.\")" 5000)
            (is (pw/visible? "#bulk-material-move .spl-bulk-error"))
            (is (= "alert" (pw/evaluate "document.querySelector('#bulk-material-move .spl-bulk-error').getAttribute('role')")))
            (is (pw/visible? "#bulk-material-move[open]") "the dialog stays open")
            (pw/click "#bulk-material-move button:has-text(\"Cancel\")")
            (pw/click ".spl-bulk-bar button:has-text(\"Move\")")
            (pw/wait-for-selector "#bulk-material-move[open]" 5000)
            (is (= "" (pw/evaluate "document.querySelector('#bulk-material-move .spl-bulk-error').textContent"))))

          (testing "the location list isn't clipped by the dialog"
            (pw/fill "#location-id-input" "BED")
            (pw/wait-for-selector "#location-id-listbox [role=option]:has-text(\"BED19\")" 5000)
            (let [{:keys [x y width height]} (pw/bounding-box "#location-id-listbox")]
              (is (true? (pw/evaluate (format "!!document.elementFromPoint(%s, %s)?.closest('#location-id-listbox')"
                                              (+ x (/ width 2)) (+ y height -6))))
                  "the bottom of the list is on top where it is drawn")))

          (testing "move one material"
            (let [id (row-id 3)]
              (pick-location! "BED5")
              (pw/click "#bulk-material-move button[type=submit]")
              (pw/wait-for-selector ".spl-banner:has-text(\"Moved 1 material\")" 10000)
              (pw/wait-for-hidden ".spl-bulk-bar" 5000)
              (is (= (:location/id bed5) (:material/location-id (material.i/get-by-id db id))))
              (is (= "" (pw/evaluate "document.getElementById('location-id-input').value"))
                  "the picker is empty for the next selection")))

          (testing "add a tag to two materials, then remove it"
            (pw/wait-for-load-state :networkidle)
            (let [ids [(row-id 1) (row-id 2)]]
              (pw/click (row-box 1))
              (pw/click (row-box 2))
              (pw/click ".spl-bulk-bar button:has-text(\"Add tag\")")
              (pw/wait-for-selector "#bulk-material-tag-add[open]" 5000)
              (pw/fill "#tag-name-bulk-tag-add" "E2E tag")
              (pw/click "#bulk-material-tag-add button[type=submit]")
              (pw/wait-for-selector ".spl-banner:has-text(\"Tagged 2 records\")" 10000)
              (pw/wait-for-hidden ".spl-bulk-bar" 5000)
              (is (= ["E2E tag"] (map :tag/name (tag.i/get-for-resources db :material ids))))

              (pw/wait-for-load-state :networkidle)
              (pw/click (row-box 1))
              (pw/click (row-box 2))
              (pw/click ".spl-bulk-bar button:has-text(\"Remove tag\")")
              (pw/wait-for-selector "#bulk-material-tag-remove[open]" 5000)
              (is (= ["E2E tag"]
                     (pw/evaluate "Array.from(document.querySelectorAll('#tag-id-bulk-tag-remove option'), o => o.textContent)")))
              (pw/click "#bulk-material-tag-remove button[type=submit]")
              (pw/wait-for-selector ".spl-banner:has-text(\"Removed the tag from 2 records\")" 10000)
              (pw/wait-for-hidden ".spl-bulk-bar" 5000)
              (is (empty? (tag.i/get-for-resources db :material ids)))))

          (testing "Remove tag on untagged rows says so and opens nothing"
            (pw/wait-for-load-state :networkidle)
            (pw/click (row-box 1))
            (pw/click ".spl-bulk-bar button:has-text(\"Remove tag\")")
            (pw/wait-for-selector ".spl-banner:has-text(\"None of the selected rows has a tag.\")" 5000)
            (is (not (pw/visible? "#bulk-material-tag-remove[open]")))
            (pw/click ".spl-bulk-bar button:has-text(\"Clear\")"))

          (testing "a dead material's form loads with quantity held at 0, and clean"
            (let [id (first (filter dead? material-ids))]
              (pw/navigate (str base-url "/material/" id "/general/"))
              (pw/wait-for-selector "#quantity")
              (is (= "0" (pw/evaluate "document.getElementById('quantity').value")))
              (is (true? (pw/evaluate "document.getElementById('quantity').readOnly")))
              (is (false? (pw/evaluate "Alpine.$data(document.getElementById('material-form')).dirty"))
                  "loading the form doesn't make it dirty")
              (is (false? (pw/evaluate "document.getElementById('quantity').classList.contains('spl-input--edited')")))))

          (testing "material form: dead holds quantity at 0, alive restores it"
            (let [id (last material-ids)]
              (pw/navigate (str base-url "/material/" id "/general/"))
              (pw/wait-for-selector "#quantity")
              (pw/fill "#quantity" "3")
              (pw/select-option "#status" "dead")
              (is (= "0" (pw/evaluate "document.getElementById('quantity').value")))
              (is (true? (pw/evaluate "document.getElementById('quantity').readOnly")))
              (pw/select-option "#status" "alive")
              (is (= "3" (pw/evaluate "document.getElementById('quantity').value")))
              (is (false? (pw/evaluate "document.getElementById('quantity').readOnly")))))

          (testing "phone: the bar is pinned to the bottom and the last row clears it"
            (pw/set-viewport-size 390 800)
            (pw/navigate (str base-url "/material/"))
            (pw/wait-for-selector "td.spl-col--select label")
            (pw/click (row-box 1))
            (pw/wait-for-selector ".spl-bulk-bar")
            (let [{:keys [y height]} (pw/bounding-box ".spl-bulk-bar")]
              (is (<= 790 (+ y height) 800)))
            ;; The End of list row also sits under the last data row, so check
            ;; the room reserved for the bar on its own.
            (let [{:keys [height]} (pw/bounding-box ".spl-bulk-bar")
                  reserved (pw/evaluate "parseFloat(getComputedStyle(document.querySelector('.spl-table-scroll')).paddingBottom)")]
              (is (<= height reserved)
                  (str "bar height " height " vs reserved " reserved)))
            (scroll-to-end!)
            (let [bar-top (:y (pw/bounding-box ".spl-bulk-bar"))
                  {:keys [y height]} (pw/bounding-box "#table-rows tr:has(input[data-select-row]) >> nth=-1")]
              (is (<= (+ y height) bar-top)
                  (str "last row bottom " (+ y height) " vs bar top " bar-top)))
            (pw/set-viewport-size 1280 800)))))))
