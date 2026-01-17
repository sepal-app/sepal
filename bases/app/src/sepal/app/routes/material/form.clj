(ns sepal.app.routes.material.form
  (:require [sepal.app.html :as html]
            [sepal.app.json :as json]
            [sepal.app.routes.accession.routes :as accession.routes]
            [sepal.app.routes.location.routes :as location.routes]
            [sepal.app.ui.form :as form]
            [sepal.material.interface.spec :as material.spec]
            [zodiac.core :as z]))

#_(def FormValues
    [:map
   ;; TODO: If there is an id value then require the accession code, taxon.name
   ;; location code and location name
     [:code :string]
     [:accession-id :string]
     [:location-id :string]])

;; (def types ["Plant"])

(defn form [& {:keys [action errors values]}]
  (let [statuses (->> material.spec/status rest (mapv name))
        types (->> material.spec/type rest (mapv name))]
    [:div
     (form/form
       {:id "material-form"
        :hx-post action
        :hx-swap "none"
        :x-on:material-form:submit.window "$el.requestSubmit()"
        :x-on:material-form:reset.window "$el.reset()"}
       [(form/anti-forgery-field)
        (form/field :label "Code"
                    :name "code"
                    :errors (:code errors)
                    :input [:input {:autocomplete "off"
                                    :class "input input-bordered w-full max-w-xs"
                                    :placeholder "Required"
                                    :required true
                                    :id "code"
                                    :name "code"
                                    :type "text"
                                    :value (:code values)}])
        (let [url (z/url-for accession.routes/index)]
          (form/field :label "Accession"
                      :name "accession-id"
                      :errors (:accession-id errors)
                      :input [:select {:x-accession-field (json/js {:url url})
                                       :placeholder "Required"
                                       :name "accession-id"
                                       :id "accession-id"
                                       :required true}
                              (when (:accession-id values)
                                [:option {:value (:accession-id values)}
                                 (:accession-code values)])]))
        (let [url (z/url-for location.routes/index)]
          (form/field :label "Location"
                      :name "location-id"
                      :errors (:location-id errors)
                      :input [:select {:x-location-field (json/js {:url url})
                                       :placeholder "Required"
                                       :required true
                                       :name "location-id"
                                       :id "location-id"}
                              (when (:location-id values)
                                [:option {:value (:location-id values)}
                                 (format "%s (%s)"
                                         (:location-code values)
                                         (:location-name values))])]))
        (form/field :label "Quantity"
                    :name "quantity"
                    :errors (:quantity errors)
                    :input [:input {:autocomplete "off"
                                    :class "input input-bordered w-full max-w-xs"
                                    :id "quantity"
                                    :name "quantity"
                                    :type "number"
                                    :min 1
                                    :required true
                                    :value (or (:quantity values) 1)}])
        (form/field :label "Status"
                    :name "status"
                    :errors (:status errors)
                    :input [:select {:name "status"
                                     :x-material-status-field true
                                     :autocomplete "off"
                                     :id "status"
                                     :required true
                                     :value (:status values)}
                            [(for [status statuses]
                               [:option {:value status
                                         :selected (when (= status (some-> values :status name))
                                                     "selected")}
                                status])]])
        (form/field :label "Type"
                    :name "type"
                    :errors (:type errors)
                    :input [:select {:name "type"
                                     :x-material-type-field true
                                     :autocomplete "off"
                                     :id "type"
                                     :required true
                                     :value (:type values)}
                            [(for [type types]
                               [:option {:value type
                                         :selected (when (= type (some-> values :type name))
                                                     "selected")}
                                type])]])

        #_(form/field :label "Memorial"
                      :name "type"
                      :input [:checkbox {:name "memorial"
                                         ;; :x-material-type-field true
                                         ;; :class "select select-bordered select-sm w-full max-w-xs px-2"
                                         ;; :autocomplete "off"
                                         :id "memorial"
                                         :x-validate.required true
                                         :value (:memorial values)}])])
     [:script {:type "module"
               :src (html/static-url "app/routes/material/form.ts")}]]))
