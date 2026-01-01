(ns sepal.app.routes.contact.form
  (:require [sepal.app.ui.form :as ui.form]))

(defn footer-buttons []
  [[:button {:class "btn"
             :x-on:click "dirty && confirm('Are you sure you want to lose your changes?') && history.back()"}
    "Cancel"]
   [:button {:class "btn btn-primary"
             :x-on:click "$dispatch('contact-form:submit')"
             :x-bind:disabled "!valid"}
    "Save"]])

(defn form [& {:keys [action errors values]}]
  (ui.form/form
    {:id "contact-form"
     :hx-post action
     :hx-swap "none"
     :x-on:contact-form:submit.window "$el.requestSubmit()"
     :x-on:contact-form:reset.window "$el.reset()"}
    [(ui.form/anti-forgery-field)
     (ui.form/input-field :label "Name"
                          :name "name"
                          :required true
                          :value (:name values)
                          :errors (:name errors))
     (ui.form/input-field :label "Email"
                          :name "email"
                          :type "email"
                          :value (:email values)
                          :errors (:email errors))
     (ui.form/input-field :label "Address"
                          :name "address"
                          :value (:address values)
                          :errors (:address errors))
     (ui.form/input-field :label "Province"
                          :name "province"
                          :value (:province values)
                          :errors (:province errors))
     (ui.form/input-field :label "Postal Code"
                          :name "postal-code"
                          :value (:postal-code values)
                          :errors (:postal-code errors))
     (ui.form/input-field :label "Country"
                          :name "country"
                          :value (:country values)
                          :errors (:country errors))
     (ui.form/input-field :label "Phone"
                          :name "phone"
                          :value (:phone values)
                          :errors (:phone errors))
     (ui.form/input-field :label "Business Name"
                          :name "business"
                          :value (:business values)
                          :errors (:business errors))
     (ui.form/textarea-field :label "Notes"
                             :name "notes"
                             :value (:notes values)
                             :errors (:notes errors))]))
