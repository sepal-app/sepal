(ns sepal.app.routes.settings.routes)

(def index :settings/index)
(def profile :settings/profile)
(def security :settings/security)
(def organization :settings/organization)
(def backups :settings/backups)
(def backup-download :settings/backup-download)

;; User management routes
(def users :settings.users/index)
(def users-invite :settings.users/invite)
(def users-resend-invitation :settings.users/resend-invitation)
(def users-update-role :settings.users/update-role)
(def users-archive :settings.users/archive)
(def users-activate :settings.users/activate)
