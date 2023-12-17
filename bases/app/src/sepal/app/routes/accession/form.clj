(ns sepal.app.routes.accession.form
  (:require [sepal.app.html :as html]
            [sepal.app.json :as json]
            [sepal.app.router :refer [url-for]]
            [sepal.app.routes.taxon.form :as taxon.form]
            [sepal.app.routes.taxon.index :as taxon.index]
            [sepal.app.ui.button :as button]
            [sepal.app.ui.form :as form]))

(defn form [& {:keys [action errors org router values]}]
  (tap> (str "values: " values))

  [:div
   [:form {:action action
           :method "POST"
           :id "accession-form"}

    (form/anti-forgery-field)
    (form/input-field :label "Code"
                      :name "code"
                      :require true
                      :value (:code values)
                      :errors (:code errors))

    (let [url (url-for router :org/taxa {:org-id (:organization/id org)})]
      (form/field :label "Taxon"
                  :for "taxon-id"
                  :input [:select {:x-taxon-field (json/write-str {:url url})
                                   :name "taxon-id"}
                          (when (:taxon-id values)
                            [:option {:value (:taxon-id values)}
                             (:taxon-name values)])]))

    (button/button :type "submit" :text "Save")]
   [:script {:type "module"
             :src (html/static-url "js/accession_form.ts")}]])
