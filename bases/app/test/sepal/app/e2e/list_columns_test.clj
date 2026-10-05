(ns sepal.app.e2e.list-columns-test
  "E2E coverage for the column picker and header sorting."
  (:require [clojure.test :refer [deftest is testing]]
            [sepal.accession.interface :as acc.i]
            [sepal.app.e2e.playwright :as pw]
            [sepal.app.e2e.server :as server]
            [sepal.app.test.email :as test.email]
            [sepal.taxon.interface :as taxon.i]
            [sepal.user.interface :as user.i]))

(deftest ^:e2e list-columns
  (server/with-server
    (fn [started]
      (let [base-url (server/server-url started)
            db (server/db started)
            email (test.email/unique)
            password "TestPassword123!"]
        (user.i/create! db {:email email :password password :role :reader})
        (let [taxon (taxon.i/create! db {:name "Quercus alba" :rank "species"})]
          ;; More than a page, so infinite scroll has something to append.
          (doseq [i (range 30)]
            (acc.i/create! db {:code (format "E2E.%04d" i)
                               :taxon-id (:taxon/id taxon)
                               :date-received (format "2020-01-%02d" (inc (mod i 28)))})))
        (pw/with-browser
          (pw/navigate (str base-url "/login"))
          (pw/wait-for-selector "input[name=\"email\"]" 10000)
          (pw/fill "input[name=\"email\"]" email)
          (pw/fill "input[name=\"password\"]" password)
          (pw/click "button:has-text(\"Login\")")
          (pw/wait-for-url #"/activity" 60000)
          (pw/navigate (str base-url "/accession/"))
          (pw/wait-for-selector "table.spl-table")

          (testing "show an extra column; appended rows have it too"
            (pw/click ".spl-col-picker-btn")
            (pw/wait-for-selector ".spl-col-picker:popover-open")
            (pw/click "input[name=shown][value=accessioned]")
            (pw/click ".spl-col-picker button:has-text(\"Apply\")")
            (pw/wait-for-selector "th:has-text(\"Accessioned\")")
            (pw/evaluate "document.querySelector('.spl-table-scroll').scrollTo(0, 1e6)")
            (pw/wait-for-load-state :networkidle)
            (is (= (pw/evaluate "document.querySelectorAll('thead th').length")
                   (pw/evaluate "document.querySelector('#table-rows tr:last-of-type').previousElementSibling.children.length"))))

          (testing "a sort holds while the search is refined"
            (pw/click "th a:has-text(\"Received\")")
            (pw/wait-for-url #"sort=received" 10000)
            (pw/fill "input[name=q]" "E2E")
            (pw/wait-for-url #"q=E2E(&|$)" 10000)
            (pw/wait-for-load-state :networkidle)
            (pw/fill "input[name=q]" "E2E.")
            (pw/wait-for-url #"q=E2E\.(&|$)" 10000)
            (pw/wait-for-load-state :networkidle)
            (is (re-find #"sort=received" (pw/evaluate "location.href")))
            (pw/fill "input[name=q]" "")
            (pw/wait-for-load-state :networkidle)
            (pw/fill "input[name=q]" "Quercus")
            (pw/wait-for-url #"q=Quercus(&|$)" 10000)
            (pw/wait-for-load-state :networkidle)
            (is (not (re-find #"sort=" (pw/evaluate "location.href")))))

          (testing "Escape closes the picker"
            (pw/click ".spl-col-picker-btn")
            (pw/wait-for-selector ".spl-col-picker:popover-open")
            (pw/press "Escape")
            (is (not (pw/evaluate "!!document.querySelector('.spl-col-picker:popover-open')"))))

          (testing "hiding the sorted column returns to the default order"
            (pw/click "th a:has-text(\"Received\")")
            (pw/wait-for-url #"sort=received" 10000)
            (pw/click ".spl-col-picker-btn")
            (pw/click "input[name=shown][value=received]")
            (pw/click ".spl-col-picker button:has-text(\"Apply\")")
            (pw/wait-for-hidden "th:has-text(\"Received\")" 10000)
            (pw/wait-for-load-state :networkidle)
            (is (not (re-find #"sort=" (pw/evaluate "location.href"))))))))))
