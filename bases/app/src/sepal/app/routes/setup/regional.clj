(ns sepal.app.routes.setup.regional
  (:require [clojure.string :as str]
            [failjure.core :as f]
            [sepal.app.datetime :as datetime]
            [sepal.app.flash :as flash]
            [sepal.app.html :as html]
            [sepal.app.http-response :as http]
            [sepal.app.routes.setup.layout :as layout]
            [sepal.app.routes.setup.routes :as setup.routes]
            [sepal.app.routes.setup.shared :as setup.shared]
            [sepal.app.ui.combobox :as combobox]
            [sepal.app.ui.form :as form]
            [sepal.error.interface :as error.i]
            [sepal.i18n.interface :refer [N_ tr]]
            [sepal.settings.interface :as settings.i]
            [sepal.validation.interface :as validation.i]
            [zodiac.core :as z]))

(def FormParams
  [:map {:closed true}
   form/AntiForgeryField
   [:timezone [:fn {:error/message (N_ "Please select a timezone")} datetime/valid-timezone?]]])

(defn timezone-select [& {:keys [value errors]}]
  (combobox/combobox
    :name "timezone"
    :label (tr "Timezone")
    :errors errors
    ;; Four hundred names that never change, so they travel with the page and
    ;; the field filters them here. No request, and no minimum before it will
    ;; show you anything.
    :items (for [{opt-value :value opt-label :label} (datetime/timezone-options)]
             {:id opt-value :text opt-label})
    :selected (when-let [match (first (filter #(= value (:value %))
                                              (datetime/timezone-options)))]
                {:id (:value match) :text (:label match)})))

(defn regional-form [& {:keys [values errors]}]
  (form/form
    {:method "post"
     :action (z/url-for setup.routes/regional)}
    (form/anti-forgery-field)
    (timezone-select :value (:timezone values)
                     :errors (:timezone errors))
    ;; Submit button inside the form
    [:div {:class "flex justify-between mt-6"}
     [:a {:href (z/url-for setup.routes/organization)
          :class "spl-btn spl-btn--ghost"}
      (tr "← Back")]
     [:button {:type "submit"
               :class "spl-btn spl-btn--primary"}
      (tr "Next →")]]))

(defn render [& {:keys [values errors flash-messages]}]
  (layout/layout
    :current-step 4
    :flash-messages flash-messages
    :content
    [:div {:class "spl-card bg-surface border border-border shadow-sm w-full max-w-2xl"}
     [:div {:class "spl-card-body"}
      [:h2 {:class "spl-card-title mb-4"} (tr "Regional Settings")]
      [:p {:class "mb-4 text-text-muted"}
       (tr "Select your organization's timezone. All timestamps in Sepal will be displayed in this timezone.")]
      (regional-form :values values :errors errors)]]))

(defn handler [{:keys [::z/context flash form-params request-method]}]
  (let [{:keys [db]} context
        current-timezone (or (settings.i/get-value db "organization.timezone") "UTC")
        values {:timezone current-timezone}]

    (case request-method
      :post
      ;; Default empty timezone to UTC before validation
      (let [form-params (update form-params "timezone" #(if (str/blank? %) "UTC" %))]
        (f/attempt-all [data (validation.i/validate-form-values FormParams form-params)
                        _saved (f/try* (do
                                         (settings.i/set-value! db "organization.timezone" (:timezone data))
                                         (setup.shared/set-current-step! db 5)))]
          (-> (http/see-other setup.routes/taxonomy)
              (flash/success (tr "Timezone saved")))
          (f/when-failed [e]
            (html/render-page (render :values form-params
                                      :errors (error.i/humanize e))))))

      ;; GET
      (do
        (setup.shared/set-current-step! db 4)
        (html/render-page (render :values values
                                  :flash-messages (:messages flash)))))))
