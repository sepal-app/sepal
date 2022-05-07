(ns sepal.app.routes.taxon.views.form
  (:require [sepal.app.ui.form :refer [anti-forgery-field input-field]]))

(defn form [& {:keys [action method values]}]
  (tap> (str "taxon form values2: " values))
  [:form {:action action
          :method (or method "get")}
   (anti-forgery-field)
   (input-field :label "Name"
                :name "name"
                :value (:name values)
                :required true)
   (input-field :label "Parent"
                :name "parent_id"
                :value (:parent-id values))
   (input-field :label "Rank"
                :name "rank"
                :value (:rank values)
                :required true)
   [:button {:type "submit"} "Save"]])
