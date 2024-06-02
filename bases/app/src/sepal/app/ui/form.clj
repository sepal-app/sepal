(ns sepal.app.ui.form
  (:require [ring.middleware.anti-forgery :refer [*anti-forgery-token*]]))

(def anti-forgery-field-name "__anti-forgery-token")

(def AntiForgeryField
  [(keyword anti-forgery-field-name) :string])

(defn anti-forgery-field []
  [:input {:type "hidden"
           :name anti-forgery-field-name
           :id "__anti-forgery-token"
           :value *anti-forgery-token*}])

(defn field [& {:keys [errors label input name]}]
  ;; TODO: try to get the name from the input attributes
  [:div {:for name
         :class "mb-4"}
   [:label {:for name
            :class "spl-label"}
    label
    [:div {:class "mt-1"}
     input

     (when errors
       [:ul {:class "errors"}
        (for [error errors]
          [:li {:class "text-red-600"} error])])]]])

(defn input-field [& {:keys [id label name read-only required type value errors]}]
  (field :errors errors
         :name name
         :label label
         :input [:input {:name name
                         :id id
                         :required (or required false)
                         :value value
                         :readonly (or read-only false)
                         :type (or type "text")
                         :autocomplete "off"
                         :class "spl-input"
                         :_class "px-4 py-2 shadow-sm focus:ring-indigo-500 focus:border-indigo-500 block w-full sm:text-md border-gray-300 rounded-md"}]))

(defn hidden-field [& {:keys [id name value]}]
  [:input {:name name
           :id id
           :value value
           :type "hidden"}])

(defn textarea-field [& {:keys [errors id label name required value]}]
  [:div {:class "mb-4"}
   [:label {:for name
            :class "spl-label"
            :_class "block text-sm font-medium text-gray-700"}
    label
    [:div  {:class "mt-1"}
     [:textarea {:name name
                 :id id
                 :required (or required false)
                 :value value
                 :type (or type "text")
                 :class "spl-input"
                 :_class "px-4 py-2 shadow-sm focus:ring-indigo-500 focus:border-indigo-500 block w-full sm:text-md border-gray-300 rounded-md"}]
     (when errors
       [:ul {:class "errors"}
        (for [error errors]
          [:li {:class "text-red-600"} error])])]]])
