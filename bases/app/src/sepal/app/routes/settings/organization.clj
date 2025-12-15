(ns sepal.app.routes.settings.organization
  (:require [sepal.app.flash :as flash]
            [sepal.app.http-response :as http]
            [sepal.app.routes.settings.layout :as layout]
            [sepal.app.routes.settings.routes :as settings.routes]
            [sepal.app.ui.form :as form]
            [sepal.error.interface :as error.i]
            [sepal.settings.interface :as settings.i]
            [sepal.settings.interface.activity :as settings.activity]
            [sepal.validation.interface :as validation.i]
            [zodiac.core :as z]))

(defn org-form [& {:keys [values errors]}]
  (form/form
    {:method "post"
     :action (z/url-for settings.routes/organization)}
    (form/anti-forgery-field)

    [:div {:class "space-y-8"}
     [:div
      [:h3 {:class "text-lg font-medium mb-4"} "Organization Identity"]
      (form/input-field :label "Long name"
                        :name "long_name"
                        :value (:long_name values)
                        :errors (:long_name errors))
      (form/input-field :label "Short name"
                        :name "short_name"
                        :value (:short_name values)
                        :errors (:short_name errors))
      (form/input-field :label "Abbreviation"
                        :name "abbreviation"
                        :value (:abbreviation values)
                        :errors (:abbreviation errors))]

     [:div
      [:h3 {:class "text-lg font-medium mb-4"} "Contact Information"]
      (form/input-field :label "Email"
                        :name "email"
                        :type "email"
                        :value (:email values)
                        :errors (:email errors))
      (form/input-field :label "Phone"
                        :name "phone"
                        :value (:phone values)
                        :errors (:phone errors))
      (form/input-field :label "Website"
                        :name "website"
                        :type "url"
                        :value (:website values)
                        :errors (:website errors))]

     [:div
      [:h3 {:class "text-lg font-medium mb-4"} "Address"]
      (form/input-field :label "Street address"
                        :name "address_street"
                        :value (:address_street values)
                        :errors (:address_street errors))
      [:div {:class "grid grid-cols-2 gap-4"}
       (form/input-field :label "City"
                         :name "address_city"
                         :value (:address_city values)
                         :errors (:address_city errors))
       (form/input-field :label "Postal code"
                         :name "address_postal_code"
                         :value (:address_postal_code values)
                         :errors (:address_postal_code errors))]
      (form/input-field :label "Country"
                        :name "address_country"
                        :value (:address_country values)
                        :errors (:address_country errors))]]

    [:div {:class "mt-4"}
     (layout/save-button "Save changes")]))

(defn render [& {:keys [viewer values errors]}]
  (layout/layout
    :viewer viewer
    :current-route settings.routes/organization
    :category "Organization"
    :title "General"
    :content (org-form :values values :errors errors)))

(def FormParams
  [:map {:closed true}
   form/AntiForgeryField
   [:long_name {:decode/form validation.i/empty->nil} [:maybe :string]]
   [:short_name {:decode/form validation.i/empty->nil} [:maybe :string]]
   [:abbreviation {:decode/form validation.i/empty->nil} [:maybe :string]]
   [:email {:decode/form validation.i/empty->nil} [:maybe :string]]
   [:phone {:decode/form validation.i/empty->nil} [:maybe :string]]
   [:website {:decode/form validation.i/empty->nil} [:maybe :string]]
   [:address_street {:decode/form validation.i/empty->nil} [:maybe :string]]
   [:address_city {:decode/form validation.i/empty->nil} [:maybe :string]]
   [:address_postal_code {:decode/form validation.i/empty->nil} [:maybe :string]]
   [:address_country {:decode/form validation.i/empty->nil} [:maybe :string]]])

(def form-key->setting-key
  {:long_name "organization.long_name"
   :short_name "organization.short_name"
   :abbreviation "organization.abbreviation"
   :email "organization.email"
   :phone "organization.phone"
   :website "organization.website"
   :address_street "organization.address_street"
   :address_city "organization.address_city"
   :address_postal_code "organization.address_postal_code"
   :address_country "organization.address_country"})

(defn settings->form-values [settings]
  (into {} (map (fn [[form-key setting-key]]
                  [form-key (get settings setting-key)])
                form-key->setting-key)))

(defn form-values->settings [form-values]
  (into {} (map (fn [[form-key setting-key]]
                  [setting-key (get form-values form-key)])
                form-key->setting-key)))

(defn handler [{:keys [::z/context form-params request-method viewer]}]
  (let [{:keys [db]} context
        current-settings (settings.i/get-values db "organization")
        values (settings->form-values current-settings)]
    (case request-method
      :post
      (let [result (validation.i/validate-form-values FormParams form-params)]
        (if (error.i/error? result)
          (http/validation-errors (validation.i/humanize result))
          (let [new-settings (form-values->settings result)]
            (settings.i/set-values! db new-settings)
            (settings.activity/create! db
                                       settings.activity/updated
                                       (:user/id viewer)
                                       {:changes new-settings})
            (-> (http/see-other settings.routes/organization)
                (flash/success "Organization settings updated successfully")))))

      (render :viewer viewer :values values))))
