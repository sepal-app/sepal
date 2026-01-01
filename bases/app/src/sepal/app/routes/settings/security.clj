(ns sepal.app.routes.settings.security
  (:require [sepal.app.flash :as flash]
            [sepal.app.http-response :as http]
            [sepal.app.routes.settings.layout :as layout]
            [sepal.app.routes.settings.routes :as settings.routes]
            [sepal.app.ui.form :as form]
            [sepal.error.interface :as error.i]
            [sepal.user.interface :as user.i]
            [sepal.user.interface.activity :as user.activity]
            [sepal.validation.interface :as validation.i]
            [zodiac.core :as z]))

(defn password-form [& {:keys [errors]}]
  (form/form
    {:method "post"
     :action (z/url-for settings.routes/security)}
    (form/anti-forgery-field)
    (form/input-field :label "Current password"
                      :name "current_password"
                      :type "password"
                      :required true
                      :errors (:current_password errors))
    (form/input-field :label "New password"
                      :name "new_password"
                      :type "password"
                      :required true
                      :errors (:new_password errors))
    (form/input-field :label "Confirm new password"
                      :name "confirm_password"
                      :type "password"
                      :required true
                      :errors (:confirm_password errors))
    [:div {:class "mt-4"}
     (layout/save-button "Change password")]))

(defn render [& {:keys [viewer errors flash]}]
  (layout/layout
    :viewer viewer
    :current-route settings.routes/security
    :category "Account"
    :title "Security"
    :flash flash
    :content (password-form :errors errors)))

(def FormParams
  [:and
   [:map {:closed true}
    form/AntiForgeryField
    [:current_password [:string {:min 1}]]
    [:new_password [:string {:min 8}]]
    [:confirm_password [:string {:min 1}]]]
   [:fn {:error/message "New passwords don't match"
         :error/path [:confirm_password]}
    (fn [{:keys [new_password confirm_password]}]
      (= new_password confirm_password))]])

(defn handler [{:keys [::z/context flash form-params request-method viewer]}]
  (let [{:keys [db]} context]
    (case request-method
      :post
      (let [result (validation.i/validate-form-values FormParams form-params)]
        (if (error.i/error? result)
          (http/validation-errors (validation.i/humanize result))
          (let [{:keys [current_password new_password]} result]
            (if (user.i/verify-password db (:user/email viewer) current_password)
              (do
                (user.i/set-password! db (:user/id viewer) new_password)
                (user.activity/create! db
                                       (:user/id viewer)  ;; created-by
                                       viewer             ;; user entity
                                       {})                ;; additional data
                (-> (http/see-other settings.routes/security)
                    (flash/success "Password changed successfully")))
              (-> (http/see-other settings.routes/security)
                  (flash/error "Current password is incorrect"))))))

      (render :viewer viewer :flash flash))))
