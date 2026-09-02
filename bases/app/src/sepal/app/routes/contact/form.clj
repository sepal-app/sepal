(ns sepal.app.routes.contact.form
  (:require [clojure.string :as str]
            [sepal.app.ui.form :as ui.form]
            [sepal.contact.interface.spec :as contact.spec]))

(defn enum-label-fn [v]
  (-> v
      (name)
      (str/replace "_" " ")
      (str/capitalize)))

(defn footer-buttons []
  (ui.form/footer-buttons :form-event "contact-form" :on-cancel :back))

(defn form [& {:keys [action errors values]}]
  (ui.form/form
    {:id "contact-form"
     :hx-post action
     :hx-swap "none"
     :x-on:contact-form:submit.window "$el.requestSubmit()"
     :x-on:contact-form:reset.window "$el.reset()"}
    [(ui.form/anti-forgery-field)
     [:div {:class "spl-form"}
      (ui.form/section
        :title "Contact"
        :hint "Who this is, and how to reach them."
        :children
        [[:div {:class "spl-form-pair"}
          (ui.form/input-field :label "Name"
                               :name "name"
                               :required true
                               :value (:name values)
                               :errors (:name errors))
          (ui.form/input-field :label "Business Name"
                               :name "business"
                               :value (:business values)
                               :errors (:business errors))]
         [:div {:class "spl-form-pair"}
          (ui.form/input-field :label "Email"
                               :name "email"
                               :type "email"
                               :value (:email values)
                               :errors (:email errors))
          (ui.form/input-field :label "Phone"
                               :name "phone"
                               :value (:phone values)
                               :errors (:phone errors))]
         (ui.form/field :label "Type"
                        :name "type"
                        :errors (:type errors)
                        :input (ui.form/enum-select "type"
                                                    contact.spec/type
                                                    (:type values)
                                                    :label-fn enum-label-fn))])

      (ui.form/section
        :title "Address"
        :children
        [(ui.form/input-field :label "Address"
                              :name "address"
                              :value (:address values)
                              :errors (:address errors))
         [:div {:class "spl-form-pair"}
          (ui.form/input-field :label "Province"
                               :name "province"
                               :value (:province values)
                               :errors (:province errors))
          (ui.form/input-field :label "Postal Code"
                               :name "postal-code"
                               :value (:postal-code values)
                               :errors (:postal-code errors))]
         (ui.form/input-field :label "Country"
                              :name "country"
                              :value (:country values)
                              :errors (:country errors))])

      (ui.form/section
        :title "Notes"
        :children
        [(ui.form/textarea-field :label "Notes"
                                 :name "notes"
                                 :value (:notes values)
                                 :errors (:notes errors))])]]))
