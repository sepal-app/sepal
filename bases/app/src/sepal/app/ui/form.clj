(ns sepal.app.ui.form
  (:require [ring.middleware.anti-forgery :refer [*anti-forgery-token*]]))

(def anti-forgery-field-name "__anti-forgery-token")

(def AntiForgeryField
  [(keyword anti-forgery-field-name) :string])

(defn form [attrs children]
  [:form (merge {:x-data true
                 :x-validate.use-browser.input true
                 :x-ref "form"
                 :class "flex flex-col gap-2"}
                attrs)
   children])

(defn anti-forgery-field []
  [:input {:type "hidden"
           :name anti-forgery-field-name
           :id "__anti-forgery-token"
           :value (force *anti-forgery-token*)}])

(defn field-errors [& {:keys [errors]}]
  (let [errors (if (string? errors)
                 [errors]
                 errors)]
    (when errors
      [:ul {:class "errors"}
       (for [error errors]
         [:div {:class "label"}
          [:span {:class "label-text-alt input-error text-red-500"} error]])])))

(defn field [& {:keys [errors label input name]}]
  [:label {:for name
           :class "form-control w-full max-w-xs"}
   [:div {:class "label"}
    [:span {:class "label-text"} label]]
   input

   (field-errors :errors errors)])

(defn input-field [& {:keys [id label name read-only required type value errors
                             minlength maxlength data-error-msg input-attrs]}]
  ;; TODO: Validate the errors format
  (field :errors errors
         :name name
         :label label
         :input [:input (merge {:autocomplete "off"
                                :class "input input-bordered input-sm"
                                :id (or id name)
                                :maxlength maxlength
                                :minlength minlength
                                :name name
                                :readonly (or read-only false)
                                :x-validate true
                                :data-error-msg data-error-msg
                                :required (or required false)
                                :aria-invalid (str (boolean (not-empty errors)))
                                :type (or type "text")
                                :value value}
                               input-attrs)]))

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
   [:textarea {:autocomplete "off"
               :name name
               :id id
               :required (or required false)
               :x-validate true
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

(defn footer [& {:keys [buttons]}]
  [:div {:class "fixed bottom-0 flex flex-row gap-4 p-4 bg-white shadow w-full"
         :x-transition:enter "transition-transform ease-out duration-300"
         :x-transition:enter-start "translate-y-20"
         :x-transition:enter-end "translate-y-0"
         :x-show "dirty"}
   buttons])
