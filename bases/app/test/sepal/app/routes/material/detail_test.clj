(ns sepal.app.routes.material.detail-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is use-fixtures]]
            [integrant.core :as ig]
            [next.jdbc.sql :as jdbc.sql]
            [peridot.core :as peri]
            [sepal.accession.interface :as accession.i]
            [sepal.app.codes :as codes]
            [sepal.app.routes.material.detail.general :as detail-general]
            [sepal.app.test :as app.test]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.location.interface :as location.i]
            [sepal.material.interface :as material.i]
            [sepal.settings.interface :as settings.i]
            [sepal.taxon.interface :as taxon.i]
            [sepal.test.interface :as test.i]
            [sepal.user.interface :as user.i])
  (:import [org.jsoup Jsoup]))

(use-fixtures :once default-system-fixture)

;; Settings rows outlive a test and the suite shares one database.
(use-fixtures :each (fn [t] (try (t) (finally (app.test/reset-codes! *db*)))))

(defn- in-place-fixtures []
  {[::user.i/factory :key/user] {:db *db*
                                 :password "testpassword123"
                                 :role :editor}
   [::taxon.i/factory :key/taxon] {:db *db*}
   [::accession.i/factory :key/accession] {:db *db* :taxon (ig/ref :key/taxon)}
   [::location.i/factory :key/location] {:db *db*}
   [::material.i/factory :key/material] {:db *db*
                                         :accession (ig/ref :key/accession)
                                         :location (ig/ref :key/location)}})

(defn- post-general
  "Posts the general form for `material` the way htmx does, with `overrides`
  on top of the record's own values."
  [user material overrides]
  (let [sess (app.test/login (:user/email user) "testpassword123")
        url (str "/material/" (:material/id material) "/general/")
        {:keys [response] :as sess} (peri/request sess url)
        token (test.i/response-anti-forgery-token response)]
    (:response (peri/request sess url
                             :request-method :post
                             :headers {"hx-request" "true"}
                             :params (merge {:__anti-forgery-token token
                                             :code (:material/code material)
                                             :accession-id (:material/accession-id material)
                                             :location-id (:material/location-id material)
                                             :quantity (:material/quantity material)
                                             :status (name (:material/status material))
                                             :type (name (:material/type material))
                                             :reason ""}
                                            overrides)))))

(defn- clean-up! [user material]
  (jdbc.sql/delete! *db* :activity {:created_by (:user/id user)})
  (jdbc.sql/delete! *db* :material {:id (:material/id material)}))

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

        (is (= "morph" (.attr form "hx-swap"))
            "Form should morph the page in place")))))

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
        (is (= 17 (.size options)) "16 reasons plus the None option")
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
                                                 :headers {"hx-request" "true"}
                                                 :params {:__anti-forgery-token token
                                                          :code (:material/code material)
                                                          :accession-id (:material/accession-id material)
                                                          :location-id (:location/id location2)
                                                          :quantity (:material/quantity material)
                                                          :status (name (:material/status material))
                                                          :type (name (:material/type material))
                                                          :reason "transferred"}))
            _ (is (app.test/saved-in-place? response))
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

(deftest test-every-tab-carries-the-actions-menu
  (tf/testing "each of a material's tabs offers the same actions"
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
      (let [sess (app.test/login (:user/email user) "testpassword123")]
        (doseq [tab ["general" "media" "observations" "tags"]]
          (let [body (Jsoup/parse ^String (:body (:response (peri/request sess (str "/material/" (:material/id material) "/" tab "/")))))]
            (is (some? (.selectFirst body ".spl-actions-menu"))
                (str "the " tab " tab has the actions menu"))
            (is (.contains (.text body) "Add a propagation")
                (str "the " tab " tab offers Add a propagation"))))))))

(deftest test-a-material-save-answers-with-the-page
  (tf/testing "the page comes back with the saved code in every place it shows"
    (in-place-fixtures)
    (fn [{:keys [user material]}]
      (try
        (let [response (post-general user material {:code "NEW-CODE"})
              body (Jsoup/parse ^String (:body response))]
          (is (app.test/saved-in-place? response))
          (is (str/includes? (.text (.selectFirst body ".spl-crumbs-current")) "NEW-CODE")
              "the breadcrumb")
          (is (str/includes? (.text (.selectFirst body ".spl-record")) "NEW-CODE")
              "the record header")
          (is (str/includes? (.text (.selectFirst body "title")) "NEW-CODE")
              "the browser tab")
          (is (= "NEW-CODE" (.attr (.selectFirst body "input[name=code]") "value"))
              "the form, from the saved record"))
        (finally
          (clean-up! user material))))))

(deftest test-a-code-the-template-rejects-asks-for-confirmation
  (tf/testing "422 with the confirmation and no page"
    (in-place-fixtures)
    (fn [{:keys [user material]}]
      (settings.i/set-values! *db* {"codes.material_template" "M{seq:000}"
                                    "codes.material_strict" "1"})
      (let [response (post-general user material {:code "NOT-A-MATCH"})
            body (:body response)]
        (is (= 422 (:status response)))
        (is (some? (.selectFirst (Jsoup/parse ^String body)
                                 (str "#" codes/confirm-target-id)))
            "the confirmation")
        (is (not (str/includes? body "page-region")))))))

(deftest test-a-material-save-that-throws-answers-with-a-flash
  (tf/testing "422 with the error flash and no page"
    (in-place-fixtures)
    (fn [{:keys [user material]}]
      (with-redefs [detail-general/save! (fn [& _] (throw (ex-info "disk full" {})))]
        (let [response (post-general user material {:code "NEW-CODE"})]
          (is (= 422 (:status response)))
          (is (not (str/includes? (str (:body response)) "page-region")))
          (is (str/includes? (str (:body response)) "Could not save the material")))))))
