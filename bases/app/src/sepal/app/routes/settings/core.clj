(ns sepal.app.routes.settings.core
  (:require [sepal.app.http-response :as http]
            [sepal.app.middleware :as middleware]
            [sepal.app.routes.settings.backups.download :as backups.download]
            [sepal.app.routes.settings.backups.index :as backups.index]
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
  ["" {:middleware [[middleware/require-viewer]]}
   ["" {:name settings.routes/index
        :handler (fn [_] (http/see-other settings.routes/profile))}]
   ["/profile" {:name settings.routes/profile
                :handler #'profile/handler}]
   ["/security" {:name settings.routes/security
                 :handler #'security/handler}]
   ;; Admin only
   ["/organization" {:name settings.routes/organization
                     :middleware [[middleware/require-admin]]
                     :handler #'organization/handler}]
   ;; Backups (admin only)
   ["/backups" {:middleware [[middleware/require-admin]]}
    ["" {:name settings.routes/backups
         :get #'backups.index/handler
         :post #'backups.index/handler}]
    ["/:filename/download" {:name settings.routes/backup-download
                            :get #'backups.download/handler}]]
   ;; User management (admin only)
   ["/users" {:middleware [[middleware/require-admin]]}
    ["" {:name settings.routes/users
         :get #'users.index/handler}]
    ["/invite" {:name settings.routes/users-invite
                :get #'users.invite/handler
                :post #'users.invite/handler}]
    ["/:id/role" {:name settings.routes/users-update-role
                  :post #'users.update-role/handler}]
    ["/:id/archive" {:name settings.routes/users-archive
                     :post #'users.archive/handler}]
    ["/:id/activate" {:name settings.routes/users-activate
                      :post #'users.activate/handler}]
    ["/:id/resend-invitation" {:name settings.routes/users-resend-invitation
                               :post #'users.resend-invitation/handler}]]])
