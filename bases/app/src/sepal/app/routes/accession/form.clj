(ns sepal.app.routes.accession.form
  (:require [sepal.app.html :as html]
            [sepal.app.router :refer [url-for]]
            [sepal.app.routes.taxon.form :as taxon.form]
            [sepal.app.ui.button :as button]
            ;; [sepal.app.routes.taxon.views.form :as form]
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

    [:div {:class "mb-4"}
     [:label {:for "taxon-id"
              :class "spl-label"}
      "Taxon"
      [:div {:class "mt-1"}
       [:taxon-field {:url (url-for router :org/taxa {:org-id (:organization/id org)})
                      :taxon-id (:id values)
                      :name "taxon-id"
                      :value (:taxon-id values)
                      :initial-value (format "{\"id\": %s, \"name\": \"%s\"}"
                                             (:taxon-id values)
                                             (:taxon-name values))}]]]]
    (button/button :type "submit" :text "Save")]

   [:script {:type "module"
             :src (html/static-url "js/accession_form.ts")}]])
