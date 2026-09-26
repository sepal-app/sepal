(ns sepal.app.routes.tag.form
  (:require [sepal.app.ui.form :as form]
            [sepal.i18n.interface :refer [tr]]))

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
      (form/section
        :title (tr "Tag")
        :hint (tr "What the tag is called, and what it groups.")
        :children
        [(form/input-field :label (tr "Name")
                           :name "name"
                           :required true
                           :value (:name values)
                           :errors (:name errors))
         (form/textarea-field :label (tr "Description")
                              :name "description"
                              :value (:description values)
                              :errors (:description errors))])]]))
