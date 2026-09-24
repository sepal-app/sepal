(ns sepal.app.routes.propagation.form
  (:require [sepal.app.routes.location.routes :as location.routes]
            [sepal.app.routes.propagation.routes :as propagation.routes]
            [sepal.app.routes.taxon.routes :as taxon.routes]
            [sepal.app.ui.accession-combobox :as accession-combobox]
            [sepal.app.ui.combobox :as combobox]
            [sepal.app.ui.form :as form]
            [zodiac.core :as z]))

(defn footer-buttons []
  (form/footer-buttons :form-event "propagation-form" :on-cancel :back))

(defn- type-field
  "The method select. `clonal` rides on the option as data, though nothing on
  this form reads it yet: the product default is decided on the server."
  [& {:keys [types value errors]}]
  (form/field
    :label "Method"
    :name "type"
    :required true
    :help "How the material was produced."
    :errors errors
    :input
    [:select {:name "type"
              :id "type"
              :class "spl-input spl-select"
              :required true
              :x-model "type"}
     (for [{:propagation-type/keys [name label clonal]} types]
       [:option {:value name
                 :data-clonal (str clonal)
                 :selected (when (= name value) "selected")}
        label])]))

(defn- rootstock-field
  "Shown for grafts. The database does not constrain it to grafts, and the
  handler does not reject a rootstock on another method."
  [& {:keys [values errors]}]
  (combobox/combobox
    :name "rootstock-taxon-id"
    :label "Rootstock"
    :url (z/url-for taxon.routes/index)
    :errors errors
    :selected (when (:rootstock-taxon-id values)
                {:id (:rootstock-taxon-id values)
                 :text (:taxon-name values)})
    :help "A graft's other parent. Commercial rootstock is bought by the bundle, so this names the cultivar rather than a plant here."))

(defn parent-material-field
  "The parent plant picker, offering the chosen accession's material and
  empty until one is chosen. Rendered by the form and by the endpoint that
  swaps it in when the accession changes, so it has one id to be replaced by."
  [& {:keys [values errors material-items]}]
  [:div {:id "parent-material-field"}
   (combobox/combobox
     :name "parent-material-id"
     :label "Parent plant"
     :items material-items
     :errors errors
     :selected (when (:parent-material-id values)
                 {:id (:parent-material-id values)
                  :text (str (:accession-code values)
                             "." (:material-code values))})
     :help (if (:accession-code values)
             "Optional. Leave it out when the cuttings came off the accession without a particular plant being recorded."
             "Optional. Choose a parent accession to pick from its plants."))])

(defn- parent-field
  "The parent accession and, once it is known, the plant.

  Locked when the form was opened from the parent's own screen, and when the
  batch has products: those are material or accessions of this lineage, and
  moving the parent would rewrite where they came from."
  [& {:keys [values errors material-items locked?]}]
  (let [accession-id (:parent-accession-id values)]
    (if locked?
      (list
        (form/hidden-field :name "parent-accession-id" :value accession-id)
        (form/input-field :label "Parent accession"
                          :name "parent-accession-display"
                          :read-only true
                          :value (:accession-code values))
        (if (:products? values)
          (list
            (form/hidden-field :name "parent-material-id"
                               :value (:parent-material-id values))
            (when (:parent-material-id values)
              (form/input-field :label "Parent plant"
                                :name "parent-material-display"
                                :read-only true
                                :value (str (:accession-code values)
                                            "." (:material-code values)))))
          (parent-material-field :values values
                                 :errors (:parent-material-id errors)
                                 :material-items material-items)))
      (list
        [:div {:hx-get (z/url-for propagation.routes/parent-plant)
               :hx-trigger "change from:#parent-accession-id"
               :hx-include "#parent-accession-id"
               :hx-target "#parent-material-field"
               :hx-swap "outerHTML"}
         (accession-combobox/accession-combobox
           :name "parent-accession-id"
           :label "Parent accession"
           :required true
           :errors (:parent-accession-id errors)
           :accession-id accession-id
           :accession-text (:accession-code values))]
        (parent-material-field :values values
                               :errors (:parent-material-id errors)
                               :material-items material-items)))))

