(ns sepal.app.routes.material.form
  (:require [sepal.app.codes :as codes]
            [sepal.app.routes.accession.routes :as accession.routes]
            [sepal.app.routes.location.routes :as location.routes]
            [sepal.app.routes.material.routes :as material.routes]
            [sepal.app.ui.combobox :as combobox]
            [sepal.app.ui.form :as form]
            [sepal.app.ui.icons.lucide :as lucide]
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
  It swaps this element for itself."
  [& {:keys [value accession-id errors]}]
  [:input (cond-> {:autocomplete "off"
                   :class "spl-input w-full"
                   :placeholder (if accession-id "Required" "Choose an accession first")
                   :required true
                   :id "code"
                   :name "code"
                   :type "text"
                   :hx-get (z/url-for material.routes/next-code)
                   :hx-trigger "material:accession-changed from:body"
                   :hx-include "#accession-id"
                   :hx-swap "outerHTML"
                   :value value
                   :aria-describedby (form/describedby "code" {:errors errors})}
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
                    :title "Use the next available code"
                    :aria-label "Use the next available code"}
             include (assoc :hx-include include))
   (lucide/rotate-cw :class "size-4")])

(defn form [& {:keys [action errors values reasons next-code-url]}]
  (let [statuses (->> material.spec/status rest (mapv name))
        types (->> material.spec/type rest (mapv name))]
    [:div
     (form/form
       {:id "material-form"
        :hx-post action
        :hx-swap "none"
        :x-on:material-form:submit.window "$el.requestSubmit()"
        :x-on:material-form:reset.window "$el.reset()"}
       [(form/anti-forgery-field)
        [:div {:class "spl-form"}
         (form/section
           :title "Identity"
           :hint "Which accession this material came from, and where it lives."
           :children
           ;; Accession first: the code is numbered within its accession, so
           ;; asking for the code above the field it depends on asks you to
           ;; look up an answer the form has not been told yet.
           [(combobox/combobox
              :name "accession-id"
              :label "Accession"
              :url (z/url-for accession.routes/index)
              :required true
              :errors (:accession-id errors)
              :selected (when (:accession-id values)
                          {:id (:accession-id values)
                           :text (:accession-code values)}))
            (form/field :label "Code"
                        :name "code"
                        :errors (:code errors)
                        :input [:div {:class "flex items-center gap-2"}
                                (code-input :value (:code values)
                                            :accession-id (:accession-id values)
                                            :errors (:code errors))
                                (when next-code-url
                                  (next-code-button :url next-code-url
                                                    :include "#accession-id"))])
            (codes/confirm-slot)
            (combobox/combobox
              :name "location-id"
              :label "Location"
              :url (z/url-for location.routes/index)
              :required true
              :errors (:location-id errors)
              :help (when-let [label (:intended-location-label values)]
                      (str "This accession is intended for " label "."))
              :selected (when (:location-id values)
                          {:id (:location-id values)
                           :text (format "%s (%s)"
                                         (:location-code values)
                                         (:location-name values))}))])

         (form/section
           :title "Holding"
           :hint "How much there is, and what condition it is in."
           :children
           [[:div {:class "spl-form-pair"}
             (form/field :label "Quantity"
                         :name "quantity"
                         :errors (:quantity errors)
                         :input [:input {:autocomplete "off"
                                         :class "spl-input w-full"
                                         :id "quantity"
                                         :name "quantity"
                                         :type "number"
                                         :min 0
                                         :required true
                                         :value (or (:quantity values) 1)}])
             (form/field :label "Status"
                         :name "status"
                         :errors (:status errors)
                         :input [:select {:name "status"
                                          :autocomplete "off"
                                          :id "status"
                                          :required true
                                          :value (:status values)}
                                 [(for [status statuses]
                                    [:option {:value status
                                              :selected (when (= status (some-> values :status name))
                                                          "selected")}
                                     status])]])]
            (form/field :label "Type"
                        :name "type"
                        :errors (:type errors)
                        :input [:select {:name "type"
                                         :autocomplete "off"
                                         :id "type"
                                         :required true
                                         :value (:type values)}
                                [(for [type types]
                                   [:option {:value type
                                             :selected (when (= type (some-> values :type name))
                                                         "selected")}
                                    type])]])
            ;; Only where a change can happen. The create page passes no
            ;; reasons, and a record being made for the first time has not
            ;; changed from anything — the field offered None and nothing else.
            (when (seq reasons)
              (form/field :label "Reason for change"
                          :name "reason"
                          :errors (:reason errors)
                          :hint "Recorded in this material's history when the location or quantity changes."
                          :input [:select {:name "reason"
                                           :id "reason"
                                           :autocomplete "off"
                                           :class "spl-input w-full"}
                                  [:option {:value ""} "None"]
                                  (for [{:material-change-reason/keys [code label]} reasons]
                                    [:option {:value code} label])]))])]])]))
