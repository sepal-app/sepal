(ns sepal.app.routes.auth.accept-invitation
  (:require [sepal.app.flash :as flash]
            [sepal.app.http-response :as http]
            [sepal.app.routes.auth.page :as page]
            [sepal.app.routes.auth.routes :as auth.routes]
            [sepal.app.ui.form :as form]
            [sepal.error.interface :as error.i]
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
   [:h1 {:class "text-2xl font-bold mb-2"} "Accept Invitation"]
   [:p {:class "text-lg mb-6"} "Set up your account for " [:strong email]]
   (form/form {:action (z/url-for auth.routes/accept-invitation)
               :method "post"}
              [(form/anti-forgery-field)
               (form/hidden-field :name "token" :value token)
               (form/input-field :label "Full Name"
                                 :name "full-name"
                                 :placeholder "Your name"
                                 :value full-name
                                 :errors (:full-name errors))
               (form/input-field :label "Password"
                                 :name "password"
                                 :type "password"
                                 :minlength 8
                                 :required true
                                 :errors (:password errors))
               (form/input-field :label "Confirm Password"
                                 :name "confirm-password"
                                 :type "password"
                                 :minlength 8
                                 :required true
                                 :errors (:confirm-password errors))
               [:div {:class "mt-6"}
                (form/submit-button "Set Password & Activate Account")]])])

(defn- render [& {:keys [email full-name token errors flash]}]
  (page/page :content (page-content :email email
                                    :full-name full-name
                                    :token token
                                    :errors errors)
             :flash flash))

(defn- invalid-invitation-response []
  (-> (http/found auth.routes/login)
      (flash/error "Invalid invitation")))

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
          (-> (http/found auth.routes/login {:email email})
              (flash/add-message "Account already activated. Please log in."))

          ;; User is invited - process invitation
          (= :invited (:user/status user))
          (case request-method
            :post
            (let [result (validation.i/validate-form-values AcceptInvitationForm params)]
              (if (error.i/error? result)
                (render :email email
                        :full-name (or (get params "full-name") (:user/full-name user))
                        :token token
                        :errors (validation.i/humanize result))
                (if-not (passwords-match? result)
                  (render :email email
                          :full-name (:full-name result)
                          :token token
                          :errors {:confirm-password ["Passwords do not match"]})
                  ;; All good - activate user
                  (let [{:keys [password full-name]} result
                        user-id (:user/id user)]
                    ;; Update full name if provided
                    (when full-name
                      (user.i/update! db user-id {:full-name full-name}))
                    ;; Set password
                    (user.i/set-password! db user-id password)
                    ;; Activate user
                    (user.i/activate! db user-id)
                    ;; Redirect to login with email prefilled
                    (let [display-name (or full-name (:user/full-name user) email)]
                      (-> (http/found auth.routes/login {:email email})
                          (flash/add-message (str "Password set for " display-name ". Please log in."))))))))

            ;; GET - show form
            (render :email email
                    :full-name (:user/full-name user)
                    :token token
                    :flash flash))

          ;; Unknown status - shouldn't happen
          :else
          (invalid-invitation-response)))

      ;; Token invalid or expired
      (invalid-invitation-response))))
