(ns sepal.app.routes.settings.users.invite
  (:require [pogonos.core :as mustache]
            [sepal.app.flash :as flash]
            [sepal.app.http-response :as http]
            [sepal.app.routes.auth.routes :as auth.routes]
            [sepal.app.routes.settings.layout :as layout]
            [sepal.app.routes.settings.users.routes :as users.routes]
            [sepal.app.ui.form :as form]
            [sepal.error.interface :as error.i]
            [sepal.mail.interface :as mail.i]
            [sepal.token.interface :as token.i]
            [sepal.user.interface :as user.i]
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
                       :class "select select-bordered select-md w-full max-w-sm"
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
   (form/form {:action (z/url-for users.routes/invite)
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
               [:p {:class "text-sm text-base-content/70 mt-4"}
                "An invitation email will be sent to this address. The invitation expires in 24 hours."]
               [:div {:class "flex gap-4 mt-6"}
                (form/submit-button {:class "btn btn-primary"} "Send Invitation")
                [:a {:href (z/url-for users.routes/index)
                     :class "btn"}
                 "Cancel"]]])])

(defn- render [& {:keys [errors values viewer]}]
  (layout/layout
    :viewer viewer
    :current-route users.routes/invite
    :category "Organization"
    :title "Invite User"
    :content (page-content :errors errors :values values)))

(defn- build-accept-url [app-domain token]
  (format "https://%s%s"
          app-domain
          (z/url-for auth.routes/accept-invitation nil {:token token})))

(defn- send-invitation-email [mail {:keys [to full-name inviter-name inviter-email accept-url from subject]}]
  (let [content (mustache/render-resource "app/email/invitation.mustache"
                                          {:full-name full-name
                                           :inviter-name inviter-name
                                           :inviter-email inviter-email
                                           :accept-url accept-url})]
    (mail.i/send-message mail {:from from
                               :to to
                               :subject subject
                               :body content})))

(defn- check-email-exists [db email]
  (when-let [existing-user (user.i/get-by-email db email)]
    (if (= :archived (:user/status existing-user))
      {:email ["This email is already registered (user is archived)"]}
      {:email ["This email is already registered"]})))

(defn handler [{:keys [::z/context form-params request-method viewer]}]
  (let [{:keys [app-domain db mail token-service
                invitation-email-from invitation-email-subject]} context]
    (case request-method
      :post
      (let [result (validation.i/validate-form-values InvitationForm form-params)]
        (if (error.i/error? result)
          (render :viewer viewer
                  :errors (validation.i/humanize result)
                  :values form-params)
          (let [{:keys [email role full-name]} result
                email-exists-error (check-email-exists db email)]
            (if email-exists-error
              (render :viewer viewer
                      :errors email-exists-error
                      :values form-params)
              ;; Create user and send invitation
              (let [user-result (user.i/create! db {:email email
                                                    :password (generate-random-password)
                                                    :role role
                                                    :full-name full-name
                                                    :status :invited})]
                (if (error.i/error? user-result)
                  (render :viewer viewer
                          :errors {:email ["Failed to create user"]}
                          :values form-params)
                  (let [token (token.i/encode token-service
                                              {:email email
                                               :expires-at (token.i/expires-in-hours 24)})
                        accept-url (build-accept-url app-domain token)
                        inviter-name (or (:user/full-name viewer) (:user/email viewer))]
                    (try
                      (send-invitation-email mail
                                             {:to email
                                              :full-name full-name
                                              :inviter-name inviter-name
                                              :inviter-email (:user/email viewer)
                                              :accept-url accept-url
                                              :from invitation-email-from
                                              :subject invitation-email-subject})
                      (-> (http/see-other users.routes/index)
                          (flash/add-message (str "Invitation sent to " email)))
                      (catch Exception e
                        (println (str "Error: Could not send invitation email: " (ex-message e)))
                        (-> (http/see-other users.routes/index)
                            (flash/error "User created but failed to send invitation email")))))))))))

      ;; GET
      (render :viewer viewer :values form-params))))
