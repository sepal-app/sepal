(ns sepal.app.routes.auth.reset-password
  (:require [reitit.core :as r]
            [sepal.app.flash :as flash]
            [sepal.app.html :as html]
            [sepal.app.http-response :as http]
            [sepal.app.router :refer [url-for]]
            [sepal.app.routes.auth.page :as page]
            [sepal.app.routes.auth.routes :as auth.routes]
            [sepal.app.ui.form :as form]
            [sepal.user.interface :as user.i]
            [taoensso.nippy :as nippy])
  (:import [java.time Instant]
           [java.util Base64]))

(defn page-content [& {:keys [email router token]}]
  [:div
   [:h1 {:class "text-2xl pb-2"} "Reset the password for "]
   [:p {:class "text-lg pb-6"} email]
   (form/form {:method "post"
               :x-validate.use-browser.input "true"
               :action (url-for router auth.routes/reset-password)}
              [(form/anti-forgery-field)
               (form/hidden-field :name "token" :value token)
               (form/input-field :label "Password"
                                 :name "password"
                                 :minlength 8
                                 :x-validate true
                                 :required true
                                 :data-error-msg "The password must be a minimum of 8 characters long.")
               (form/input-field :label "Confirm password"
                                 :name "confirm_password"
                                 :minlength 8
                                 :required true
                                 :data-error-msg "The passwords do not match"
                                 :input-attrs {:x-validate "$el.value === $formData.password.value"})
               (form/submit-button "Send")])])

(defn render [& {:keys [email errors flash router token]}]
  (-> (page/page :content (page-content :email email
                                        :errors errors
                                        :router router
                                        :token token)
                 :flash flash
                 :router router)
      (html/render-html)))

(defn token-valid? [created-at]
  (let [now (Instant/now)
        ten-minutes-ago (.minus now 10 java.time.temporal.ChronoUnit/MINUTES)]
    (.isAfter created-at ten-minutes-ago)))

(defn decode-reset-password-token
  "Decode a reset password token that was encoded with
  sepal.app.routes.auth.forgot-password#reset-password-token"
  [token secret]
  (try
    (let [[email created-at] (-> (Base64/getDecoder)
                                 (.decode token)
                                 (nippy/thaw {:password [:cached secret]}))]
      {:email email
       :created-at (java.time.Instant/ofEpochSecond created-at)})
    (catch clojure.lang.ExceptionInfo _ex
      ;; TODO: Use proper logging
      (println (str "Warning: Could not decrypt reset password token: " token)))))

(defn handler [{:keys [context flash params request-method ::r/router]}]
  (let [{:keys [db reset-password-secret]} context
        {:keys [token]} params
        {:keys [email created-at]} (decode-reset-password-token token
                                                                reset-password-secret)
        user (user.i/get-by-email db email)]

    (case request-method
      :post
      (let [{:keys [password]} params]
        (if (and (some? user)
                 (token-valid? created-at))
          (do
            (user.i/set-password! db (:user/id user) password)
            (-> (http/found router auth.routes/login)
                (flash/add-message "Your password has been reset.")))
          (-> (http/found router auth.routes/login {:token token})
              (flash/error "Invalid password reset token."))))

      ;; else
      (if (some? user)
        (render :email email
                ;; :errors errors
                :router router
                :token token
                :flash flash)
        (-> (http/found router auth.routes/login {:token token})
            (flash/error "Invalid password reset token."))))))