(defn- location-field [& {:keys [values errors]}]
  (combobox/combobox
    :name "location-id"
    :label "Location"
    :url (z/url-for location.routes/index)
    :errors errors
    :selected (when (:location-id values)
                {:id (:location-id values)
                 :text (format "%s (%s)"
                               (:location-code values)
                               (:location-name values))})
    :help "Where the batch sits while it runs. A nursery bench is a location."))

(defn- status-field [& {:keys [statuses value errors]}]
  (form/field
    :label "Status"
    :name "status"
    :errors errors
    :input
    [:select {:name "status" :id "status" :class "spl-input spl-select"}
     (for [{:propagation-status/keys [name label]} statuses]
       [:option {:value name
                 :selected (when (= name value) "selected")}
        label])]))

(defn form
  "The create form, and with :statuses the edit form. An edit has a status and
  no parent quantity: that field records a division, which happens once."
  [& {:keys [action errors values types statuses material-items parent-locked? today]}]
  (let [type (or (:type values) "seed")
        graft? (= "graft" (name type))
        edit? (some? statuses)
        material? (and (not edit?) (:parent-material-id values))]
    [:div {:x-data (str "{ type: '" (name type) "' }")}
     (form/form
       {:id "propagation-form"
        :hx-post action
        :hx-swap "none"
        :x-on:propagation-form:submit.window "$el.requestSubmit()"
        :x-on:propagation-form:reset.window "$el.reset()"}
       [(form/anti-forgery-field)
        [:div {:class "spl-form"}
         ;; Untitled: the method is one field, and a heading would repeat its
         ;; label. Rootstock joins it only for a graft.
         (form/section
           :children
           [(type-field :types types :value (name type) :errors (:type errors))
            [:div (cond-> {:x-show "type === 'graft'"}
                    (not graft?) (assoc :style "display:none"))
             (rootstock-field :values values :errors (:rootstock-taxon-id errors))]])

         (form/section
           :title "Parent"
           :hint "The accession this came off, and the plant when it is known."
           :children
           (parent-field :values values
                         :errors errors
                         :material-items material-items
                         :locked? parent-locked?))

         (form/section
           :title "Batch"
           :hint "Where it sits and how many came through."
           :children
           [(when edit?
              (status-field :statuses statuses
                            :value (some-> (:status values) name)
                            :errors (:status errors)))
            (location-field :values values :errors (:location-id errors))
            [:div {:class "spl-form-pair"}
             (form/input-field :label "Propagated"
                               :name "propagated-on"
                               :type "date"
                               :value (:propagated-on values)
                               ;; The route refuses a future date too; max
                               ;; keeps the picker from offering one.
                               :input-attrs {:max today}
                               :errors (:propagated-on errors))
             (form/input-field :label "Succeeded on"
                               :name "succeeded-on"
                               :type "date"
                               :value (:succeeded-on values)
                               :input-attrs {:max today}
                               :errors (:succeeded-on errors))]
            [:div {:class "spl-form-pair"}
             (form/input-field :label "Started"
                               :name "quantity-started"
                               :type "number"
                               :value (:quantity-started values)
                               :errors (:quantity-started errors)
                               :help "Leave blank for a mass sowing.")
             (form/input-field :label "Succeeded"
                               :name "quantity-succeeded"
                               :type "number"
                               :value (:quantity-succeeded values)
                               :errors (:quantity-succeeded errors))]
            (when material?
              (form/input-field
                :label "Parent quantity"
                :name "parent-quantity"
                :type "number"
                :value (:parent-quantity values)
                :errors (:parent-quantity errors)
                :help "Optional. Filling this reduces the parent lot and records the change as a division. Leave blank when taking cuttings or seed, which removes nothing."))])

         (form/section
           :children
           (form/textarea-field :label "Notes"
                                :name "notes"
                                :value (:notes values)
                                :errors (:notes errors)
                                :help "Anything the fields above do not hold, such as a medium or treatment tried, or why the batch failed."))]])]))
