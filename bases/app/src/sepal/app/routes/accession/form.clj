(ns sepal.app.routes.accession.form
  (:require [sepal.app.html :as html]
            [sepal.app.json :as json]
            [sepal.app.router :refer [url-for]]
            [sepal.app.routes.org.routes :as org.routes]
            [sepal.app.ui.form :as form]))

(defn form [& {:keys [action errors org router values]}]
  [:div
   (form/form
     {:action action
      :method "POST"
      :id "accession-form"
      :x-on:accession-form:submit.window "$el.submit()"
      :x-on:accession-form:reset.window "$el.reset()"}
     [(form/anti-forgery-field)
      (form/input-field :label "Code"
                        :name "code"
                        :require true
                        :value (:code values)
                        :errors (:code errors))

      (let [url (url-for router org.routes/taxa {:org-id (:organization/id org)})]
        (form/field :label "Taxon"
                    :name "taxon-id"
                    :input [:select {:x-taxon-field (json/js {:url url})
                                     :x-validate.required true
                                     :id "taxon-id"
                                     :class "input input-bordered input-sm"
                                     :name "taxon-id"
                                     :autocomplete "off"}
                            (when (:taxon-id values)
                              [:option {:value (:taxon-id values)}
                               (:taxon-name values)])]))])
   [:script {:type "module"
             :src (html/static-url "js/accession_form.ts")}]])
