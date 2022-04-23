(ns sepal.app.ui.form
  (:require [ring.middleware.anti-forgery :refer [*anti-forgery-token*]]))

(defn anti-forgery-field []
  [:input {:type "hidden"
           :name "__anti-forgery-token"
           :id "__anti-forgery-token"
           :value *anti-forgery-token*}])

(defn input-field [& {:keys [label name required type value]}]
  [:div {:class "mb-4"}
   [:label {:for name
            :class "block text-sm font-medium text-gray-700"} label
    [:div  {:class "mt-1"}
     [:input {:name name
              :required (or required false)
              :value value
              :type (or type "text")
              :class "px-4 py-2 shadow-sm focus:ring-indigo-500 focus:border-indigo-500 block w-full sm:text-md border-gray-300 rounded-md"}]]]])

(defn select-field [& {:keys [label name value options]}]
  [:div {:class "mb-4"}
   [:label {:for name
            :class "block text-sm font-medium text-gray-700"} label
    [:div  {:class "mt-1"}
     [:select {:name name
               :value value
               :class "mt-1 block w-full pl-3 pr-10 py-2 text-base border-gray-300 focus:outline-none focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm rounded-md"}
      (for [option options]
        [:option option])]]]])
