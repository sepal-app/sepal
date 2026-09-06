(ns sepal.app.routes.tag.form
  (:require [sepal.app.ui.form :as form]))

(defn footer-buttons []
  (form/footer-buttons :form-event "tag-form" :on-cancel :back))

(defn form [& {:keys [action errors values]}]
  (form/form
    {:id "tag-form"
     :hx-post action
     :hx-swap "none"
     :x-on:tag-form:submit.window "$el.requestSubmit()"
     :x-on:tag-form:reset.window "$el.reset()"}
    [(form/anti-forgery-field)
     [:div {:class "spl-form"}
      (form/input-field :label "Name"
                        :name "name"
                        :required true
                        :value (:name values)
                        :errors (:name errors))
      (form/textarea-field :label "Description"
                           :name "description"
                           :value (:description values)
                           :errors (:description errors))]]))
