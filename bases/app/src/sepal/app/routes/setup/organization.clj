(ns sepal.app.routes.setup.organization
  (:require [sepal.app.flash :as flash]
            [sepal.app.html :as html]
            [sepal.app.http-response :as http]
            [sepal.app.routes.setup.layout :as layout]
            [sepal.app.routes.setup.routes :as setup.routes]
            [sepal.app.routes.setup.shared :as setup.shared]
            [sepal.app.ui.form :as form]
            [sepal.error.interface :as error.i]
            [sepal.settings.interface :as settings.i]
            [sepal.validation.interface :as validation.i]
            [zodiac.core :as z]))

(def FormParams
  [:map {:closed true}
   form/AntiForgeryField
   [:long_name [:string {:min 1 :error/message "Organization name is required"}]]
   [:short_name {:decode/form validation.i/empty->nil} [:maybe :string]]
   [:abbreviation {:decode/form validation.i/empty->nil} [:maybe :string]]
   [:email {:decode/form validation.i/empty->nil} [:maybe :string]]
   [:phone {:decode/form validation.i/empty->nil} [:maybe :string]]])

(def form-key->setting-key
  {:long_name "organization.long_name"
   :short_name "organization.short_name"
   :abbreviation "organization.abbreviation"
   :email "organization.email"
   :phone "organization.phone"})

(defn settings->form-values [settings]
  (into {} (map (fn [[form-key setting-key]]
                  [form-key (get settings setting-key)])
                form-key->setting-key)))

(defn form-values->settings [form-values]
  (into {} (keep (fn [[form-key setting-key]]
                   (when-let [v (get form-values form-key)]
                     [setting-key v]))
                 form-key->setting-key)))

(defn org-form [& {:keys [values errors]}]
  (form/form
    {:method "post"
     :action (z/url-for setup.routes/organization)}
    (form/anti-forgery-field)

    [:div {:class "space-y-4"}
     (form/input-field :label "Organization name"
                       :name "long_name"
                       :value (:long_name values)
                       :errors (:long_name errors)
                       :required true
                       :input-attrs {:placeholder "e.g., Royal Botanic Gardens, Kew"})
     (form/input-field :label "Short name"
                       :name "short_name"
                       :value (:short_name values)
                       :errors (:short_name errors)
                       :input-attrs {:placeholder "e.g., Kew Gardens"})
     (form/input-field :label "Abbreviation"
                       :name "abbreviation"
                       :value (:abbreviation values)
                       :errors (:abbreviation errors)
                       :input-attrs {:placeholder "e.g., RBG Kew"})
     (form/input-field :label "Contact email"
                       :name "email"
                       :type "email"
                       :value (:email values)
                       :errors (:email errors))
     (form/input-field :label "Contact phone"
                       :name "phone"
                       :value (:phone values)
                       :errors (:phone errors))]

    ;; Submit button inside the form
    [:div {:class "flex justify-between mt-6"}
     [:a {:href (z/url-for setup.routes/server)
          :class "btn btn-ghost"}
      "← Back"]
     [:button {:type "submit"
               :class "btn btn-primary"}
      "Next →"]]))

(defn render [& {:keys [values errors flash-messages]}]
  (layout/layout
    :current-step 3
    :flash-messages flash-messages
    :content
    [:div {:class "card bg-base-100 border border-base-300 shadow-sm w-full max-w-2xl"}
     [:div {:class "card-body"}
      [:h2 {:class "card-title text-2xl mb-4"} "Organization Information"]
      [:p {:class "mb-4 text-base-content/70"}
       "Tell us about your organization. You can update these settings later."]
      (org-form :values values :errors errors)]]))

(defn handler [{:keys [::z/context flash form-params request-method]}]
  (let [{:keys [db]} context
        current-settings (settings.i/get-values db "organization")
        values (settings->form-values current-settings)]

    (case request-method
      :post
      (let [result (validation.i/validate-form-values FormParams form-params)]
        (if (error.i/error? result)
          (html/render-page (render :values form-params
                                    :errors (validation.i/humanize result)))
          (let [new-settings (form-values->settings result)]
            (when (seq new-settings)
              (settings.i/set-values! db new-settings))
            (setup.shared/set-current-step! db 4)
            (-> (http/see-other setup.routes/regional)
                (flash/success "Organization information saved")))))

      ;; GET
      (do
        (setup.shared/set-current-step! db 3)
        (html/render-page (render :values values
                                  :flash-messages (:messages flash)))))))
