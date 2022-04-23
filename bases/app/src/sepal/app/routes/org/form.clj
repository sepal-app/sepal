(ns sepal.app.routes.org.form
  (:require [sepal.app.ui.form :refer [anti-forgery-field input-field]]))

(defn form [& {:keys [action method values]}]
  [:form {:action action
          :method (or method "get")}
   (anti-forgery-field)
   (input-field :label "Name"
                :name "name"
                :value (:name values))
   (input-field :label "Short name"
                :name "short-name"
                :value (:short-name values))
   (input-field :label "Abbreviation"
                :name "abbreviation"
                :value (:abbreviation values))
   [:button {:type "submit"} "Save"]])
