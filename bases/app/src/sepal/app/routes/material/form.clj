(ns sepal.app.routes.material.form
  (:require [sepal.app.html :as html]
            [sepal.app.json :as json]
            [sepal.app.router :refer [url-for]]
            [sepal.app.ui.button :as button]
            [sepal.app.ui.form :as form]))

(defn form [& {:keys [action errors org router values]}]
  ;; TODO: spec to validate the values
  [:div
   [:form {:action action
           :method "POST"
           :id "material-form"}

    ;; TODO: Add an organization-id field so we don't have to nest the url under the id

    (form/anti-forgery-field)

    (form/input-field :label "Code"
                      :name "code"
                      :require true
                      :value (:code values)
                      :errors (:code errors))

    (let [url (url-for router :org/accessions {:org-id (:organization/id org)})]
      (form/field :label "Accession"
                  :for "accession-id"
                  :input [:select {:x-accession-field (json/write-str {:url url})
                                   :name "accession-id"}
                          (when (:accession-id values)
                            [:option {:value (:accession-id values)}
                             (:accession-code values)])]))

    (let [url (url-for router :org/locations {:org-id (:organization/id org)})]
      (form/field :label "Location"
                  :for "location-id"
                  :input [:select {:x-location-field (json/write-str {:url url})
                                   :name "location-id"}
                          (when (:location-id values)
                            [:option {:value (:location-id values)}
                             (format "%s (%s)"
                                     (:location-code values)
                                     (:location-name values))])]))

    (button/button :type "submit" :text "Save")]

   [:script {:type "module"
             :src (html/static-url "js/material_form.ts")}]])
