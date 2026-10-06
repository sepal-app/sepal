(ns sepal.app.routes.material.form
  (:require [sepal.app.codes :as codes]
            [sepal.app.json :as json]
            [sepal.app.routes.location.routes :as location.routes]
            [sepal.app.routes.material.routes :as material.routes]
            [sepal.app.ui.accession-combobox :as accession-combobox]
            [sepal.app.ui.combobox :as combobox]
            [sepal.app.ui.form :as form]
            [sepal.app.ui.icons.lucide :as lucide]
            [sepal.app.ui.page :as ui.page]
            [sepal.i18n.interface :refer [tr trc]]
            [sepal.material.interface.spec :as material.spec]
            [zodiac.core :as z]))

#_(def FormValues
    [:map
   ;; TODO: If there is an id value then require the accession code, taxon.name
   ;; location code and location name
     [:code :string]
     [:accession-id :string]
     [:location-id :string]])

;; (def types ["Plant"])

(defn code-input
  "The Code control, rendered both by the form and by the next-code endpoint.

  Two of the three ways into this form do not know an accession -- the list
  create button and its empty state both link to a bare /material/new -- so
  the suggestion arrives from the endpoint rather than from the first render.
  It swaps this element for itself.

  `suggest?` is for the create form only: on an edit, a new suggestion would
  overwrite the record's existing code."
  [& {:keys [value accession-id errors suggest?]}]
  [:input (cond-> {:autocomplete "off"
                   :class "spl-input w-full"
                   :placeholder (if accession-id (tr "Required") (tr "Choose an accession first"))
                   :required true
                   :id "code"
                   :name "code"
                   :type "text"
                   :value value
                   :aria-describedby (form/describedby "code" {:errors errors})}
            suggest? (assoc :hx-get (z/url-for material.routes/next-code)
                            :hx-trigger "change from:#accession-id"
                            :hx-include "#accession-id"
                            :hx-swap "outerHTML")
            (seq errors) (assoc :aria-invalid "true"))])

(defn- next-code-button
  "Replaces the Code field with the current next code.

  A code read when the page loaded can be taken by the time you save, and the
  form gives no other way to ask again short of reloading and losing the rest
  of what you typed. Create forms only: on an edit this would overwrite a
  record's existing code with no undo."
  [& {:keys [url include]}]
  [:button (cond-> {:type "button"
                    :class "spl-btn spl-btn--sm spl-btn--icon"
                    :hx-get url
                    :hx-target "#code"
                    :hx-swap "outerHTML"
                    :title (tr "Use the next available code")
                    :aria-label (tr "Use the next available code")}
             include (assoc :hx-include include))
   (lucide/rotate-cw :class "size-4")])

(defn form [& {:keys [action errors values reasons next-code-url]}]
  (let [statuses (rest material.spec/status)
        types (rest material.spec/type)]
    [:div
     (form/form
       (merge ui.page/region-swap
              {:id "material-form"
               :hx-post action
               :x-on:material-form:submit.window "$el.requestSubmit()"
               :x-on:material-form:reset.window "$el.reset()"})
       [(form/anti-forgery-field)
        [:div {:class "spl-form"}
         (form/section
           :title (tr "Identity")
           :hint (tr "Which accession this material came from, and where it lives.")
           :children
           ;; Accession first: the code is numbered within its accession, so
           ;; asking for the code above the field it depends on asks you to
           ;; look up an answer the form has not been told yet.
           [(accession-combobox/accession-combobox
              :name "accession-id"
              :required true
              :errors (:accession-id errors)
              :accession-id (:accession-id values)
              :accession-text (:accession-code values))
            (form/field :label (trc "material" "Code")
                        :name "code"
                        :errors (:code errors)
                        :input [:div {:class "flex items-center gap-2"}
                                (code-input :value (:code values)
                                            :accession-id (:accession-id values)
                                            :suggest? (some? next-code-url)
                                            :errors (:code errors))
                                (when next-code-url
                                  (next-code-button :url next-code-url
                                                    :include "#accession-id"))])
            (codes/confirm-slot)
            (combobox/combobox
              :name "location-id"
              :label (tr "Location")
              :url (z/url-for location.routes/index)
              :required true
              :errors (:location-id errors)
              :help (when-let [label (:intended-location-label values)]
                      (tr "This accession is intended for %1." label))
              :selected (when (:location-id values)
                          {:id (:location-id values)
                           :text (format "%s (%s)"
                                         (:location-code values)
                                         (:location-name values))}))])

         (form/section
           :title (tr "Holding")
           :hint (tr "How much there is, and what condition it is in.")
           :children
           [[:div {:class "spl-form-pair"
                   :x-data (str "quantityStatus("
                                (json/js (sort (map name material.spec/living-statuses)))
                                ")")}
             (form/field :label (tr "Quantity")
                         :name "quantity"
                         :errors (:quantity errors)
                         :input [:input {:autocomplete "off"
                                         :class "spl-input w-full"
                                         :id "quantity"
                                         :x-ref "quantity"
                                         :x-bind:readonly "locked"
                                         :name "quantity"
                                         :type "number"
                                         :min 0
                                         :required true
                                         :value (or (:quantity values) 1)}])
             (form/field :label (tr "Status")
                         :name "status"
                         :errors (:status errors)
                         :input [:select {:name "status"
                                          :class "spl-input spl-select"
                                          :autocomplete "off"
                                          :id "status"
                                          :x-ref "status"
                                          :x-on:change "sync()"
                                          :required true
                                          :value (:status values)}
                                 [(for [status statuses]
                                    [:option {:value (name status)
                                              :selected (when (= (name status) (some-> values :status name))
                                                          "selected")}
                                     (tr (material.spec/status-labels status))])]])]
            (form/field :label (tr "Type")
                        :name "type"
                        :errors (:type errors)
                        :input [:select {:name "type"
                                         :class "spl-input spl-select"
                                         :autocomplete "off"
                                         :id "type"
                                         :required true
                                         :value (:type values)}
                                [(for [type types]
                                   [:option {:value (name type)
                                             :selected (when (= (name type) (some-> values :type name))
                                                         "selected")}
                                    (tr (material.spec/type-labels type))])]])
            ;; Only where a change can happen. The create page passes no
            ;; reasons, and a record being made for the first time has not
            ;; changed from anything — the field offered None and nothing else.
            (when (seq reasons)
              (form/field :label (tr "Reason for change")
                          :name "reason"
                          :errors (:reason errors)
                          :hint (tr "Recorded in this material's history when the location or quantity changes.")
                          :input [:select {:name "reason"
                                           :id "reason"
                                           :autocomplete "off"
                                           :class "spl-input spl-select w-full"}
                                  [:option {:value ""} (tr "None")]
                                  (for [{:material-change-reason/keys [code label]} reasons]
                                    [:option {:value code} (trc "material_change_reason" label)])]))])]])]))
