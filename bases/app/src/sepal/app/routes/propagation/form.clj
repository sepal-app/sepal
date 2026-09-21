(ns sepal.app.routes.propagation.form
  (:require [sepal.app.routes.accession.routes :as accession.routes]
            [sepal.app.routes.location.routes :as location.routes]
            [sepal.app.routes.taxon.routes :as taxon.routes]
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

(defn- parent-field
  "The parent accession: a picker when it is not yet decided, a locked line
  when the form was opened from the parent's own screen. The material picker
  appears only then, because a plant can only be named once the accession is."
  [& {:keys [values errors material-items]}]
  (let [accession-id (:parent-accession-id values)]
    (if accession-id
      (list
        (form/hidden-field :name "parent-accession-id" :value accession-id)
        (form/input-field :label "Parent accession"
                          :name "parent-accession-display"
                          :read-only true
                          :value (:accession-code values))
        (when (seq material-items)
          (combobox/combobox
            :name "parent-material-id"
            :label "Parent plant"
            :items material-items
            :selected (when (:parent-material-id values)
                        {:id (:parent-material-id values)
                         :text (str (:accession-code values)
                                    "." (:material-code values))})
            :help "Optional. Leave it out when the cuttings came off the accession without a particular plant being recorded.")))
      (combobox/combobox
        :name "parent-accession-id"
        :label "Parent accession"
        :url (z/url-for accession.routes/index)
        :required true
        :errors errors
        :selected (when accession-id
                    {:id accession-id
                     :text (:accession-code values)})))))

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

(defn form [& {:keys [action errors values types material-items]}]
  (let [type (or (:type values) "seed")
        graft? (= "graft" (name type))
        material? (:parent-material-id values)]
    [:div {:x-data (str "{ type: '" (name type) "' }")}
     (form/form
       {:id "propagation-form"
        :hx-post action
        :hx-swap "none"
        :x-on:propagation-form:submit.window "$el.requestSubmit()"
        :x-on:propagation-form:reset.window "$el.reset()"}
       [(form/anti-forgery-field)
        [:div {:class "spl-form"}
         (form/section
           :title "Method"
           :hint "How the material was produced."
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
                         :errors (:parent-accession-id errors)
                         :material-items material-items))

         (form/section
           :title "Batch"
           :hint "Where it sits and how many came through."
           :children
           [(location-field :values values :errors (:location-id errors))
            [:div {:class "spl-form-pair"}
             (form/input-field :label "Propagated"
                               :name "propagated-on"
                               :type "date"
                               :value (:propagated-on values)
                               :errors (:propagated-on errors))
             (form/input-field :label "Succeeded on"
                               :name "succeeded-on"
                               :type "date"
                               :value (:succeeded-on values)
                               :errors (:succeeded-on errors))]
            [:div {:class "spl-form-pair"}
             (form/input-field :label "Started"
                               :name "quantity-started"
                               :type "number"
                               :value (:quantity-started values)
                               :errors (:quantity-started errors)
                               :help "Leave blank for a mass sowing, where nobody counted.")
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
                :help "Optional. Filling this reduces the parent lot and records the change as a division. Leave blank when taking cuttings or seed, which removes nothing."))])]])]))
