(ns sepal.app.routes.settings.users.resend-invitation
  (:require [pogonos.core :as mustache]
            [sepal.app.flash :as flash]
            [sepal.app.http-response :as http]
            [sepal.app.routes.auth.routes :as auth.routes]
            [sepal.app.routes.settings.users.routes :as users.routes]
            [sepal.mail.interface :as mail.i]
            [sepal.token.interface :as token.i]
            [sepal.user.interface :as user.i]
            [zodiac.core :as z]))

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

(defn handler [{:keys [::z/context path-params viewer]}]
  (let [{:keys [app-domain db mail token-service
                invitation-email-from invitation-email-subject]} context
        user-id (parse-long (:id path-params))
        user (when user-id (user.i/get-by-id db user-id))]
    (cond
      ;; User not found
      (nil? user)
      (-> (http/see-other users.routes/index)
          (flash/error "User not found"))

      ;; User is not in invited status
      (not= :invited (:user/status user))
      (-> (http/see-other users.routes/index)
          (flash/error "Can only resend invitations for users with 'invited' status"))

      ;; User is invited - resend
      :else
      (let [email (:user/email user)
            full-name (:user/full-name user)
            token (token.i/encode token-service
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
              (flash/add-message (str "Invitation resent to " email)))
          (catch Exception e
            (println (str "Error: Could not resend invitation email: " (ex-message e)))
            (-> (http/see-other users.routes/index)
                (flash/error "Failed to send invitation email"))))))))
