(ns sepal.app.routes.location.form
  (:require [sepal.app.html :as html]
            [sepal.app.ui.form :as form]))

(defn form [& {:keys [action errors values]}]
  [:div
   (form/form
     {:action action
      :method "POST"
      :id "location-form"
      :x-ref "locationForm"}
     [:<>
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
                           :errors (:description errors))])
   [:script {:type "module"
             :src (html/static-url "js/location_form.ts")}]])
