(ns sepal.app.routes.accession.form
  (:require [sepal.app.html :as html]
            [sepal.app.json :as json]
            [sepal.app.router :refer [url-for]]
            [sepal.app.ui.button :as button]
            [sepal.app.ui.form :as form]))

(defn form [& {:keys [action errors org router values]}]
  (tap> (str "values: " values))

  [:div
   [:form {:action action
           :method "POST"
           :id "accession-form"
           :class "flex flex-col gap-2"}

    (form/anti-forgery-field)
    (form/input-field :label "Code"
                      :name "code"
                      :require true
                      :value (:code values)
                      :errors (:code errors))

    (let [url (url-for router :org/taxa {:org-id (:organization/id org)})]
      (form/field :label "Taxon"
                  :for "taxon-id"
                  :input [:select {:x-taxon-field (json/js {:url url})
                                   :class "input input-bordered input-sm"
                                   :name "taxon-id"}
                          (when (:taxon-id values)
                            [:option {:value (:taxon-id values)}
                             (:taxon-name values)])]))

    [:div {:class "flex flex-row mt-4 justify-between items-center"}
     [:button {:type "submit"
               :class "btn btn-primary"}
      "Save"]]]
   [:script {:type "module"
             :src (html/static-url "js/accession_form.ts")}]])
