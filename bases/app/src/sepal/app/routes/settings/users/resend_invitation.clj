(ns sepal.app.routes.settings.users.resend-invitation
  (:require [clojure.tools.logging :as log]
            [sepal.app.flash :as flash]
            [sepal.app.html :as html]
            [sepal.app.routes.auth.routes :as auth.routes]
            [sepal.app.routes.settings.users.invite :as invite]
            [sepal.token.interface :as token.i]
            [sepal.user.interface :as user.i]
            [zodiac.core :as z]))

(defn- build-accept-url [app-base-url token]
  (str app-base-url (z/url-for auth.routes/accept-invitation nil {:token token})))

(defn- say
  "Answer with a message and nothing else.

  The button posts with hx-swap=\"none\" because resending changes nothing on
  the page, so there is no markup to return — and a redirect, which this used
  to answer with, leaves the message in the session for a page load that never
  happens. An empty 200 lets wrap-flash-messages swap the banner in out of
  band, which is the same route users/activate takes."
  [response-fn message]
  (-> (html/render-partial "")
      (response-fn message)))

(defn handler [{:keys [::z/context path-params viewer]}]
  (let [{:keys [app-base-url db mail token-service organization-name
                invitation-email-from invitation-email-subject]} context
        user-id (parse-long (:id path-params))
        user (when user-id (user.i/get-by-id db user-id))]
    (cond
      (nil? user)
      (say flash/error "User not found")

      (not= :invited (:user/status user))
      (say flash/error "Can only resend invitations for users with 'invited' status")

      :else
      (let [email (:user/email user)
            token (token.i/encode token-service
                                  {:email email
                                   :expires-at (token.i/expires-in-hours 24)})]
        (try
          (invite/send-invitation-email
            mail
            {:to email
             :full-name (:user/full-name user)
             :inviter-name (or (:user/full-name viewer) (:user/email viewer))
             :inviter-email (:user/email viewer)
             :accept-url (build-accept-url app-base-url token)
             :organization-name organization-name
             :from invitation-email-from
             :subject (invite/invitation-subject organization-name
                                                 invitation-email-subject)})
          (say flash/success (str "Invitation resent to " email))
          (catch Exception e
            ;; Logged, not printed. A resend failing in production said only
            ;; "Failed to send invitation email" and put the reason nowhere.
            (log/error e "could not resend the invitation email")
            (say flash/error "Failed to send invitation email")))))))
