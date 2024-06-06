(ns sepal.app.routes.location.form
  (:require [sepal.app.ui.form :as form]))

(defn form [& {:keys [action errors values]}]
  ;; TODO: spec to validate the values
  (form/form
   {:action action
    :method "POST"
    :id "location-form"}
   [:<>
    ;; TODO: Add an organization-id field so we don't have to nest the url under the id

    (form/anti-forgery-field)
    (form/input-field :label "Name"
                      :name "name"
                      :required true
                      :value (:name values)
                      :errors (:name errors))

    (form/input-field :label "Code"
                      :name "code"
                      :required true
                      :value (:code values)
                      :errors (:code errors))

    (form/textarea-field :label "Description"
                         :name "description"
                         :value (:description values)
                         :errors (:description errors))
    [:div {:class "spl-btn-grp mt-4"}
     (form/button "Save")]]))
