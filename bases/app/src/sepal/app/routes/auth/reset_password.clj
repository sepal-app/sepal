(ns sepal.app.routes.auth.reset-password
  (:require [sepal.app.flash :as flash]
            [sepal.app.http-response :as http]
            [sepal.app.routes.auth.page :as page]
            [sepal.app.routes.auth.routes :as auth.routes]
            [sepal.app.ui.form :as form]
            [sepal.token.interface :as token.i]
            [sepal.user.interface :as user.i]
            [zodiac.core :as z]))

(defn page-content [& {:keys [email token]}]
  [:div
   [:h1 {:class "text-2xl pb-2"} "Reset the password for "]
   [:p {:class "text-lg pb-6"} email]
   (form/form {:method "post"
               :action (z/url-for auth.routes/reset-password)}
              [(form/anti-forgery-field)
               (form/hidden-field :name "token" :value token)
               (form/input-field :label "Password"
                                 :name "password"
                                 :minlength 8
                                 :required true
                                 :data-error-msg "The password must be a minimum of 8 characters long.")
               (form/input-field :label "Confirm password"
                                 :name "confirm_password"
                                 :minlength 8
                                 :required true
                                 :data-error-msg "The passwords do not match")
               (form/submit-button "Send")])])

(defn render [& {:keys [email errors flash token]}]
  (page/page :content (page-content :email email
                                    :errors errors
                                    :token token)
             :flash flash))

(defn- active-user? [user]
  (and (some? user)
       (= :active (:user/status user))))

(defn handler [{:keys [::z/context flash params request-method]}]
  (let [{:keys [db token-service]} context
        ;; params contains merged query-params and form-params with string keys
        {:strs [token password]} params]
    ;; token.i/valid? returns the decoded data if token is valid and not expired
    (if-let [{:keys [email]} (token.i/valid? token-service token)]
      (let [user (user.i/get-by-email db email)]
        ;; Only allow password reset for active users
        (if (active-user? user)
          (case request-method
            :post
            (do
              (user.i/set-password! db (:user/id user) password)
              (-> (http/found auth.routes/login)
                  (flash/add-message "Your password has been reset.")))

            ;; GET - show password reset form
            (render :email email
                    :token token
                    :flash flash))
          ;; User not found or not active
          (-> (http/found auth.routes/login)
              (flash/error "Invalid password reset token."))))
      ;; Token invalid or expired
      (-> (http/found auth.routes/login)
          (flash/error "Invalid password reset token.")))))
