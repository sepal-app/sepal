(ns sepal.app.routes.contact.form
  (:require [clojure.string :as str]
            [sepal.app.ui.form :as ui.form]
            [sepal.contact.interface.spec :as contact.spec]
            [sepal.i18n.interface :refer [tr]]))

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
        :title (tr "Contact")
        :hint (tr "Who this is, and how to reach them.")
        :children
        [[:div {:class "spl-form-pair"}
          (ui.form/input-field :label (tr "Name")
                               :name "name"
                               :required true
                               :value (:name values)
                               :errors (:name errors))
          (ui.form/input-field :label (tr "Business Name")
                               :name "business"
                               :value (:business values)
                               :errors (:business errors))]
         [:div {:class "spl-form-pair"}
          (ui.form/input-field :label (tr "Email")
                               :name "email"
                               :type "email"
                               :value (:email values)
                               :errors (:email errors))
          (ui.form/input-field :label (tr "Phone")
                               :name "phone"
                               :value (:phone values)
                               :errors (:phone errors))]
         (ui.form/field :label (tr "Type")
                        :name "type"
                        :errors (:type errors)
                        :input (ui.form/enum-select "type"
                                                    contact.spec/type
                                                    (:type values)
                                                    :label-fn #(tr (contact.spec/type-labels % (enum-label-fn %)))))])

      (ui.form/section
        :title (tr "Address")
        :children
        ;; The old single-line address, for a contact saved before the split.
        ;; Read-only and only rendered when there is one: no rule splits a
        ;; one-line address into street and city without being wrong on some
        ;; rows in silence, so it is left as typed rather than parsed. It still
        ;; posts its value, which is what keeps it from being dropped on save.
        (cond-> []
          (seq (:address values))
          (conj (ui.form/input-field :label (tr "Address (as first entered)")
                                     :name "address"
                                     :read-only true
                                     :value (:address values)
                                     :help (tr "Saved before addresses were split up. Fill in the fields below to replace it.")
                                     :errors (:address errors)))

          true
          (into [(ui.form/input-field :label (tr "Address line 1")
                                      :name "address1"
                                      :value (:address1 values)
                                      :errors (:address1 errors))
                 (ui.form/input-field :label (tr "Address line 2")
                                      :name "address2"
                                      :value (:address2 values)
                                      :errors (:address2 errors))
                 [:div {:class "spl-form-pair"}
                  (ui.form/input-field :label (tr "City")
                                       :name "city"
                                       :value (:city values)
                                       :errors (:city errors))
                  (ui.form/input-field :label (tr "Province / State")
                                       :name "province"
                                       :value (:province values)
                                       :errors (:province errors))]
                 [:div {:class "spl-form-pair"}
                  (ui.form/input-field :label (tr "Postal Code")
                                       :name "postal-code"
                                       :value (:postal-code values)
                                       :errors (:postal-code errors))
                  (ui.form/input-field :label (tr "Country")
                                       :name "country"
                                       :value (:country values)
                                       :errors (:country errors))]])))

      (ui.form/section
        :title (tr "Notes")
        :children
        [(ui.form/textarea-field :label (tr "Notes")
                                 :name "notes"
                                 :value (:notes values)
                                 :errors (:notes errors))])]]))
