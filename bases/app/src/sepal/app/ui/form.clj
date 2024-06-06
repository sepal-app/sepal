(ns sepal.app.ui.form
  (:require [ring.middleware.anti-forgery :refer [*anti-forgery-token*]]))

(def anti-forgery-field-name "__anti-forgery-token")

(def AntiForgeryField
  [(keyword anti-forgery-field-name) :string])

(defn form [attrs children]
  [:form (merge {:x-data ""
                 :x-validate ""
                 :x-ref "form"
                 :class "flex-flex-col gap-2"}
                attrs)
   children])

(defn anti-forgery-field []
  [:input {:type "hidden"
           :name anti-forgery-field-name
           :id "__anti-forgery-token"
           :value *anti-forgery-token*}])

(defn field-errors [& {:keys [errors]}]
  (when errors
    [:ul {:class "errors"}
     (for [error errors]
       [:div {:class "label"}
        [:span {:class "label-text-alt"} error]])]))

(defn field [& {:keys [errors label input name]}]
  [:label {:for name
           :class "form-control w-full max-w-xs"}
   [:div {:class "label"}
    [:span {:class "label-text"} label]]
   input

   [:div {:class "label"}
    [:span {:class "label-text-alt"
            :id (str "error-msg-" name)}]]

   (field-errors errors)])

(defn input-field [& {:keys [id label name read-only required type value errors minlength maxlength]}]
  (field :errors errors
         :name name
         :label label
         :input [:input {:autocomplete "off"
                         :class "spl-input"
                         :id (or id name)
                         :maxlength maxlength
                         :minlength minlength
                         :name name
                         :readonly (or read-only false)
                         :x-validate.required (or required false)
                         :required (or required false)
                         :type (or type "text")
                         :value value}]))

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

(defn button
  ([children]
   (button {} children))
  ([attrs children]
   [:button (merge {:type "submit"
                    :class "btn btn-primary"
                    :x-bind:disabled "$refs?.form && !$validate.isComplete($refs.form)"}
                   attrs)
    children]))
