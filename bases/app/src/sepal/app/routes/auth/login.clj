(ns sepal.app.routes.auth.login
  (:require [sepal.app.flash :as flash]
            [sepal.app.html :as html]
            [sepal.app.http-response :as http]
            [sepal.app.routes.auth.page :as page]
            [sepal.app.routes.auth.routes :as auth.routes]
            [sepal.app.routes.dashboard.routes :as dashboard.routes]
            [sepal.app.session :as session]
            [sepal.app.ui.form :as form]
            [sepal.user.interface :as user.i]
            [zodiac.core :as z]))

#_(defn send-verification-email [email]
  ;; TODO: bump
    )

;; TODO: Use htmx to submit so we don't do a full page reload

(defn form [& {:keys [email invitation next]}]
  (form/form {:method "post"
              :action (z/url-for auth.routes/login)}
             (form/anti-forgery-field)
             (when invitation
               (form/hidden-field :name "invitation" :value invitation))
             (form/input-field :label "Email" :name "email" :value email :type "email"
                               :require true)
             (form/input-field :label "Password" :name "password" :type "password"
                               :required true)
             (form/hidden-field :name "next" :value next)
             [:div {:class "flex flex-row mt-4 justify-between items-center"}
              [:button {:type "submit"
                         ;; :x-bind:disabled "submitting"
                        :class (html/attr "inline-flex" "justify-center" "py-2" "px-4" "border"
                                          "border-transparent" "shadow-sm" "text-sm" "font-medium"
                                          "rounded-md" "text-white" "bg-green-700" "hover:bg-green-700"
                                          "focus:outline-none" "focus:ring-2" "focus:ring-offset-2"
                                          "focus:ring-green-500")}
               "Login"]
              [:p
               [:a {:href (z/url-for auth.routes/forgot-password)}
                "Forgot password?"]]]

             [:div {:class "mt-4"}
              [:a {:href (z/url-for auth.routes/register)} "Don't have an account?"]]))

(defn render [& {:keys [email #_field-errors invitation next flash]}]
  (page/page :content [:div
                       [:h1 {:class "text-3xl pb-6"} "Welcome to Sepal"]
                       (form :email email
                             :invitation invitation
                             :next next)]
             :flash flash))

(defn handler [{:keys [::z/context flash params request-method]}]
  (let [{:keys [db]} context
        ;; TODO: we need to params encode this because we're getting the params
        ;; with string keys
        {:strs [email password invitation next]} params]
    ;; TODO: Put the email in the session so we can put the value in the input
    ;; after the redirect on error

    (case request-method
      :post
      (let [user (user.i/verify-password db email password)
            error (when-not user "Invalid password")
            session (when-not error (session/user->session user))]
        (if-not error
          (-> (http/see-other dashboard.routes/index)
              (assoc :session session))
          ;; TODO: pass params on redirect
          (-> (http/see-other auth.routes/login)
              (flash/error error))))

      ;; else
      (render :next next
              :email email
              :invitation invitation
              :next next
              :field-errors nil
              :flash flash))))
