(ns sepal.app.routes.auth.accept-invitation
  (:require [failjure.core :as f]
            [sepal.app.flash :as flash]
            [sepal.app.http-response :as http]
            [sepal.app.routes.auth.page :as page]
            [sepal.app.routes.auth.routes :as auth.routes]
            [sepal.app.ui.form :as form]
            [sepal.error.interface :as error.i]
            [sepal.i18n.interface :as i18n :refer [tr]]
            [sepal.token.interface :as token.i]
            [sepal.user.interface :as user.i]
            [sepal.user.interface.spec :as user.spec]
            [sepal.validation.interface :as validation.i]
            [zodiac.core :as z]))

(def AcceptInvitationForm
  [:map {:closed true}
   [:token :string]
   [:full-name {:optional true
                :decode/form validation.i/empty->nil}
    [:maybe :string]]
   [:password user.spec/password]
   [:confirm-password :string]])

(defn- passwords-match? [{:keys [password confirm-password]}]
  (= password confirm-password))

(defn- page-content [& {:keys [email full-name token errors]}]
  [:div
   [:h1 {:class "spl-auth-title"} (tr "Accept invitation")]
   (into [:p {:class "text-lg mb-6"}]
         (i18n/fill (tr "Set up your account for %1") [:strong email]))
   (form/form {:action (z/url-for auth.routes/accept-invitation)
               :method "post"}
              [(form/anti-forgery-field)
               (form/hidden-field :name "token" :value token)
               (form/input-field :label (tr "Full name")
                                 :name "full-name"
                                 :placeholder (tr "Your name")
                                 :value full-name
                                 :errors (:full-name errors))
               (form/input-field :label (tr "Password")
                                 :name "password"
                                 :type "password"
                                 :minlength 8
                                 :required true
                                 :errors (:password errors))
               (form/input-field :label (tr "Confirm password")
                                 :name "confirm-password"
                                 :type "password"
                                 :minlength 8
                                 :required true
                                 :errors (:confirm-password errors))
               (form/submit-button {:class "spl-btn spl-btn--primary mt-6"} (tr "Set password and activate account"))])])

(defn- render [& {:keys [email full-name token errors flash]}]
  (page/page :content (page-content :email email
                                    :full-name full-name
                                    :token token
                                    :errors errors)
             :flash flash))

(defn- unusable-invitation-page [title message]
  (page/page :content [:div
                       [:h1 {:class "spl-auth-title"} title]
                       [:p {:class "text-lg"} message]]))

(defn- invalid-invitation-response []
  (unusable-invitation-page
    (tr "Invitation not valid")
    (tr "This invitation link isn't valid. Check that you opened the whole link from the email, or ask the person who invited you to send a new one.")))

(defn- expired-invitation-response []
  (unusable-invitation-page
    (tr "Invitation expired")
    (tr "This invitation has expired. Ask the person who invited you to send a new one.")))

(defn- already-activated-response [email]
  (-> (http/found auth.routes/login {:email email})
      (flash/add-message (tr "Account already activated. Please log in."))))

(defn handler [{:keys [::z/context flash params request-method]}]
  (let [{:keys [db token-service]} context
        {:strs [token]} params]
    ;; First validate the token
    (if-let [{:keys [email]} (token.i/valid? token-service token)]
      ;; Token is valid - check user status
      (let [user (user.i/get-by-email db email)]
        (cond
          ;; User not found or archived - invalid
          (or (nil? user) (= :archived (:user/status user)))
          (invalid-invitation-response)

          ;; User already active - redirect to login
          (= :active (:user/status user))
          (already-activated-response email)

          ;; User is invited - process invitation
          (= :invited (:user/status user))
          (case request-method
            :post
            (f/attempt-all [result (validation.i/validate-form-values AcceptInvitationForm params)]
              (if-not (passwords-match? result)
                (render :email email
                        :full-name (:full-name result)
                        :token token
                        :errors {:confirm-password [(tr "Passwords do not match")]})
                ;; All good - activate user
                (f/attempt-all [_activated (f/try* (let [{:keys [password full-name]} result
                                                         user-id (:user/id user)]
                                                     ;; Update full name if provided
                                                     (when full-name
                                                       (user.i/update! db user-id {:full-name full-name}))
                                                     ;; Set password
                                                     (user.i/set-password! db user-id password)
                                                     ;; Activate user
                                                     (user.i/activate! db user-id)))]
                  ;; Redirect to login with email prefilled
                  (let [display-name (or (:full-name result) (:user/full-name user) email)]
                    (-> (http/found auth.routes/login {:email email})
                        (flash/add-message (tr "Password set for %1. Please log in." display-name))))
                  (f/when-failed [e]
                    (http/failure-flash e (http/found auth.routes/login {:email email}) (tr "Could not set your password. Please try again.")))))
              (f/when-failed [e]
                (render :email email
                        :full-name (or (get params "full-name") (:user/full-name user))
                        :token token
                        :errors (error.i/humanize e))))

            ;; GET - show form
            (render :email email
                    :full-name (:user/full-name user)
                    :token token
                    :flash flash))

          ;; Unknown status - shouldn't happen
          :else
          (invalid-invitation-response)))

      ;; Expired, or not a token this garden issued. An expired link is often
      ;; an old email for an account activated since through a resent one.
      (if-let [{:keys [email]} (token.i/decode token-service token)]
        (if (= :active (:user/status (user.i/get-by-email db email)))
          (already-activated-response email)
          (expired-invitation-response))
        (invalid-invitation-response)))))
