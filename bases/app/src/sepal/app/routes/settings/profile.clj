(ns sepal.app.routes.settings.profile
  (:require [sepal.app.flash :as flash]
            [sepal.app.http-response :as http]
            [sepal.app.routes.auth.routes :as auth.routes]
            [sepal.app.routes.settings.layout :as layout]
            [sepal.app.routes.settings.routes :as settings.routes]
            [sepal.app.ui.form :as ui.form]
            [sepal.error.interface :as error.i]
            [sepal.user.interface :as user.i]
            [sepal.user.interface.activity :as user.activity]
            [sepal.validation.interface :as validation.i]
            [zodiac.core :as z]))

(defn profile-form [& {:keys [values errors]}]
  (ui.form/form
    {:method "post"
     :action (z/url-for settings.routes/profile)}
    (ui.form/anti-forgery-field)
    (ui.form/input-field :label "Full name"
                         :name "full-name"
                         :value (:full-name values)
                         :errors (:full-name errors))
    (ui.form/input-field :label "Email"
                         :name "email"
                         :type "email"
                         :value (:email values)
                         :required true
                         :errors (:email errors))
    [:div {:class "mt-4"}
     (layout/save-button "Save changes")]))

(defn page-content [& {:keys [values errors]}]
  [:div
   [:div {:class "flex justify-end mb-6"}
    [:a {:href (z/url-for auth.routes/logout)
         :class "btn btn-outline btn-error btn-sm"}
     "Logout"]]
   (profile-form :values values :errors errors)])

(defn render [& {:keys [viewer values errors flash]}]
  (layout/layout
    :viewer viewer
    :current-route settings.routes/profile
    :category "Account"
    :title "Profile"
    :flash flash
    :content (page-content :values values :errors errors)))

(def FormParams
  [:map {:closed true}
   ui.form/AntiForgeryField
   [:full-name {:decode/form validation.i/empty->nil} [:maybe :string]]
   [:email [:string {:min 1}]]])

(defn handler [{:keys [::z/context flash form-params request-method viewer]}]
  (let [{:keys [db]} context
        values {:full-name (:user/full-name viewer)
                :email (:user/email viewer)}]
    (case request-method
      :post
      (let [result (validation.i/validate-form-values FormParams form-params)]
        (if (error.i/error? result)
          (http/validation-errors (validation.i/humanize result))
          (let [updated (user.i/update! db (:user/id viewer) result)]
            (if (error.i/error? updated)
              (-> (http/see-other settings.routes/profile)
                  (flash/error "Failed to update profile"))
              (do
                (user.activity/create! db
                                       (:user/id viewer)  ;; created-by
                                       viewer             ;; user entity
                                       {})                ;; additional data (schema doesn't support full_name)
                (-> (http/see-other settings.routes/profile)
                    (flash/success "Profile updated successfully")))))))

      (render :viewer viewer :values values :flash flash))))
