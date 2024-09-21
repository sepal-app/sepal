(ns sepal.app.routes.auth.forgot-password
  (:require [pogonos.core :as mustache]
            [reitit.core :as r]
            [sepal.app.flash :as flash]
            [sepal.app.html :as html]
            [sepal.app.http-response :as http]
            [sepal.app.router :refer [url-for]]
            [sepal.app.routes.auth.page :as page]
            [sepal.app.routes.auth.routes :as auth.routes]
            [sepal.app.ui.form :as form]
            [sepal.postmark.interface :as postmark.i]
            [sepal.user.interface :as user.i]
            [taoensso.nippy :as nippy])
  (:import [java.util Base64]))

(defn page-content [& {:keys [router]}]
  [:div
   [:h1 {:class "text-3xl pb-6"} "Forgot password"]
   [:p {:class "py-4"} "If the email address exists in Sepal then we'll send you
   an email to help reset your password."]
   [:form {:method "post"
           :action (url-for router auth.routes/forgot-password)}
    (form/anti-forgery-field)
    (form/input-field :label "Email"
                      :name "email"
                      :required true
                      :type "email")
    (form/submit-button "Send")]])

(defn render [& {:keys [errors flash router]}]
  (-> (page/page :content (page-content :errors errors
                                        :router router)
                 :flash flash
                 :router router)
      (html/render-html)))

(defn reset-password-token
  "Create a password reset token for an email that's encrypted with secret.

  This token needs to be compatibile
  sepal.app.routes.auth.reset-password#decrypt-reset-password-token
  "
  [email secret]
  (let [timestamp (.getEpochSecond (java.time.Instant/now))
        data (nippy/freeze [email timestamp]
                           {:password [:cached secret]})]
    (-> (Base64/getEncoder)
        (.withoutPadding)
        (.encodeToString data))))

(defn send-reset-password-email [postmark to subject from reset-password-url]
  (let [content (mustache/render-resource "app/email/reset_password.mustache"
                                          {:email to
                                           :reset-password-url reset-password-url
                                           :support-email from})]
    (postmark.i/email postmark {"From" from
                                "To" to
                                "Subject" subject
                                "TextBody" content
                                "MessageStream" "outbound"})))

(defn handler [{:keys [context flash params request-method ::r/router]}]
  (let [{:keys [app-domain db postmark reset-password-secret forgot-password-email-from
                forgot-password-email-subject]} context
        {:keys [email]} params]
    (case request-method
      :post
      (if (user.i/exists? db email)
        (let [token (reset-password-token email reset-password-secret)
              ;; TODO: This needs to be an absolute url
              reset-password-url (format "http://%s%s"
                                         app-domain
                                         (url-for router
                                                  auth.routes/reset-password
                                                  nil
                                                  {:token token}))
              resp (send-reset-password-email postmark
                                              email
                                              forgot-password-email-subject
                                              forgot-password-email-from
                                              reset-password-url)]
          (if (= (:status resp) 200)
            (-> (http/found router auth.routes/forgot-password)
                (flash/add-message "Check your email."))
            (do
              ;; TODO: PRoper logging
              (println (str "Error: Could not send forgot password email: " resp))
              (-> (http/found router auth.routes/forgot-password)
                  (flash/error "Error: Could not send email.")))))
        (-> (http/found router auth.routes/forgot-password)
            (flash/add-message "Check your email.")))

      ;; else
      (render :router router
              :flash flash))))
