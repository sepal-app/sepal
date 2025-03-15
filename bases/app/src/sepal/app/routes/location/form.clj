(ns sepal.app.routes.location.form
  (:require [sepal.app.html :as html]
            [sepal.app.ui.form :as form]))

(defn footer-buttons []
  [[:button {:class "btn btn-primary"
             :x-on:click "$dispatch('location-form:submit')"
             :x-bind:disabled "!valid"}
    "Save"]
   [:button {:class "btn btn-secondary"
             :x-on:click "dirty && confirm('Are you sure you want to lose your changes?') && history.back()"}
    "Cancel"]])

(defn form [& {:keys [action errors values]}]
  [:div
   (form/form
     {:action action
      :method "POST"
      :id "location-form"
      :x-on:location-form:submit.window "$el.submit()"
      :x-on:location-form:reset.window "$el.reset()"}
     [(form/anti-forgery-field)
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
                           :errors (:description errors))])])
