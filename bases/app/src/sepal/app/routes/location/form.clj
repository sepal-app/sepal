(ns sepal.app.routes.location.form
  (:require [sepal.app.ui.form :as form]))

(defn footer-buttons []
  (form/footer-buttons :form-event "location-form" :on-cancel :back))

(defn code-input
  "The Code control on its own, so a save refused for a taken code can swap the
  field back carrying the error rather than reloading the page and losing what
  was typed."
  [& {:keys [value errors]}]
  (form/input-field :label "Code"
                    :name "code"
                    :required true
                    :value value
                    :errors errors))

(defn form [& {:keys [action errors values]}]
  [:div
   (form/form
     {:id "location-form"
      :hx-post action
      :hx-swap "none"
      :x-on:location-form:submit.window "$el.requestSubmit()"
      :x-on:location-form:reset.window "$el.reset()"}
     [(form/anti-forgery-field)
      [:div {:class "spl-form"}
       (form/section
         :title "Details"
         :hint "What this place is called and how it is referred to."
         :children
         [[:div {:class "spl-form-pair"}
           (form/input-field :label "Name"
                             :name "name"
                             :required true
                             :value (:name values)
                             :errors (:name errors))
           (code-input :value (:code values) :errors (:code errors))]
          (form/textarea-field :label "Description"
                               :name "description"
                               :value (:description values)
                               :errors (:description errors))])]])])
