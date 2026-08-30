(ns sepal.app.ui.form
  (:require [clojure.string :as str]
            [ring.middleware.anti-forgery :refer [*anti-forgery-token*]]
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
  "Render just the error list for a field. Can be used for OOB swaps.

  The list is always present so HTMX has a stable OOB target, and is referenced
  by the input's aria-describedby so a screen reader announces the error with
  the field rather than leaving it stranded on the page."
  [field-name errors & {:keys [hx-swap-oob?]}]
  [:ul (cond-> {:id (errors-id field-name)
                :class "spl-error"}
         hx-swap-oob? (assoc :hx-swap-oob "true"))
   (for [[i error] (map-indexed vector errors)]
     [:li {:id (error-id field-name i)} error])])

(defn describedby
  "Which elements describe this field. Help text and the error list both do."
  [field-name {:keys [help errors]}]
  (->> [(when help (description-id field-name))
        (when (seq errors) (errors-id field-name))]
       (remove nil?)
       (str/join " ")
       (not-empty)))

(defn field
  "One labelled control.

  A single input takes <label for>. It used to be wrapped in a fieldset with a
  legend, which labels a *group* — so no input in the app had a programmatic
  label at all.

  :for is the id of the control being labelled, defaulting to :name."
  [& {:keys [name errors label input help required for]}]
  [:div {:class "spl-field"}
   [:label {:class "spl-label"
            :id (label-id name)
            :for (or for name)}
    label
    (when required [:span {:class "spl-required" :aria-hidden "true"} " *"])]
   input
   (when help
     [:span {:class "spl-help" :id (description-id name)} help])
   (error-list name errors :hx-swap-oob? true)])

(defn input-field [& {:keys [id label name read-only required type value errors
                             help minlength maxlength input-attrs]}]
  (let [control-id (or id name)]
    (field :errors errors
           :name name
           :label label
           :help help
           :required required
           :for control-id
           :input [:input (merge (cond-> {:autocomplete "off"
                                          :class "spl-input"
                                          :id control-id
                                          :maxlength maxlength
                                          :minlength minlength
                                          :name name
                                          :readonly (or read-only false)
                                          :required (or required false)
                                          :type (or type "text")
                                          :value value}
                                   (seq errors) (assoc :aria-invalid "true")
                                   (describedby name {:help help :errors errors})
                                   (assoc :aria-describedby
                                          (describedby name {:help help :errors errors})))
                                 input-attrs)])))

(defn section
  "A group of related fields under a heading. Headings mark regions; rules
  separate peers. The column caps at 576px — lists are dense, forms are roomy."
  [& {:keys [title hint children]}]
  [:section {:class "spl-form-section"}
   (when title [:h2 {:class "spl-form-section-title"} title])
   (when hint [:p {:class "spl-form-section-hint"} hint])
   [:div {:class "spl-form-fields"} children]])

(defn hidden-field [& {:keys [id name value input-attrs]}]
  [:input (merge {:name name
                  :id id
                  :value value
                  :type "hidden"}
                 input-attrs)])

(defn textarea-field [& {:keys [errors id label name required value help]}]
  (let [control-id (or id name)]
    (field :errors errors
           :name name
           :label label
           :help help
           :required required
           :for control-id
           :input [:textarea (cond-> {:autocomplete "off"
                                      :name name
                                      :id control-id
                                      :required (or required false)
                                      :class "spl-input spl-textarea"}
                               (seq errors) (assoc :aria-invalid "true")
                               (describedby name {:help help :errors errors})
                               (assoc :aria-describedby
                                      (describedby name {:help help :errors errors})))
                   value])))

(defn submit-button
  ([children]
   (submit-button {} children))
  ([attrs children]
   [:button (merge {:type "submit"
                    :x-bind:disabled "!dirty || !valid"}
                   attrs)
    children]))

(defn enum-select
  "Helper for the common case of building a <select/> from a malli :enum spec."
  [name enum value & {:keys [label-fn value-fn filter-fn]
                      :or {value-fn clojure.core/name
                           label-fn clojure.core/name
                           filter-fn keyword?}}]
  [:select {:name name
            :class "spl-input spl-select"
            :autocomplete "off"
            :id name
            :value value}
   [:option ""]
   (for [[val label] (map #(vector (value-fn %) (label-fn %))
                          (->> enum rest (filter filter-fn)))]
     ;; Compare using value-fn on both sides to handle keyword vs string
     [:option {:value val
               :selected (when (= val (some-> value value-fn))
                           "selected")}
      label])])

(defn footer [& {:keys [buttons]}]
  [:div {:class "spl-form-footer"
         :x-transition:enter "transition-transform ease-out duration-300"
         :x-transition:enter-start "translate-y-20"
         :x-transition:enter-end "translate-y-0"
         :x-show "dirty"}
   (ui.page/page-inner
     [:div {:class "flex flex-row gap-4 "}
      buttons])])
