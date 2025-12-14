(ns sepal.app.authorization-routes-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [peridot.core :as peri]
            [ring.middleware.session.store :as store]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*app* *db* *cookie-store* default-system-fixture]]
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
