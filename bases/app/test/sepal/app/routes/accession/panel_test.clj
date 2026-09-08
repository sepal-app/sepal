(ns sepal.app.routes.accession.panel-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [integrant.core :as ig]
            [peridot.core :as peri]
            [sepal.accession.interface :as accession.i]
            [sepal.app.test :as app.test]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.taxon.interface :as taxon.i]
            [sepal.user.interface :as user.i])
  (:import [org.jsoup Jsoup]))

(use-fixtures :once default-system-fixture)

(defn- summary-value
  "The value rendered beside a Summary label, or nil when the row is absent.
  `summary-section` emits each pair as a dt/dd in one dl, so the value is the
  labelled dt's next sibling."
  [body label]
  (some (fn [dt]
          (when (= label (.text dt))
            (some-> (.nextElementSibling dt) (.text))))
        (.select body "dl.spl-kv dt.spl-k")))

(deftest test-the-panel-shows-the-receipt-fields
  (tf/testing "GET /accession/:id/ renders both receipt rows"
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :reader}
     [::taxon.i/factory :key/taxon] {:db *db*}
     [::accession.i/factory :key/accession]
     {:db *db*
      :taxon (ig/ref :key/taxon)
      ;; quantity 0 on purpose: an accession recorded with nothing received is
      ;; a real state, and `summary-section` drops a nil value -- so this also
      ;; proves 0 is not being treated as absent.
      :data {:received-type :bare_root_plant :quantity-received 0}}}
    (fn [{:keys [user accession]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response]} (peri/request sess (str "/accession/" (:accession/id accession) "/"))
            body (Jsoup/parse ^String (:body response))]
        (is (= 200 (:status response)))
        (is (= "Bare root plant" (summary-value body "Received as"))
            "underscores render as spaces -- `format-provenance-type` would give Bare_root_plant")
        (is (= "0" (summary-value body "Quantity received"))
            "zero renders; it is truthy in Clojure, so summary-section keeps it")))))

(deftest test-the-panel-omits-the-receipt-rows-when-unset
  (tf/testing "GET /accession/:id/ for an accession with neither field"
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :reader}
     [::taxon.i/factory :key/taxon] {:db *db*}
     [::accession.i/factory :key/accession]
     {:db *db*
      :taxon (ig/ref :key/taxon)
      :data {:received-type nil :quantity-received nil}}}
    (fn [{:keys [user accession]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response]} (peri/request sess (str "/accession/" (:accession/id accession) "/"))
            body (Jsoup/parse ^String (:body response))]
        (is (= 200 (:status response)))
        (is (nil? (summary-value body "Received as")))
        (is (nil? (summary-value body "Quantity received")))))))
