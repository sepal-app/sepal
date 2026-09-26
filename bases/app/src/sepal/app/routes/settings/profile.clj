(ns sepal.app.routes.settings.profile
  (:require [failjure.core :as f]
            [sepal.app.flash :as flash]
            [sepal.app.http-response :as http]
            [sepal.app.routes.auth.routes :as auth.routes]
            [sepal.app.routes.settings.layout :as layout]
            [sepal.app.routes.settings.routes :as settings.routes]
            [sepal.app.ui.form :as ui.form]
            [sepal.i18n.interface :as i18n :refer [tr]]
            [sepal.user.interface :as user.i]
            [sepal.user.interface.activity :as user.activity]
            [sepal.validation.interface :as validation.i]
            [zodiac.core :as z]))

(defn- language-select [& {:keys [value errors]}]
  (ui.form/field
    :label (tr "Language")
    :name "language"
    :errors errors
    :help (tr "Browser default uses the language your browser asks for, or English if Sepal has no translation for it.")
    :input [:select {:name "language"
                     :id "language"
                     :class "spl-input spl-select"
                     :aria-describedby (ui.form/description-id "language")}
            [:option {:value ""} (tr "Browser default")]
            (for [locale (i18n/available-locales)]
              [:option {:value locale
                        :lang (.replace ^String locale "_" "-")
                        :selected (when (= locale value) "selected")}
               (i18n/display-name locale)])]))

(defn profile-form [& {:keys [values errors]}]
  (ui.form/form
    {:method "post"
     :action (z/url-for settings.routes/profile)}
    (ui.form/anti-forgery-field)
    (ui.form/input-field :label (tr "Full name")
                         :name "full-name"
                         :value (:full-name values)
                         :errors (:full-name errors))
    (ui.form/input-field :label (tr "Email")
                         :name "email"
                         :type "email"
                         :value (:email values)
                         :required true
                         :errors (:email errors))
    (language-select :value (:language values)
                     :errors (:language errors))
    [:div {:class "mt-4"}
     (layout/save-button (tr "Save changes"))]))

(defn page-content [& {:keys [values errors]}]
  [:div
   [:div {:class "flex justify-end mb-6"}
    [:a {:href (z/url-for auth.routes/logout)
         :class "spl-btn spl-btn--danger spl-btn--sm"}
     (tr "Logout")]]
   (profile-form :values values :errors errors)])

(defn render [& {:keys [viewer values errors flash]}]
  (layout/layout
    :viewer viewer
    :current-route settings.routes/profile
    :category (tr "Account")
    :title (tr "Profile")
    :flash flash
    :content (page-content :values values :errors errors)))

(def FormParams
  [:map {:closed true}
   ui.form/AntiForgeryField
   [:full-name {:decode/form validation.i/empty->nil} [:maybe :string]]
   [:email [:string {:min 1}]]
   [:language {:optional true :decode/form validation.i/empty->nil}
    [:maybe [:fn {:error/message "Choose a language from the list"}
             #(contains? (set (i18n/available-locales)) %)]]]])

(defn handler [{:keys [::z/context flash form-params request-method viewer]}]
  (let [{:keys [db]} context
        values {:full-name (:user/full-name viewer)
                :email (:user/email viewer)
                :language (:user/language viewer)}]
    (case request-method
      :post
      (f/attempt-all [data (validation.i/validate-form-values FormParams form-params)
                      _updated (f/try* (user.i/update! db (:user/id viewer) data))]
        (do
          (user.activity/create! db
                                 (:user/id viewer)  ;; created-by
                                 viewer             ;; user entity
                                 {})                ;; additional data (schema doesn't support full_name)
          (-> (http/see-other settings.routes/profile)
              (flash/success (tr "Profile updated successfully"))))
        (f/when-failed [e]
          (http/failure-flash e (http/see-other settings.routes/profile) (tr "Failed to update profile"))))

      (render :viewer viewer :values values :flash flash))))
