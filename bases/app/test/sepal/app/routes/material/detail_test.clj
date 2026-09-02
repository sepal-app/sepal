(ns sepal.app.routes.material.detail-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [integrant.core :as ig]
            [next.jdbc.sql :as jdbc.sql]
            [peridot.core :as peri]
            [sepal.accession.interface :as accession.i]
            [sepal.app.test :as app.test]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.location.interface :as location.i]
            [sepal.material.interface :as material.i]
            [sepal.taxon.interface :as taxon.i]
            [sepal.test.interface :as test.i]
            [sepal.user.interface :as user.i])
  (:import [org.jsoup Jsoup]))

(use-fixtures :once default-system-fixture)

(deftest test-update-material-validation-errors
  (tf/testing "POST with invalid data returns 422 with OOB error elements"
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :editor}
     [::taxon.i/factory :key/taxon] {:db *db*}
     [::accession.i/factory :key/accession] {:db *db* :taxon (ig/ref :key/taxon)}
     [::location.i/factory :key/location] {:db *db*}
     [::material.i/factory :key/material] {:db *db*
                                           :accession (ig/ref :key/accession)
                                           :location (ig/ref :key/location)}}
    (fn [{:keys [user material]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            detail-url (str "/material/" (:material/id material) "/general/")
            {:keys [response] :as sess} (-> sess
                                            (peri/request detail-url))
            token (test.i/response-anti-forgery-token response)
            {:keys [response]} (-> sess
                                   (peri/request detail-url
                                                 :request-method :post
                                                 :params {:__anti-forgery-token token
                                                          :code ""}))]
        (is (= 422 (:status response))
            (str "Expected 422, got " (:status response) " with body: " (:body response)))

        (is (= "text/html" (get-in response [:headers "Content-Type"]))
            "Should return text/html content type for HTMX OOB swap")

        (let [body (Jsoup/parse ^String (:body response))]
          (let [oob-elements (.select body "[hx-swap-oob]")]
            (is (pos? (.size oob-elements))
                "Should have elements with hx-swap-oob attribute"))

          (is (some? (.selectFirst body "#code-errors"))
              "Should have error list for code field"))))))

(deftest test-update-material-form-has-htmx-attributes
  (tf/testing "Form has HTMX attributes for OOB error swapping"
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :editor}
     [::taxon.i/factory :key/taxon] {:db *db*}
     [::accession.i/factory :key/accession] {:db *db* :taxon (ig/ref :key/taxon)}
     [::location.i/factory :key/location] {:db *db*}
     [::material.i/factory :key/material] {:db *db*
                                           :accession (ig/ref :key/accession)
                                           :location (ig/ref :key/location)}}
    (fn [{:keys [user material]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response]} (-> sess
                                   (peri/request (str "/material/" (:material/id material) "/general/")))
            body (Jsoup/parse ^String (:body response))
            form (.selectFirst body "form#material-form")]
        (is (some? (.attr form "hx-post"))
            "Form should have hx-post attribute")

        (is (= "none" (.attr form "hx-swap"))
            "Form should have hx-swap='none' for OOB error updates")))))

(deftest test-update-material-form-has-error-containers
  (tf/testing "Form fields have error containers with correct IDs for OOB targeting"
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :editor}
     [::taxon.i/factory :key/taxon] {:db *db*}
     [::accession.i/factory :key/accession] {:db *db* :taxon (ig/ref :key/taxon)}
     [::location.i/factory :key/location] {:db *db*}
     [::material.i/factory :key/material] {:db *db*
                                           :accession (ig/ref :key/accession)
                                           :location (ig/ref :key/location)}}
    (fn [{:keys [user material]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response]} (-> sess
                                   (peri/request (str "/material/" (:material/id material) "/general/")))
            body (Jsoup/parse ^String (:body response))]
        (is (some? (.selectFirst body "#code-errors"))
            "Code field should have error container with id code-errors")))))

(deftest test-update-material-form-has-reason-select
  (tf/testing "Form offers the seeded change reasons"
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :editor}
     [::taxon.i/factory :key/taxon] {:db *db*}
     [::accession.i/factory :key/accession] {:db *db* :taxon (ig/ref :key/taxon)}
     [::location.i/factory :key/location] {:db *db*}
     [::material.i/factory :key/material] {:db *db*
                                           :accession (ig/ref :key/accession)
                                           :location (ig/ref :key/location)}}
    (fn [{:keys [user material]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response]} (-> sess
                                   (peri/request (str "/material/" (:material/id material) "/general/")))
            body (Jsoup/parse ^String (:body response))
            select (.selectFirst body "select#reason")
            options (.select select "option")]
        (is (some? select) "Form should have a reason select")
        (is (= 16 (.size options)) "15 reasons plus the None option")
        (is (= "Dead" (.text (.select select "option[value=dead]"))))))))

