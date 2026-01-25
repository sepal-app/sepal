(ns sepal.app.routes.auth.forgot-password
  (:require [pogonos.core :as mustache]
            [sepal.app.flash :as flash]
            [sepal.app.http-response :as http]
            [sepal.app.routes.auth.page :as page]
            [sepal.app.routes.auth.routes :as auth.routes]
            [sepal.app.ui.form :as form]
            [sepal.mail.interface :as mail.i]
            [sepal.token.interface :as token.i]
            [sepal.user.interface :as user.i]
            [zodiac.core :as z]))

(defn page-content [& {:keys []}]
  [:div
   [:h1 {:class "text-3xl pb-6"} "Forgot password"]
   [:p {:class "py-4"} "If the email address exists in Sepal then we'll send you
   an email to help reset your password."]
   [:form {:method "post"
           :action (z/url-for auth.routes/forgot-password)}
    (form/anti-forgery-field)
    (form/input-field :label "Email"
                      :name "email"
                      :required true
                      :type "email")
    (form/submit-button {:class "btn btn-primary mt-4"} "Send")]])

(defn render [& {:keys [errors flash]}]
  (page/page :content (page-content :errors errors)
             :flash flash))

(defn reset-password-token
  "Create a password reset token for an email using the token service.
   Token expires in 30 minutes."
  [token-service email]
  (token.i/encode token-service
                  {:email email
                   :expires-at (token.i/expires-in-minutes 30)}))

(defn send-reset-password-email [mail to subject from reset-password-url]
  (let [content (mustache/render-resource "app/email/reset_password.mustache"
                                          {:email to
                                           :reset-password-url reset-password-url
                                           :support-email from})]
    (mail.i/send-message mail {:from from
                               :to to
                               :subject subject
                               :body content})))

(defn- active-user? [user]
  (and (some? user)
       (= :active (:user/status user))))

(defn handler [{:keys [::z/context flash params request-method]}]
  (let [{:keys [app-domain db mail token-service forgot-password-email-from
                forgot-password-email-subject]} context
        {:keys [email]} params]
    (case request-method
      :post
      ;; Only send reset email for active users (prevents enumeration)
      (let [user (user.i/get-by-email db email)]
        (if (active-user? user)
          (let [token (reset-password-token token-service email)
                ;; TODO: This needs to be an absolute url
                reset-password-url (format "https://%s%s"
                                           app-domain
                                           (z/url-for auth.routes/reset-password
                                                      nil
                                                      {:token token}))]
            (try
              (send-reset-password-email mail
                                         email
                                         forgot-password-email-subject
                                         forgot-password-email-from
                                         reset-password-url)
              (-> (http/found auth.routes/forgot-password)
                  (flash/add-message "Check your email."))
              (catch Exception e
                ;; TODO: Proper logging
                (println (str "Error: Could not send forgot password email: " (ex-message e)))
                (-> (http/found auth.routes/forgot-password)
                    (flash/error "Error: Could not send email.")))))
          ;; Show same message for non-existent/inactive users (no enumeration)
          (-> (http/found auth.routes/forgot-password)
              (flash/add-message "Check your email."))))

      ;; else
      (render :flash flash))))
