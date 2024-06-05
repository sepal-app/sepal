(ns sepal.app.routes.location.form
  (:require [sepal.app.ui.button :as button]
            [sepal.app.ui.form :as form]))

(defn form [& {:keys [action errors values]}]
  ;; TODO: spec to validate the values
  [:div
   [:form {:action action
           :method "POST"
           :id "location-form"}

    ;; TODO: Add an organization-id field so we don't have to nest the url under the id

    (form/anti-forgery-field)
    (form/input-field :label "Name"
                      :name "name"
                      :require true
                      :value (:name values)
                      :errors (:name errors))

    (form/input-field :label "Code"
                      :name "code"
                      :require true
                      :value (:code values)
                      :errors (:code errors))

    (form/textarea-field :label "Description"
                         :name "description"
                         :value (:description values)
                         :errors (:description errors))
    [:div {:class "flex flex-row mt-4 justify-between items-center"}
     [:button {:type "submit"
               :class "btn btn-primary"}
      "Save"]]]])
