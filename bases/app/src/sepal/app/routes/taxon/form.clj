(ns sepal.app.routes.taxon.form
  (:require [sepal.app.html :as html]
            [sepal.app.json :as json]
            [sepal.app.router :refer [url-for]]
            [sepal.app.routes.taxon.form :as taxon.form]
            [sepal.app.ui.button :as button]
            [sepal.app.ui.form :as form]))

(def ranks
  ["class"
   "family"
   "form"
   "genus"
   "kingdom"
   "order"
   "phylum"
   "section"
   "series"
   "species"
   "subclass"
   "subfamily"
   "subform"
   "subgenus"
   "subsection"
   "subseries"
   "subspecies"
   "subtribe"
   "subvariety"
   "superorder"
   "tribe"
   "variety"])

(defn form [& {:keys [action errors org router values]}]
  (tap> (str "values: " values))
  [:div
   [:form {:action action
           :method "POST"
           :id "taxon-form"}

    (form/anti-forgery-field)
    (form/input-field :label "Name"
                      :name "name"
                      :require true
                      :value (:name values)
                      :errors (:name errors))

    (let [url (url-for router :org/taxa {:org-id (:organization/id org)})]
      (form/field :label "Parent"
                  :for "parent-id"
                  :input [:select {:x-taxon-field (json/js {:url url})
                                   :name "parent-id"
                                   :autocomplete= "off"}
                          (when (:parent-id values)
                            [:option {:value (:parent-id values)}
                             (:parent-name values)])]))

    (form/field :label "Rank"
                :for "rank"
                :input [:select {:name "rank"
                                 :autocomplete= "off"
                                 :id "taxon-rank"
                                 :value (:rank values)}
                        [:<>
                         (for [rank ranks]
                           [:option {:value rank
                                     :selected (when (= (:rank values) rank)
                                                 "selected")}
                            rank])]])

    (button/button :type "submit" :text "Save")]

   [:script {:type "module"
             :src (html/static-url "js/taxon_form.ts")}]])
