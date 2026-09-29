(ns sepal.app.routes.auth.forgot-password
  (:require [clojure.string :as str]
            [sepal.app.flash :as flash]
            [sepal.app.http-response :as http]
            [sepal.app.routes.auth.page :as page]
            [sepal.app.routes.auth.routes :as auth.routes]
            [sepal.app.ui.form :as form]
            [sepal.i18n.interface :as i18n :refer [tr]]
            [sepal.mail.interface :as mail.i]
            [sepal.token.interface :as token.i]
            [sepal.user.interface :as user.i]
            [zodiac.core :as z]))

(defn page-content [& {:keys []}]
  [:div
   [:h1 {:class "spl-auth-title"} (tr "Forgot password")]
   [:p {:class "py-4"} (tr "If the email address exists in Sepal then we'll send you an email to help reset your password.")]
   [:form {:method "post"
           :action (z/url-for auth.routes/forgot-password)}
    (form/anti-forgery-field)
    (form/input-field :label (tr "Email")
                      :name "email"
                      :required true
                      :type "email")
    (form/submit-button {:class "spl-btn spl-btn--primary mt-4"} (tr "Send"))]])

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

(defn reset-password-body [email reset-password-url support-email]
  (str (str/join "\n\n"
                 [(tr "A request was made to reset the Sepal password for %1" email)
                  (tr "If you didn't request the password to be reset you can safely ignore this email.")
                  (tr "To reset the password for %1 open the following link: %2" email reset-password-url)
                  (tr "If you have concerns about this email please feel free to reach out to %1" support-email)])
       "\n"))

(defn send-reset-password-email
  "Sends in the language `subject` and the body are rendered in, so call it
  inside the recipient's locale. A configured subject is sent as configured."
  [mail to subject from reset-password-url]
  (mail.i/send-message mail {:from from
                             :to to
                             :subject (or subject (tr "Sepal - Reset Password"))
                             :body (reset-password-body to reset-password-url from)}))

(def ^:private link-route
  "Where the emailed link goes, by user status. An invited user has no password
  to reset yet, so their link goes to the invitation page, which sets one."
  {:active auth.routes/reset-password
   :invited auth.routes/accept-invitation})

(defn handler [{:keys [::z/context flash params request-method]}]
  (let [{:keys [app-base-url db mail token-service forgot-password-email-from
                forgot-password-email-subject]} context
        {:strs [email]} params]
    (case request-method
      :post
      ;; Only send email for active and invited users (prevents enumeration)
      (let [user (user.i/get-by-email db email)]
        (if-let [route (link-route (:user/status user))]
          (let [token (reset-password-token token-service email)
                reset-password-url (str app-base-url
                                        (z/url-for route
                                                   nil
                                                   {:token token}))]
            (try
              ;; The recipient's saved language, else the browser's: the
              ;; person asking is the person the email is for.
              (i18n/with-locale (or (:user/language user) i18n/*locale*)
                (send-reset-password-email mail
                                           email
                                           forgot-password-email-subject
                                           forgot-password-email-from
                                           reset-password-url))
              (-> (http/found auth.routes/forgot-password)
                  (flash/add-message (tr "Check your email.")))
              (catch Exception e
                ;; TODO: Proper logging
                (println (str "Error: Could not send forgot password email: " (ex-message e)))
                (-> (http/found auth.routes/forgot-password)
                    (flash/error (tr "Error: Could not send email."))))))
          ;; Show same message for non-existent/inactive users (no enumeration)
          (-> (http/found auth.routes/forgot-password)
              (flash/add-message (tr "Check your email.")))))

      ;; else
      (render :flash flash))))
