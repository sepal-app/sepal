(ns sepal.app.e2e.bulk-actions-test
  "E2E coverage for selecting list rows and acting on them."
  (:require [clojure.test :refer [deftest is testing]]
            [sepal.accession.interface :as acc.i]
            [sepal.app.e2e.playwright :as pw]
            [sepal.app.e2e.server :as server]
            [sepal.app.test.email :as test.email]
            [sepal.location.interface :as location.i]
            [sepal.material.interface :as material.i]
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
