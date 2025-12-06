(ns sepal.app.ui.form
  (:require [ring.middleware.anti-forgery :refer [*anti-forgery-token*]]
            [sepal.app.ui.page :as ui.page]))

(def anti-forgery-field-name "__anti-forgery-token")

(def AntiForgeryField
  [(keyword anti-forgery-field-name) :string])

(defn form [attrs & children]
  [:form (merge {:x-data true
                 :x-ref "form"
                 :class "grid gap-1"
                 :x-form-state {}}
                attrs)
   children])

(defn anti-forgery-field []
  [:input {:type "hidden"
           :name anti-forgery-field-name
           :id "__anti-forgery-token"
           :value (force *anti-forgery-token*)}])

(defn label-id [field-name]
  (str field-name "-label"))

(defn description-id [field-name]
  (str field-name "-description"))

(defn errors-id
  "ID for the error container element for a field."
  [field-name]
  (str field-name "-errors"))

(defn error-id
  ([field-name]
   (str field-name "-error"))
  ([field-name index]
   (str field-name "-error-" index)))

(defn error-list
  "Render just the error list for a field. Can be used for OOB swaps."
  [field-name errors & {:keys [hx-swap-oob?]}]
  [:ul (cond-> {:id (errors-id field-name)
                :class ["validator-hint text-error"
                        (when (seq errors) "visible")]}
         hx-swap-oob? (assoc :hx-swap-oob "true"))
   (for [[i error] (map-indexed vector errors)]
     [:li {:id (error-id field-name i)} error])])

(defn field [& {:keys [name errors label input]}]
  [:fieldset {:class "fieldset w-full"}
   [:legend {:class "fieldset-legend text-md"
             :id (label-id name)}
    label]
   input
   (error-list name errors :hx-swap-oob? true)])

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
  (field :errors errors
         :name name
         :label label
         :input  [:textarea {:autocomplete "off"
                             :name name
                             :id id
                             :required (or required false)
                             :class "textarea textarea-bordered w-full"}
                  value]))

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
            :class "select select-bordered select-md w-full max-w-sm px-2"
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
  [:div {:class "fixed bottom-0 py-4 bg-white shadow w-full"
         :x-transition:enter "transition-transform ease-out duration-300"
         :x-transition:enter-start "translate-y-20"
         :x-transition:enter-end "translate-y-0"
         :x-show "dirty"}
   (ui.page/page-inner
     [:div {:class "flex flex-row gap-4 "}
      buttons])])
