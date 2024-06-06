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
   (form/form
    {:action action
     :method "POST"
     :id "taxon-form"}
    [:<>
     (form/anti-forgery-field)
     (form/hidden-field :name "organization-id"
                        :value (:organization-id values))

     (form/input-field :label "Name"
                       :name "name"
                       :required true
                       :read-only read-only
                       :value (:name values)
                       :errors (:name errors))

     (form/input-field :label "Author"
                       :name "author"
                       :read-only read-only
                       :value (:author values)
                       :errors (:author errors))

     (if read-only
       (form/input-field :label "Parent"
                         :name "parent-id"
                         :required true
                         :read-only read-only
                         :value (:parent-name values))

       (let [url (url-for router :org/taxa {:org-id (:organization/id org)})]
         (form/field :label "Parent"
                     :name "parent-id"
                     ;; :readonly read-only
                     :input [:select {:x-taxon-field (json/js {:url url})
                                      :class "input input-bordered input-sm"
                                      :name "parent-id"
                                      :id "parent-id"
                                      :x-validate.required true
                                      :read-only read-only
                                      :autocomplete "off"}
                             (when (:parent-id values)
                               [:option {:value (:parent-id values)}
                                (:parent-name values)])])))

     (if read-only
       (form/input-field :label "Rank"
                         :name "rank"
                         :read-only read-only
                         :value (:rank values))
       (form/field :label "Rank"
                   :name "rank"
                   :input [:select {:name "rank"
                                    :class "select select-bordered select-sm w-full max-w-xs"
                                    :autocomplete "off"
                                    :id "rank"
                                    :read-only read-only
                                    :x-validate.required true
                                    :value (:rank values)}
                           [:<>
                            (for [rank ranks]
                              [:option {:value rank
                                        :selected (when (= rank (some-> values :rank name))
                                                    "selected")}
                               rank])]]))

     ;; TODO: When creating a new taxon we should havea "Create" button to only
     ;; save when all of the required fields are filled in
     [:div {:class "spl-btn-grp mt-4"}
      (form/button "Save")]])

   [:script {:type "module"
             :src (html/static-url "js/taxon_form.ts")}]])
