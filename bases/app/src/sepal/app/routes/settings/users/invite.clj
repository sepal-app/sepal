(ns sepal.app.routes.settings.users.invite
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log]
            [failjure.core :as f]
            [sepal.app.flash :as flash]
            [sepal.app.http-response :as http]
            [sepal.app.routes.auth.routes :as auth.routes]
            [sepal.app.routes.settings.layout :as layout]
            [sepal.app.routes.settings.routes :as settings.routes]
            [sepal.app.ui.form :as form]
            [sepal.error.interface :as error.i]
            [sepal.i18n.interface :refer [tr]]
            [sepal.mail.interface :as mail.i]
            [sepal.token.interface :as token.i]
            [sepal.user.interface :as user.i]
            [sepal.user.interface.activity :as user.activity]
            [sepal.user.interface.spec :as user.spec]
            [sepal.validation.interface :as validation.i]
            [zodiac.core :as z]))

(def InvitationForm
  [:map {:closed true}
   [:email user.spec/email]
   [:role user.spec/role]
   [:full-name {:optional true
                :decode/form validation.i/empty->nil}
    [:maybe :string]]])

(defn- generate-random-password
  "Generate a random 32-character password for invited users.
   This password is never used - the user sets their own password when accepting."
  []
  (let [chars "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*"]
    (apply str (repeatedly 32 #(rand-nth chars)))))

(defn- role-select [& {:keys [value errors]}]
  (let [selected-value (or value "reader")]
    (form/field
      :name "role"
      :label "Role"
      :errors errors
      :input [:select {:name "role"
                       :id "role"
                       :class "spl-input spl-select w-full max-w-sm"
                       :required true}
              (for [{:keys [value label]} [{:value "reader" :label "Reader"}
                                           {:value "editor" :label "Editor"}
                                           {:value "admin" :label "Admin"}]]
                [:option {:value value
                          :selected (= value selected-value)}
                 label])])))

(defn- page-content [& {:keys [errors values]}]
  [:div
   [:h1 {:class "text-2xl font-bold mb-6"} "Invite User"]
   (form/form {:action (z/url-for settings.routes/users-invite)
               :method "post"}
              [(form/anti-forgery-field)
               (form/input-field :label "Email"
                                 :name "email"
                                 :type "email"
                                 :required true
                                 :value (:email values)
                                 :errors (:email errors))
               (form/input-field :label "Full Name"
                                 :name "full-name"
                                 :placeholder "Optional"
                                 :value (:full-name values)
                                 :errors (:full-name errors))
               (role-select :value (:role values)
                            :errors (:role errors))
               [:p {:class "text-sm text-text-muted mt-4"}
                "An invitation email will be sent to this address. The invitation expires in 24 hours."]
               ;; Cancel then the primary action, the order every other form in
               ;; the app uses.
               [:div {:class "flex gap-4 mt-6"}
                [:a {:href (z/url-for settings.routes/users)
                     :class "spl-btn"}
                 "Cancel"]
                (form/submit-button {:class "spl-btn spl-btn--primary"} "Send Invitation")]])])

(defn- render [& {:keys [errors values viewer]}]
  (layout/layout
    :viewer viewer
    :current-route settings.routes/users-invite
    :category "Organization"
    :title "Invite User"
    :content (page-content :errors errors :values values)))

(defn- build-accept-url [app-base-url token]
  (str app-base-url (z/url-for auth.routes/accept-invitation nil {:token token})))

(defn invitation-subject
  "What the invitation is called in an inbox.

  The garden's name leads, because that is what tells the recipient which
  invitation this is — someone may keep records for more than one. Falls back
  to the configured subject, and then to a plain one, so a garden that has not
  named itself still sends a sensible email."
  [organization-name configured]
  (cond
    organization-name (tr "You have been invited to %1 on Sepal" organization-name)
    (not (str/blank? configured)) configured
    :else (tr "You have been invited to Sepal")))

(defn invitation-body [{:keys [full-name inviter-name inviter-email organization-name accept-url]}]
  (str (str/join "\n\n"
                 [(if full-name (tr "Hello %1," full-name) (tr "Hello,"))
                  (if organization-name
                    (tr "%1 (%2) has invited you to join %3 on Sepal, a botanical collection management system."
                        inviter-name inviter-email organization-name)
                    (tr "%1 (%2) has invited you to join Sepal, a botanical collection management system."
                        inviter-name inviter-email))
                  (tr "To accept this invitation and set up your account, click the link below:")
                  accept-url
                  (tr "After clicking the link, you will be asked to set a password for your account. Once your password is set, you will need to log in with your email address and new password.")
                  (tr "This invitation expires in 24 hours.")
                  (tr "If you weren't expecting this invitation, you can safely ignore this email.")])
       "\n"))

(defn send-invitation-email
  "Public so resend-invitation sends the same email. It was a private copy
  there, and the two had already drifted: only this one names the garden.

  Sends in the current locale, which is the inviter's: the invitee has no
  account and no browser to ask yet."
  [mail {:keys [to from subject] :as invitation}]
  (mail.i/send-message mail {:from from
                             :to to
                             :subject subject
                             :body (invitation-body invitation)}))

(defn- check-email-exists [db email]
  (when-let [existing-user (user.i/get-by-email db email)]
    (if (= :archived (:user/status existing-user))
      {:email ["This email is already registered (user is archived)"]}
      {:email ["This email is already registered"]})))

(defn handler [{:keys [::z/context form-params request-method viewer]}]
  (let [{:keys [app-base-url db mail token-service organization-name
                invitation-email-from invitation-email-subject]} context]
    (case request-method
      :post
      (f/attempt-all [result (validation.i/validate-form-values InvitationForm form-params)]
        (let [{:keys [email role full-name]} result
              email-exists-error (check-email-exists db email)]
          (if email-exists-error
            (render :viewer viewer
                    :errors email-exists-error
                    :values form-params)
            ;; Create user and send invitation
            (f/attempt-all [user-result (f/try* (user.i/create! db {:email email
                                                                    :password (generate-random-password)
                                                                    :role role
                                                                    :full-name full-name
                                                                    :status :invited}))]
              (let [_ (user.activity/create-user! db (:user/id viewer) user-result)
                    token (token.i/encode token-service
                                          {:email email
                                           :expires-at (token.i/expires-in-hours 24)})
                    accept-url (build-accept-url app-base-url token)
                    inviter-name (or (:user/full-name viewer) (:user/email viewer))]
                (try
                  (send-invitation-email mail
                                         {:to email
                                          :full-name full-name
                                          :inviter-name inviter-name
                                          :inviter-email (:user/email viewer)
                                          :accept-url accept-url
                                          :organization-name organization-name
                                          :from invitation-email-from
                                          :subject (invitation-subject
                                                     organization-name
                                                     invitation-email-subject)})
                  (-> (http/see-other settings.routes/users)
                      (flash/add-message (str "Invitation sent to " email)))
                  (catch Exception e
                    (println (str "Error: Could not send invitation email: " (ex-message e)))
                    (-> (http/see-other settings.routes/users)
                        (flash/error "User created but failed to send invitation email")))))
              (f/when-failed [e]
                ;; Log it. This swallowed the exception and showed one fixed
                ;; sentence, so an invitation failing in production told
                ;; nobody what went wrong — including the operator reading
                ;; the logs.
                (log/error e "could not create the invited user")
                (render :viewer viewer
                        ;; The check above and the insert are not atomic, so
                        ;; a double-submitted form can lose the race to its
                        ;; own first request. Look again before blaming the
                        ;; insert.
                        :errors (or (check-email-exists db email)
                                    (error.i/humanize
                                      (if (instance? Exception e)
                                        (error.i/ex->error e)
                                        e))
                                    {:email ["Failed to create user"]})
                        :values form-params)))))
        (f/when-failed [e]
          (render :viewer viewer
                  :errors (error.i/humanize e)
                  :values form-params)))

      ;; GET
      (render :viewer viewer :values form-params))))