(deftest test-update-material-move-records-a-change-row
  (tf/testing "POST moving material records the change row with the chosen reason"
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :editor}
     [::taxon.i/factory :key/taxon] {:db *db*}
     [::accession.i/factory :key/accession] {:db *db* :taxon (ig/ref :key/taxon)}
     [::location.i/factory :key/location] {:db *db*}
     [::location.i/factory :key/location2] {:db *db*}
     [::material.i/factory :key/material] {:db *db*
                                           :accession (ig/ref :key/accession)
                                           :location (ig/ref :key/location)}}
    (fn [{:keys [user material location2]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            detail-url (str "/material/" (:material/id material) "/general/")
            {:keys [response]} (-> sess
                                   (peri/request detail-url))
            token (test.i/response-anti-forgery-token response)
            {:keys [response]} (-> sess
                                   (peri/request detail-url
                                                 :request-method :post
                                                 :params {:__anti-forgery-token token
                                                          :code (:material/code material)
                                                          :accession-id (:material/accession-id material)
                                                          :location-id (:location/id location2)
                                                          :quantity (:material/quantity material)
                                                          :status (name (:material/status material))
                                                          :type (name (:material/type material))
                                                          :reason "transferred"}))
            _ (is (contains? #{200 204 302} (:status response))
                  (str "expected a redirect or success, got " (:status response)
                       " with body: " (:body response)))
            changes (material.i/list-by-material-id *db* (:material/id material))]
        (is (= 1 (count changes)))
        (is (= "transferred" (:material-change/reason (first changes))))
        (is (= (:location/id location2)
               (:material-change/to-location-id (first changes))))
        ;; The user halt fails the FK otherwise: save! wrote an activity row.
        (jdbc.sql/delete! *db* :activity {:created_by (:user/id user)})
        (jdbc.sql/delete! *db* :material {:id (:material/id material)})))))

(deftest test-history-panel-shows-three-newest-with-show-all
  (tf/testing "the panel shows the three newest changes and a Show all button beyond that"
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :editor}
     [::taxon.i/factory :key/taxon] {:db *db*}
     [::accession.i/factory :key/accession] {:db *db* :taxon (ig/ref :key/taxon)}
     [::location.i/factory :key/location] {:db *db*}
     [::location.i/factory :key/location2] {:db *db*}
     [::material.i/factory :key/material] {:db *db*
                                           :accession (ig/ref :key/accession)
                                           :location (ig/ref :key/location)}}
    (fn [{:keys [user material location location2]}]
      (let [id (:material/id material)
            ;; Four changes: three moves, one quantity change. Newest last
            ;; written, so the quantity change is the newest.
            _ (doseq [[_ to reason] [[nil (:location/id location2) "transferred"]
                                     [nil (:location/id location) "lost"]
                                     [nil (:location/id location2) "stolen"]]]
                (material.i/create-change! *db* {:material-id id
                                                 :from-location-id (:location/id location)
                                                 :to-location-id to
                                                 :quantity 0
                                                 :reason reason}))
            _ (material.i/create-change! *db* {:material-id id
                                               :quantity -1
                                               :reason "distributed"})
            sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response]} (-> sess
                                   (peri/request (str "/material/" id "/general/")))
            body (Jsoup/parse ^String (:body response))]
        (is (= 200 (:status response)))
        (is (some? (.selectFirst body ":containsOwn(Show all (4))"))
            "four changes -> three shown and a Show all (4) button")
        (is (.contains (.text body) "Distributed elsewhere")
            "the newest change card is shown")
        (let [{:keys [response]} (-> sess
                                     (peri/request (str "/material/" id "/history/")))]
          (is (= 200 (:status response)))
          (let [all-body (Jsoup/parse ^String (:body response))
                cards (.select all-body ".spl-card")]
            (is (= 4 (.size cards)) "the Show all fragment holds every card")
            (is (some? (.selectFirst all-body ":containsOwn(Stolen)"))
                "older cards are present in the full fragment")))
        (jdbc.sql/delete! *db* :material {:id id})))))
