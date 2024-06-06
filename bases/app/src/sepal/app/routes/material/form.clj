(ns sepal.app.routes.material.form
  (:require [sepal.app.html :as html]
            [sepal.app.json :as json]
            [sepal.app.router :refer [url-for]]
            [sepal.app.ui.button :as button]
            [sepal.app.ui.form :as form]))

(def FormValues
  [:map
   ;; TODO: If there is an id value then require the accession code, taxon.name
   ;; location code and location name
   [:code :string]
   [:accession-id :string]
   [:location-id :string]])

(defn form [& {:keys [action errors org router values]}]
  ;; TODO: spec to validate the values
  [:div
   (form/form
    {:action action
     :method "POST"
     :id "material-form"}
    [:<>
     ;; TODO: Add an organization-id field so we don't have to nest the url under the id
     ;;
     (form/anti-forgery-field)

     (form/field :label "Code"
                 :name "code"
                 :errors (:code errors)
                 :input [:input {:autocomplete "off"
                                 :class "input input-bordered input-sm w-full max-w-xs bg-white"
                                 :x-validate.required true
                                 :id "code"
                                 :name "code"
                                 :type "text"
                                 :value (:code values)}])

     (let [url (url-for router :org/accessions {:org-id (:organization/id org)})]
       (form/field :label "Accession"
                   :name "accession-id"
                   :input [:select {:x-accession-field (json/js {:url url})
                                    :x-validate.required true
                                    :placeholder "Required"
                                    :name "accession-id"
                                    :id "accession-id"
                                    :class "input input-bordered input-sm"}
                           (when (:accession-id values)
                             [:option {:value (:accession-id values)}
                              (:accession-code values)])]))

     (let [url (url-for router :org/locations {:org-id (:organization/id org)})]
       (form/field :label "Location"
                   :name "location-id"
                   :input [:select {:x-location-field (json/js {:url url})
                                    :x-validate.required true
                                    :name "location-id"
                                    :id "location-id"
                                    :class "input input-bordered input-sm"}
                           (when (:location-id values)
                             [:option {:value (:location-id values)}
                              (format "%s (%s)"
                                      (:location-code values)
                                      (:location-name values))])]))

     [:div {:class "spl-btn-grp mt-4"}
      (form/button "Save")]])

   [:script {:type "module"
             :src (html/static-url "js/material_form.ts")}]])
