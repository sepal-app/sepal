(ns sepal.app.authorization-routes-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [integrant.core :as ig]
            [peridot.core :as peri]
            [sepal.accession.interface :as accession.i]
            [sepal.app.authorization :as authz]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*app* *db* default-system-fixture]]
            [sepal.contact.interface :as contact.i]
            [sepal.location.interface :as location.i]
            [sepal.material.interface :as material.i]
            [sepal.media.interface :as media.i]
            [sepal.note.interface :as note.i]
            [sepal.note.interface.permission :as note.perm]
            [sepal.observation.interface :as observation.i]
            [sepal.observation.interface.permission :as observation.perm]
            [sepal.propagation.interface :as propagation.i]
            [sepal.tag.interface :as tag.i]
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

(deftest panel-actions-visibility-test
  (tf/testing "the side panel's Actions menu is shown only to roles that can edit"
    {[::taxon.i/factory :key/taxon] {:db *db*}
     [::accession.i/factory :key/accession] {:db *db* :taxon (ig/ref :key/taxon)}
     [::location.i/factory :key/location] {:db *db*}
     [::material.i/factory :key/material] {:db *db*
                                           :accession (ig/ref :key/accession)
                                           :location (ig/ref :key/location)}}
    (fn [{:keys [taxon accession material]}]
      (doseq [[role shown?] [[:editor true] [:reader false]]
              :let [sess (login-as *db* role)]
              path [(str "/accession/" (:accession/id accession) "/panel/")
                    (str "/material/" (:material/id material) "/panel/")
                    (str "/taxon/" (:taxon/id taxon) "/panel/")]]
        (testing (str role " " path)
          (let [{:keys [response]} (peri/request sess path)]
            (is (= 200 (:status response)))
            (is (= shown? (boolean (body-contains? response "spl-actions-menu"))))))))))

(deftest detail-post-requires-edit-test
  (tf/testing "a detail page's POST needs the resource's edit permission"
    {[::user.i/factory :key/user] {:db *db*}
     [::taxon.i/factory :key/taxon] {:db *db*}
     [::accession.i/factory :key/accession] {:db *db* :taxon (ig/ref :key/taxon)}
     [::contact.i/factory :key/contact] {:db *db*}
     [::propagation.i/factory :key/propagation] {:db *db* :accession (ig/ref :key/accession)}
     [::media.i/factory :key/media] {:db *db* :user (ig/ref :key/user)}}
    (fn [{:keys [contact propagation media]}]
      (let [paths [(str "/contact/" (:contact/id contact) "/")
                   (str "/propagation/" (:propagation/id propagation) "/")
                   (str "/media/" (:media/id media) "/")]
            post (fn [role path]
                   (let [sess (login-as *db* role)
                         {:keys [response] :as sess} (peri/request sess "/settings/profile")
                         token (test.i/response-anti-forgery-token response)]
                     (:response (peri/request sess path
                                              :request-method :post
                                              :params {:__anti-forgery-token token
                                                       :name "Changed"
                                                       :title "Changed"}))))]
        (doseq [path paths]
          (testing (str "reader " path)
            (is (= 403 (:status (post :reader path))))))
        (testing "and the reader's POST wrote nothing"
          (is (= (:contact/name contact) (:contact/name (contact.i/get-by-id *db* (:contact/id contact)))))
          (is (= (:media/title media) (:media/title (media.i/get-by-id *db* (:media/id media))))))
        (doseq [path paths]
          (testing (str "editor " path)
            (is (not= 403 (:status (post :editor path))))))))))

(deftest tag-detail-roles-test
  (tf/testing "a reader sees a tag's page but can't save it"
    {[::tag.i/factory :key/tag] {:db *db*}}
    (fn [{:keys [tag]}]
      (let [path (str "/tag/" (:tag/id tag) "/")]
        (testing "reader GET shows the panel without a form"
          (let [{:keys [response]} (peri/request (login-as *db* :reader) path)]
            (is (= 200 (:status response)))
            (is (body-contains? response "Linked records"))
            (is (not (body-contains? response "<form")))))

        (testing "reader POST is refused and writes nothing"
          (let [{:keys [response] :as sess} (peri/request (login-as *db* :reader) "/settings/profile")
                {:keys [response]} (peri/request sess path
                                                 :request-method :post
                                                 :params {:__anti-forgery-token (test.i/response-anti-forgery-token response)
                                                          :name "Changed"})]
            (is (= 403 (:status response)))
            (is (= (:tag/name tag) (:tag/name (tag.i/get-by-id *db* (:tag/id tag)))))))

        (testing "editor GET shows the form, and the editor's POST saves"
          (let [{:keys [response] :as sess} (peri/request (login-as *db* :editor) path)
                _ (is (= 200 (:status response)))
                _ (is (body-contains? response "<form"))
                {:keys [response]} (peri/request sess path
                                                 :request-method :post
                                                 :params {:__anti-forgery-token (test.i/response-anti-forgery-token response)
                                                          :name "Renamed"
                                                          :description ""})]
            (is (= 200 (:status response)))
            (is (= "Renamed" (:tag/name (tag.i/get-by-id *db* (:tag/id tag)))))))))))

(deftest notes-and-observations-use-their-own-permissions-test
  (tf/testing "adding a note or an observation needs its own create permission"
    {[::taxon.i/factory :key/taxon] {:db *db*}
     [::accession.i/factory :key/accession] {:db *db* :taxon (ig/ref :key/taxon)}
     [::location.i/factory :key/location] {:db *db*}
     [::material.i/factory :key/material] {:db *db*
                                           :accession (ig/ref :key/accession)
                                           :location (ig/ref :key/location)}}
    (fn [{:keys [accession material]}]
      ;; A reader given only the two create permissions: the parent records'
      ;; edit permission is still missing.
      (with-redefs [authz/permissions (update authz/permissions :reader conj
                                              note.perm/create observation.perm/create)]
        (let [{:keys [response] :as sess} (peri/request (login-as *db* :reader) "/settings/profile")
              token (test.i/response-anti-forgery-token response)
              notes (str "/accession/" (:accession/id accession) "/notes/")
              observations (str "/material/" (:material/id material) "/observations/")]
          (testing "the tabs still need the parent's edit permission"
            (is (= 302 (:status (:response (peri/request sess notes)))))
            (is (= 302 (:status (:response (peri/request sess observations))))))

          (testing "a note needs only note.perm/create"
            (is (= 200 (:status (:response (peri/request sess notes
                                                         :request-method :post
                                                         :params {:__anti-forgery-token token
                                                                  :body "A reader's note"})))))
            (is (some #(= "A reader's note" (:note/body %))
                      (note.i/get-for-resource *db* :accession (:accession/id accession)))))

          (testing "an observation needs only observation.perm/create"
            (is (= 200 (:status (:response (peri/request sess observations
                                                         :request-method :post
                                                         :params {:__anti-forgery-token token
                                                                  :type "general"
                                                                  :value ""
                                                                  :observed_on "2026-01-01"
                                                                  :observed_by ""
                                                                  :next_check_on ""
                                                                  :note "A reader's observation"})))))
            (is (some #(= "A reader's observation" (:observation/note %))
                      (observation.i/get-for-resource *db* :material (:material/id material))))))))))

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
