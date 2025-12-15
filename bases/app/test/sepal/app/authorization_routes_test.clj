(ns sepal.app.authorization-routes-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [integrant.core :as ig]
            [peridot.core :as peri]
            [ring.middleware.session.store :as store]
            [sepal.accession.interface :as accession.i]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*app* *db* *cookie-store* default-system-fixture]]
            [sepal.contact.interface :as contact.i]
            [sepal.taxon.interface :as taxon.i]
            [sepal.test.interface :as test.i]
            [sepal.user.interface :as user.i]))

(use-fixtures :once default-system-fixture)

(defn- redirect? [{:keys [status]}]
  (contains? #{301 302 303 307 308} status))

(defn- follow-redirects
  "Follow redirects until we get a non-redirect response."
  [sess]
  (if (redirect? (:response sess))
    (recur (peri/follow-redirect sess))
    sess))

(defn- login-as
  "Create a user with the given role and return a logged-in peridot session."
  [db role]
  (let [email (str (name role) "-" (random-uuid) "@test.com")
        password "password123"
        _ (user.i/create! db {:email email
                              :password password
                              :role role})
        {:keys [response] :as sess} (-> (peri/session *app*)
                                        (peri/request "/login"))
        token (test.i/response-anti-forgery-token response)]
    (-> sess
        (peri/request "/login"
                      :request-method :post
                      :params {:__anti-forgery-token token
                               :email email
                               :password password})
        (follow-redirects))))

(deftest settings-organization-admin-only-test
  (testing "admin can access organization settings"
    (let [sess (login-as *db* :admin)
          {:keys [response]} (peri/request sess "/settings/organization")]
      (is (= 200 (:status response)))))

  (testing "editor cannot access organization settings"
    (let [sess (login-as *db* :editor)
          {:keys [response]} (peri/request sess "/settings/organization")]
      (is (= 403 (:status response)))))

  (testing "reader cannot access organization settings"
    (let [sess (login-as *db* :reader)
          {:keys [response]} (peri/request sess "/settings/organization")]
      (is (= 403 (:status response))))))

(deftest profile-settings-all-roles-test
  (testing "all roles can access profile settings"
    (doseq [role [:admin :editor :reader]]
      (testing (str role " role")
        (let [sess (login-as *db* role)
              {:keys [response]} (peri/request sess "/settings/profile")]
          (is (= 200 (:status response))))))))

(deftest accession-create-route-test
  (testing "admin can access accession create"
    (let [sess (login-as *db* :admin)
          {:keys [response]} (peri/request sess "/accession/new/")]
      (is (= 200 (:status response)))))

  (testing "editor can access accession create"
    (let [sess (login-as *db* :editor)
          {:keys [response]} (peri/request sess "/accession/new/")]
      (is (= 200 (:status response)))))

  (testing "reader cannot access accession create"
    (let [sess (login-as *db* :reader)
          {:keys [response]} (peri/request sess "/accession/new/")]
      (is (= 403 (:status response))))))

(deftest accession-index-all-roles-test
  (testing "all roles can access accession index"
    (doseq [role [:admin :editor :reader]]
      (testing (str role " role")
        (let [sess (login-as *db* role)
              {:keys [response]} (peri/request sess "/accession/")]
          (is (= 200 (:status response))))))))

(deftest taxon-create-route-test
  (testing "admin can access taxon create"
    (let [sess (login-as *db* :admin)
          {:keys [response]} (peri/request sess "/taxon/new/")]
      (is (= 200 (:status response)))))

  (testing "editor can access taxon create"
    (let [sess (login-as *db* :editor)
          {:keys [response]} (peri/request sess "/taxon/new/")]
      (is (= 200 (:status response)))))

  (testing "reader cannot access taxon create"
    (let [sess (login-as *db* :reader)
          {:keys [response]} (peri/request sess "/taxon/new/")]
      (is (= 403 (:status response))))))

(deftest location-create-route-test
  (testing "admin can access location create"
    (let [sess (login-as *db* :admin)
          {:keys [response]} (peri/request sess "/location/new/")]
      (is (= 200 (:status response)))))

  (testing "editor can access location create"
    (let [sess (login-as *db* :editor)
          {:keys [response]} (peri/request sess "/location/new/")]
      (is (= 200 (:status response)))))

  (testing "reader cannot access location create"
    (let [sess (login-as *db* :reader)
          {:keys [response]} (peri/request sess "/location/new/")]
      (is (= 403 (:status response))))))

(deftest contact-create-route-test
  (testing "admin can access contact create"
    (let [sess (login-as *db* :admin)
          {:keys [response]} (peri/request sess "/contact/new/")]
      (is (= 200 (:status response)))))

  (testing "editor can access contact create"
    (let [sess (login-as *db* :editor)
          {:keys [response]} (peri/request sess "/contact/new/")]
      (is (= 200 (:status response)))))

  (testing "reader cannot access contact create"
    (let [sess (login-as *db* :reader)
          {:keys [response]} (peri/request sess "/contact/new/")]
      (is (= 403 (:status response))))))

;; =============================================================================
;; Role-Aware UI Tests
;; =============================================================================

(defn- body-contains? [response pattern]
  (re-find (re-pattern pattern) (:body response)))

(deftest settings-sidebar-organization-visibility-test
  (testing "admin sees Organization section in settings sidebar"
    (let [sess (login-as *db* :admin)
          {:keys [response]} (peri/request sess "/settings/profile")]
      (is (= 200 (:status response)))
      (is (body-contains? response "Organization"))))

  (testing "editor does not see Organization section in settings sidebar"
    (let [sess (login-as *db* :editor)
          {:keys [response]} (peri/request sess "/settings/profile")]
      (is (= 200 (:status response)))
      (is (not (body-contains? response ">Organization<")))))

  (testing "reader does not see Organization section in settings sidebar"
    (let [sess (login-as *db* :reader)
          {:keys [response]} (peri/request sess "/settings/profile")]
      (is (= 200 (:status response)))
      (is (not (body-contains? response ">Organization<"))))))

(deftest index-page-create-button-visibility-test
  (testing "admin sees Create button on accession index"
    (let [sess (login-as *db* :admin)
          {:keys [response]} (peri/request sess "/accession/")]
      (is (= 200 (:status response)))
      (is (body-contains? response "Create"))))

  (testing "editor sees Create button on accession index"
    (let [sess (login-as *db* :editor)
          {:keys [response]} (peri/request sess "/accession/")]
      (is (= 200 (:status response)))
      (is (body-contains? response "Create"))))

  (testing "reader does not see Create button on accession index"
    (let [sess (login-as *db* :reader)
          {:keys [response]} (peri/request sess "/accession/")]
      (is (= 200 (:status response)))
      ;; Create button has specific class and text - check it's not present
      (is (not (body-contains? response "btn-primary[^>]*>Create<"))))))

(deftest detail-page-redirect-test
  (tf/testing "admin is redirected to edit tabs on accession detail"
    {[::taxon.i/factory :key/taxon] {:db *db*}
     [::accession.i/factory :key/accession] {:db *db* :taxon (ig/ref :key/taxon)}}
    (fn [{:keys [accession]}]
      (let [sess (login-as *db* :admin)
            {:keys [response]} (peri/request sess (str "/accession/" (:accession/id accession) "/"))]
        ;; Should redirect to /general/
        (is (= 302 (:status response)))
        (is (re-find #"/general/$" (get-in response [:headers "Location"]))))))

  (tf/testing "reader sees panel view inline on accession detail (no redirect)"
    {[::taxon.i/factory :key/taxon] {:db *db*}
     [::accession.i/factory :key/accession] {:db *db* :taxon (ig/ref :key/taxon)}}
    (fn [{:keys [accession]}]
      (let [sess (login-as *db* :reader)
            {:keys [response]} (peri/request sess (str "/accession/" (:accession/id accession) "/"))]
        ;; Should render panel as full page (200), not redirect
        (is (= 200 (:status response)))
        ;; Should contain panel content
        (is (body-contains? response "Summary"))))))

(deftest contact-detail-redirect-test
  (tf/testing "editor sees form on contact detail"
    {[::contact.i/factory :key/contact] {:db *db*}}
    (fn [{:keys [contact]}]
      (let [sess (login-as *db* :editor)
            {:keys [response]} (peri/request sess (str "/contact/" (:contact/id contact) "/"))]
        (is (= 200 (:status response)))
        ;; Form should be present
        (is (body-contains? response "<form")))))

  (tf/testing "reader sees panel view inline on contact detail (no redirect)"
    {[::contact.i/factory :key/contact] {:db *db*}}
    (fn [{:keys [contact]}]
      (let [sess (login-as *db* :reader)
            {:keys [response]} (peri/request sess (str "/contact/" (:contact/id contact) "/"))]
        ;; Should render panel as full page (200), not redirect
        (is (= 200 (:status response)))
        ;; Should contain panel content (Summary section is in panel)
        (is (body-contains? response "Summary"))
        ;; Form should NOT be present for readers
        (is (not (body-contains? response "<form")))))))

(deftest edit-route-redirect-test
  (testing "reader accessing edit route is redirected to detail (not 403)"
    (tf/testing "accession general tab redirects readers to detail"
      {[::taxon.i/factory :key/taxon] {:db *db*}
       [::accession.i/factory :key/accession] {:db *db* :taxon (ig/ref :key/taxon)}}
      (fn [{:keys [accession]}]
        (let [sess (login-as *db* :reader)
              {:keys [response]} (peri/request sess (str "/accession/" (:accession/id accession) "/general/"))]
          ;; Should redirect to detail page, not return 403
          (is (= 302 (:status response)))
          (is (re-find #"/accession/\d+/$" (get-in response [:headers "Location"])))))))

  (testing "editor can access edit route normally"
    (tf/testing "accession general tab works for editors"
      {[::taxon.i/factory :key/taxon] {:db *db*}
       [::accession.i/factory :key/accession] {:db *db* :taxon (ig/ref :key/taxon)}}
      (fn [{:keys [accession]}]
        (let [sess (login-as *db* :editor)
              {:keys [response]} (peri/request sess (str "/accession/" (:accession/id accession) "/general/"))]
          (is (= 200 (:status response))))))))
