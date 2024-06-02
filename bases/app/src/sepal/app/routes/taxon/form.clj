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

(defn form [& {:keys [action errors org read-only router values]}]
  [:div
   [:form {:action action
           :method "POST"
           :id "taxon-form"}
    (form/anti-forgery-field)
    (form/hidden-field :name "organization-id"
                       :value (:organization-id values))

    (form/input-field :label "Name"
                      :name "name"
                      :require true
                      :read-only read-only
                      :value (:name values)
                      :errors (:name errors))

    (if read-only
      (form/input-field :label "Parent"
                        :name "parent-id"
                        :require true
                        :read-only read-only
                        :value (:parent-name values))

      (let [url (url-for router :org/taxa {:org-id (:organization/id org)})]
        (form/field :label "Parent"
                    :for "parent-id"
                    ;; :readonly read-only
                    :input [:select {:x-taxon-field (json/js {:url url})
                                     :name "parent-id"
                                     :read-only read-only
                                     :autocomplete= "off"}
                            (when (:parent-id values)
                              [:option {:value (:parent-id values)}
                               (:parent-name values)])])))

    (if read-only
      (form/input-field :label "Rank"
                        :name "rank"
                        :read-only read-only
                        :value (:rank values))
      (form/field :label "Rank"
                  :for "rank"
                  :input [:select {:name "rank"
                                   :autocomplete "off"
                                   :id "taxon-rank"
                                   :read-only read-only
                                   :value (:rank values)}
                          [:<>
                           (for [rank ranks]
                             [:option {:value rank
                                       :selected (when (= rank (some-> values :rank name))
                                                   "selected")}
                              rank])]]))

    ;; TODO: When creating a new taxon we should havea "Create" button to only
    ;; save when all of the required fields are filled in
    (button/button :type "submit" :text "Save")]

   [:script {:type "module"
             :src (html/static-url "js/taxon_form.ts")}]])
