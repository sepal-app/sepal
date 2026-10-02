(ns sepal.app.routes.location.form
  (:require [sepal.app.routes.location.routes :as location.routes]
            [sepal.app.ui.combobox :as combobox]
            [sepal.app.ui.form :as form]
            [sepal.app.ui.page :as ui.page]
            [sepal.i18n.interface :refer [tr trc]]
            [zodiac.core :as z]))

(defn footer-buttons []
  (form/footer-buttons :form-event "location-form" :on-cancel :back))

(defn code-input
  "The Code control on its own, so a save refused for a taken code can swap the
  field back carrying the error rather than reloading the page and losing what
  was typed."
  [& {:keys [value errors]}]
  (form/input-field :label (trc "location" "Code")
                    :name "code"
                    :required true
                    :value value
                    :errors errors))

(defn form [& {:keys [action errors values]}]
  [:div
   (form/form
     (merge ui.page/region-swap
            {:id "location-form"
             :hx-post action
             :x-on:location-form:submit.window "$el.requestSubmit()"
             :x-on:location-form:reset.window "$el.reset()"})
     [(form/anti-forgery-field)
      [:div {:class "spl-form"}
       (form/section
         :title (tr "Details")
         :hint (tr "What this place is called and how it is referred to.")
         :children
         [[:div {:class "spl-form-pair"}
           (form/input-field :label (tr "Name")
                             :name "name"
                             :required true
                             :value (:name values)
                             :errors (:name errors))
           (code-input :value (:code values) :errors (:code errors))]
          (form/textarea-field :label (tr "Description")
                               :name "description"
                               :value (:description values)
                               :errors (:description errors))
          (combobox/combobox
            :name "parent-id"
            :label (trc "location" "Parent")
            :help (tr "The location this one sits inside, if any.")
            ;; Editing leaves out this location and everything below it.
            :url (if-let [id (:id values)]
                   (z/url-for location.routes/index nil {:exclude id})
                   (z/url-for location.routes/index))
            :errors (:parent-id errors)
            :selected (when (:parent-id values)
                        {:id (:parent-id values)
                         :text (format "%s (%s)" (:parent-code values) (:parent-name values))}))])]])])
