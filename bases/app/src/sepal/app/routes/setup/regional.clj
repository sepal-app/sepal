(ns sepal.app.routes.setup.regional
  (:require [clojure.string :as str]
            [sepal.app.flash :as flash]
            [sepal.app.html :as html]
            [sepal.app.http-response :as http]
            [sepal.app.routes.setup.layout :as layout]
            [sepal.app.routes.setup.routes :as setup.routes]
            [sepal.app.routes.setup.shared :as setup.shared]
            [sepal.app.ui.form :as form]
            [sepal.error.interface :as error.i]
            [sepal.settings.interface :as settings.i]
            [sepal.validation.interface :as validation.i]
            [zodiac.core :as z])
  (:import [java.time ZoneId ZonedDateTime]))

(def FormParams
  [:map {:closed true}
   form/AntiForgeryField
   [:timezone [:string {:min 1 :error/message "Please select a timezone"}]]])

(defn timezone-options
  "Returns all canonical IANA timezone options with UTC offset labels."
  []
  (->> (ZoneId/getAvailableZoneIds)
       (filter #(re-matches #"^[A-Z][a-z]+/[A-Za-z_/]+" %))
       (remove #(str/starts-with? % "Etc/"))
       sort
       (mapv (fn [zone-id]
               (let [zone (ZoneId/of zone-id)
                     offset (.getOffset (ZonedDateTime/now zone))]
                 {:value zone-id
                  :label (format "(UTC%s) %s" offset zone-id)})))))

(defn timezone-select [& {:keys [value errors]}]
  (form/field
    :label "Timezone"
    :name "timezone"
    :errors errors
    :input [:select {:name "timezone"
                     :id "timezone"
                     :x-timezone-field true
                     :required true}
            [:option {:value ""} "Select a timezone..."]
            (for [{opt-value :value opt-label :label} (timezone-options)]
              [:option {:value opt-value
                        :selected (= opt-value value)}
               opt-label])]))

(defn regional-form [& {:keys [values errors]}]
  (form/form
    {:method "post"
     :action (z/url-for setup.routes/regional)
     :hx-post (z/url-for setup.routes/regional)
     :hx-swap "none"}
    (form/anti-forgery-field)
    (timezone-select :value (:timezone values)
                     :errors (:timezone errors))
    ;; Submit button inside the form
    [:div {:class "flex justify-between mt-6"}
     [:a {:href (z/url-for setup.routes/organization)
          :class "btn btn-ghost"}
      "← Back"]
     [:button {:type "submit"
               :class "btn btn-primary"}
      "Next →"]]))

(defn render [& {:keys [values errors flash-messages]}]
  (layout/layout
    :current-step 4
    :flash-messages flash-messages
    :content
    [:div {:class "card bg-base-100 border border-base-300 shadow-sm w-full max-w-2xl"}
     [:div {:class "card-body"}
      [:h2 {:class "card-title text-2xl mb-4"} "Regional Settings"]
      [:p {:class "mb-4 text-base-content/70"}
       "Select your organization's timezone. All timestamps in Sepal will be displayed in this timezone."]
      (regional-form :values values :errors errors)]]))

(defn handler [{:keys [::z/context flash form-params request-method]}]
  (let [{:keys [db]} context
        current-timezone (or (settings.i/get-value db "organization.timezone") "UTC")
        values {:timezone current-timezone}]

    (case request-method
      :post
      (let [;; Default empty timezone to UTC before validation
            form-params (update form-params "timezone" #(if (str/blank? %) "UTC" %))
            result (validation.i/validate-form-values FormParams form-params)]
        (if (error.i/error? result)
          (http/validation-errors (validation.i/humanize result))
          (do
            (settings.i/set-value! db "organization.timezone" (:timezone result))
            (setup.shared/set-current-step! db 5)
            (-> (http/hx-redirect setup.routes/review)
                (flash/success "Timezone saved")))))

      ;; GET
      (do
        (setup.shared/set-current-step! db 4)
        (html/render-page (render :values values
                                  :flash-messages (:messages flash)))))))
