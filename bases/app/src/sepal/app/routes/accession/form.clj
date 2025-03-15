(ns sepal.app.routes.accession.form
  (:require [sepal.app.html :as html]
            [sepal.app.json :as json]
            [sepal.app.routes.taxon.routes :as taxon.routes]
            [sepal.app.ui.form :as form]
            [zodiac.core :as z]))

(defn form [& {:keys [action errors values]}]
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

      (let [url (z/url-for taxon.routes/index)]
        (form/field :label "Taxon"
                    :name "taxon-id"
                    :input [:select {:x-taxon-field (json/js {:url url})
                                     :id "taxon-id"
                                     :class "select select-bordered select-md w-full max-w-xs px-2"
                                     :required true
                                     :name "taxon-id"
                                     :autocomplete "off"}
                            (when (:taxon-id values)
                              [:option {:value (:taxon-id values)}
                               (:taxon-name values)])]))])
   [:script {:type "module"
             :src (html/static-url "app/routes/accession/form.ts")}]])
