(ns sepal.app.authorization-test
  (:require [clojure.test :refer [deftest is testing]]
            [sepal.accession.interface.permission :as accession.perm]
            [sepal.app.authorization :as authz]))

(deftest has-permission?-test
  (testing "admin has all permissions"
    (is (authz/has-permission? :admin authz/organization-view))
    (is (authz/has-permission? :admin authz/organization-edit))
    (is (authz/has-permission? :admin authz/users-view))
    (is (authz/has-permission? :admin accession.perm/view))
    (is (authz/has-permission? :admin accession.perm/create))
    (is (authz/has-permission? :admin accession.perm/edit))
    (is (authz/has-permission? :admin accession.perm/delete)))

  (testing "editor has resource CRUD but not org/users"
    (is (not (authz/has-permission? :editor authz/organization-view)))
    (is (not (authz/has-permission? :editor authz/organization-edit)))
    (is (not (authz/has-permission? :editor authz/users-view)))
    (is (authz/has-permission? :editor accession.perm/view))
    (is (authz/has-permission? :editor accession.perm/create))
    (is (authz/has-permission? :editor accession.perm/edit))
    (is (authz/has-permission? :editor accession.perm/delete)))

  (testing "reader has only view permissions for resources"
    (is (not (authz/has-permission? :reader authz/organization-view)))
    (is (not (authz/has-permission? :reader authz/users-view)))
    (is (authz/has-permission? :reader accession.perm/view))
    (is (not (authz/has-permission? :reader accession.perm/create)))
    (is (not (authz/has-permission? :reader accession.perm/edit)))
    (is (not (authz/has-permission? :reader accession.perm/delete))))

  (testing "all roles have profile and security permissions"
    (doseq [role [:admin :editor :reader]]
      (is (authz/has-permission? role authz/profile-view))
      (is (authz/has-permission? role authz/profile-edit))
      (is (authz/has-permission? role authz/security-view))
      (is (authz/has-permission? role authz/security-edit)))))

(deftest user-has-permission?-test
  (testing "checks user's role for permission"
    (let [admin {:user/role :admin}
          editor {:user/role :editor}
          reader {:user/role :reader}]
      (is (authz/user-has-permission? admin authz/organization-edit))
      (is (not (authz/user-has-permission? editor authz/organization-edit)))
      (is (not (authz/user-has-permission? reader authz/organization-edit)))))

  (testing "returns nil for user without role"
    (is (nil? (authz/user-has-permission? {} authz/organization-view)))
    (is (nil? (authz/user-has-permission? nil authz/organization-view)))))

(deftest role-predicates-test
  (testing "admin?"
    (is (authz/admin? {:user/role :admin}))
    (is (not (authz/admin? {:user/role :editor})))
    (is (not (authz/admin? {:user/role :reader}))))

  (testing "editor?"
    (is (not (authz/editor? {:user/role :admin})))
    (is (authz/editor? {:user/role :editor}))
    (is (not (authz/editor? {:user/role :reader}))))

  (testing "reader?"
    (is (not (authz/reader? {:user/role :admin})))
    (is (not (authz/reader? {:user/role :editor})))
    (is (authz/reader? {:user/role :reader}))))

(deftest can-edit?-test
  (testing "admin and editor can edit"
    (is (authz/can-edit? {:user/role :admin}))
    (is (authz/can-edit? {:user/role :editor})))

  (testing "reader cannot edit"
    (is (not (authz/can-edit? {:user/role :reader})))))
