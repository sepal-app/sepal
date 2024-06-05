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
  [:label {:for name
           :class "form-control w-full max-w-xs"}
   [:div {:class "label"}
    [:span {:class "label-text"} label]]
   input

   (when errors
     [:ul {:class "errors"}
      (for [error errors]
        [:li {:class "text-red-600"} error])])])

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
                         :class "input input-bordered input-sm w-full max-w-xs bg-white"}]))

(defn hidden-field [& {:keys [id name value]}]
  [:input {:name name
           :id id
           :value value
           :type "hidden"}])

(defn textarea-field [& {:keys [errors id label name required value]}]
  [:label {:for name
           :class "form-control w-full max-w-xs"}
   [:div {:class "label"}
    [:span {:class "label-text"} label]]
   [:textarea {:name name
               :id id
               :required (or required false)
               :value value
               :type (or type "text")
               :class "textarea textarea-bordered"}]

   (when errors
     [:ul {:class "errors"}
      (for [error errors]
        [:li {:class "text-red-600"} error])])])
