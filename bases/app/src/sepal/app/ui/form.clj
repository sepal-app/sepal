(ns sepal.app.ui.form
  (:require [ring.middleware.anti-forgery :refer [*anti-forgery-token*]]
            [sepal.malli.interface :as malli.i]))

(def anti-forgery-field-name "__anti-forgery-token")

(def AntiForgeryField
  [(keyword anti-forgery-field-name) :string])

(defn form [attrs & children]
  [:form (merge {:x-data true
                 :x-ref "form"
                 :class "flex flex-col"
                 :x-form-state {}}
                attrs)
   children])

(defn anti-forgery-field []
  [:input {:type "hidden"
           :name anti-forgery-field-name
           :id "__anti-forgery-token"
           :value (force *anti-forgery-token*)}])

(defn field [& {:keys [errors label input]}]
  [:fieldset {:class "fieldset w-full"}
   [:legend {:class "fieldset-legend text-md"}
    label]
   input
   [:ul {:class "validator-hint"}
    (for [error errors]
      [:li error])]])

(defn input-field [& {:keys [id label name read-only required type value errors
                             minlength maxlength input-attrs]}]
  ;; TODO: Validate the errors format
  ;; (tap> (str "input-field/errors: " errors))

;; TODO: The main thing we need to figure out with the form is how to set client
  ;; set both server side and client side validation errors in the field error
  (field :errors errors
         :name name
         :label label
         :input [:input (merge {:autocomplete "off"
                                :class "input validator w-full"
                                :id (or id name)
                                :maxlength maxlength
                                :minlength minlength
                                :name name
                                :readonly (or read-only false)
                                :required (or required false)
                                :type (or type "text")
                                :value value}
                               input-attrs)]))

(defn hidden-field [& {:keys [id name value input-attrs]}]
  [:input (merge {:name name
                  :id id
                  :value value
                  :type "hidden"}
                 input-attrs)])

(defn textarea-field [& {:keys [errors id label name required value]}]
  [:label {:for name
           :class "form-control w-full max-w-xs"}
   [:div {:class "label"}
    [:span {:class "label-text"} label]]
   [:textarea {:autocomplete "off"
               :name name
               :id id
               :required (or required false)
               :class "textarea textarea-bordered"}
    value]

   (when errors
     [:ul {:class "errors"}
      (for [error errors]
        [:li {:class "text-red-600"} error])])])

(defn submit-button
  ([children]
   (submit-button {} children))
  ([attrs children]
   [:button (merge {:type "submit"
                    :class "btn btn-primary"
                    :x-bind:disabled "$refs?.form && !$validate.isComplete($refs.form)"}
                   attrs)
    children]))

(defn enum-select
  "Helper for the common case of building a <select/> from a malli :enum spec."
  [name enum value & {:keys [label-fn value-fn filter-fn]
                      :or {value-fn clojure.core/name
                           label-fn clojure.core/name
                           filter-fn keyword?}}]
  [:select {:name name
            :class "select select-bordered select-md w-full max-w-xs px-2"
            :autocomplete "off"
            :id name
            :value value}
   [:option ""]
   (for [[val label] (map #(vector (value-fn %) (label-fn %))
                          (->> enum rest (filter filter-fn)))]
     [:option {:value val
               :selected (when (= val value)
                           "selected")}
      label])])

(defn footer [& {:keys [buttons]}]
  [:div {:class "fixed bottom-0 flex flex-row gap-4 p-4 bg-white shadow w-full"
         :x-transition:enter "transition-transform ease-out duration-300"
         :x-transition:enter-start "translate-y-20"
         :x-transition:enter-end "translate-y-0"
         :x-show "dirty"}
   buttons])
