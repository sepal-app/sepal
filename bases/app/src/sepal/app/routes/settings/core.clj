(ns sepal.app.routes.settings.core
  (:require [sepal.app.authorization :as authz]
            [sepal.app.http-response :as http]
            [sepal.app.routes.settings.backups.download :as backups.download]
            [sepal.app.routes.settings.backups.index :as backups.index]
            [sepal.app.routes.settings.codes :as codes]
            [sepal.app.routes.settings.organization :as organization]
            [sepal.app.routes.settings.profile :as profile]
            [sepal.app.routes.settings.routes :as settings.routes]
            [sepal.app.routes.settings.security :as security]
            [sepal.app.routes.settings.users.activate :as users.activate]
            [sepal.app.routes.settings.users.archive :as users.archive]
            [sepal.app.routes.settings.users.index :as users.index]
            [sepal.app.routes.settings.users.invite :as users.invite]
            [sepal.app.routes.settings.users.resend-invitation :as users.resend-invitation]
            [sepal.app.routes.settings.users.update-role :as users.update-role]))

(defn routes []
  [""
   ["" {:name settings.routes/index
        :permission authz/profile-view
        :handler (fn [_] (http/see-other settings.routes/profile))}]
   ["/profile" {:name settings.routes/profile
                :permission authz/profile-edit
                :handler #'profile/handler}]
   ["/security" {:name settings.routes/security
                 :permission authz/security-edit
                 :handler #'security/handler}]
   ;; Admin only. Codes and backups have no permissions of their own, so they
   ;; go with organization-edit.
   ["/organization" {:name settings.routes/organization
                     :permission authz/organization-edit
                     :handler #'organization/handler}]
   ["/codes" {:name settings.routes/codes
              :permission authz/organization-edit
              :get #'codes/handler
              :post #'codes/handler}]
   ["/backups"
    ["" {:name settings.routes/backups
         :permission authz/organization-edit
         :get #'backups.index/handler
         :post #'backups.index/handler}]
    ["/:filename/download" {:name settings.routes/backup-download
                            :permission authz/organization-edit
                            :get #'backups.download/handler}]]
   ["/users"
    ["" {:name settings.routes/users
         :permission authz/users-view
         :get #'users.index/handler}]
    ["/invite" {:name settings.routes/users-invite
                :permission authz/users-create
                :get #'users.invite/handler
                :post #'users.invite/handler}]
    ["/:id/role" {:name settings.routes/users-update-role
                  :permission authz/users-change-role
                  :post #'users.update-role/handler}]
    ["/:id/archive" {:name settings.routes/users-archive
                     :permission authz/users-edit
                     :post #'users.archive/handler}]
    ["/:id/activate" {:name settings.routes/users-activate
                      :permission authz/users-edit
                      :post #'users.activate/handler}]
    ["/:id/resend-invitation" {:name settings.routes/users-resend-invitation
                               :permission authz/users-create
                               :post #'users.resend-invitation/handler}]]])
