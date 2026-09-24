(ns sepal.app.routes.accession.form
  (:require [clojure.string :as str]
            [sepal.accession.interface.spec :as accession.spec]
            [sepal.app.codes :as codes]
            [sepal.app.html :as html]
            [sepal.app.routes.contact.routes :as contact.routes]
            [sepal.app.routes.location.routes :as location.routes]
            [sepal.app.routes.taxon.routes :as taxon.routes]
            [sepal.app.ui.combobox :as ui.combobox]
            [sepal.app.ui.form :as ui.form]
            [sepal.app.ui.icons.lucide :as lucide]
            [sepal.contact.interface.name :as contact.name]
            [zodiac.core :as z]))

(defn enum-label-fn [v]
  (-> v
      (name)
      (str/replace "_" " ")
      (str/capitalize)))

(def code-help "Your garden's accession number. Must be unique.")

(defn code-input
  "The Code control on its own, so a collision can swap it for one carrying the
  recomputed suggestion.

  Hand-rolled rather than ui.form/input-field, which returns a whole field and
  cannot be swapped on its own -- so this has to carry the same aria wiring
  input-field would have given it."
  [& {:keys [value errors help]}]
  [:input (cond-> {:autocomplete "off"
                   :class "spl-input"
                   :id "code"
                   :name "code"
                   :required true
                   :minlength 1
                   :type "text"
                   :value value
                   :aria-describedby (ui.form/describedby "code" {:help help
                                                                  :errors errors})}
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

(defn form
  "The create page and the edit page share this.

  `provenance-suggestion-url` is create-only. Given it, picking a taxon asks
  the server whether its rank implies a provenance and the page fills
  Provenance Type in — until you set it yourself. The edit form never passes
  it, so nothing can quietly reclassify an accession you are only correcting."
  [& {:keys [action errors location supplier taxon next-code-url values
             provenance-suggestion-url today]}]
  [:div
   (ui.form/form
     {:id "accession-form"
      :hx-post action
      :hx-swap "none"
      :x-on:accession-form:submit.window "$el.requestSubmit()"
      :x-on:accession-form:reset.window "$el.reset()"}
     [:div {:class "spl-form"}
      (ui.form/anti-forgery-field)
      (ui.form/section
        :title "Identity"
        :hint "What this accession is, and what you call it."
        :children
        [(ui.form/field :label "Code"
                        :name "code"
                        :required true
                        :errors (:code errors)
                        :help code-help
                        :input [:div {:class "flex items-center gap-2"}
                                (code-input :value (:code values)
                                            :errors (:code errors)
                                            :help code-help)
                                (when next-code-url
                                  (next-code-button :url next-code-url))])
         (codes/confirm-slot)

         (let [taxa-url (z/url-for taxon.routes/index)]
           (list
             (ui.combobox/combobox
               :name "taxon-id"
               :label "Taxon"
               :url taxa-url
               :required true
               :errors (:taxon-id errors)
               :help "Start typing a name to search the taxonomy."
               :selected (when (:taxon/id taxon)
                           {:id (:taxon/id taxon) :text (:taxon/name taxon)}))
             (when provenance-suggestion-url
               (ui.form/suggestion-listener
                 :id "provenance-suggestion"
                 :url provenance-suggestion-url
                 :trigger "change from:#taxon-id-input"
                 :include "#taxon-id"
                 :apply-fn "window.applyProvenanceSuggestion"))))

         [:div {:class "spl-form-pair"}
          (ui.form/field :label "ID Qualifier"
                         :name "id-qualifier"
                         :input (ui.form/enum-select "id-qualifier"
                                                     accession.spec/id-qualifier
                                                     (:id-qualifier values)))
          ;; TODO: This should only be set when the id-qualifier is set
          (ui.form/field :label "ID Qualifier Rank"
                         :name "id-qualifier-rank"
                         :input (ui.form/enum-select "id-qualifier-rank"
                                                     accession.spec/id-qualifier-rank
                                                     (:id-qualifier-rank values)
                                                     :label-fn enum-label-fn))]])

      (ui.form/section
        :title "Provenance"
        :hint "Where the material came from. Wild status applies only to wild-collected material."
        :children
        [[:div {:class "spl-form-pair"}
          (ui.form/field :label "Provenance Type"
                         :name "provenance-type"
                         :input (ui.form/enum-select "provenance-type"
                                                     accession.spec/provenance-type
                                                     (:provenance-type values)
                                                     :label-fn enum-label-fn
                                                     ;; Set it yourself and the
                                                     ;; suggestion stops.
                                                     :attrs {:x-on:change "$el.dataset.touched = 'true'"}))

          ;; TODO: This should only be set when the provenance type is "wild"
          (ui.form/field :label "Wild Provenance Status"
                         :name "wild-provenance-status"
                         :input (ui.form/enum-select "wild-provenance-status"
                                                     accession.spec/wild-provenance-status
                                                     (:wild-provenance-status values)
                                                     :label-fn enum-label-fn))]

         (ui.combobox/combobox
           :name "supplier-contact-id"
           :label "Supplier"
           :url (z/url-for contact.routes/index)
           :errors (:supplier-contact-id errors)
           ;; The picker only searches contacts that already exist, so a garden
           ;; with none has no way in from here. The link opens in a new tab
           ;; because this form is usually half filled in by the time you find
           ;; out.
           :help (list "Suppliers come from your contacts. "
                       [:a {:class "spl-link"
                            :href (z/url-for contact.routes/new)
                            :target "_blank"
                            :rel "noreferrer"}
                        "Create a contact"]
                       " in a new tab if the one you want is missing.")
           :selected (when (:contact/id supplier)
                       {:id (:contact/id supplier)
                        :text (contact.name/label supplier)}))])

      (ui.form/section
        :title "Placement"
        :hint "Where this material is meant to go before it is planted."
        :children
        [(ui.combobox/combobox
           :name "intended-location-id"
           :label "Intended location"
           :url (z/url-for location.routes/index)
           :errors (:intended-location-id errors)
           :help "Leave it empty until the bed is decided."
           :selected (when (:location/id location)
                       {:id (:location/id location)
                        :text (format "%s (%s)"
                                      (:location/code location)
                                      (:location/name location))}))])

      (ui.form/section
        :title "Receipt"
        :hint "What arrived, how much of it, and when."
        :children
        [[:div {:class "spl-form-pair"}
          (ui.form/input-field :label "Date Received"
                               :name "date-received"
                               :type "date"
                               :value (:date-received values)
                               ;; The route refuses a future date too; max
                               ;; keeps the picker from offering one.
                               :input-attrs {:max today}
                               :errors (:date-received errors))
          (ui.form/input-field :label "Date Accessioned"
                               :name "date-accessioned"
                               :type "date"
                               :value (:date-accessioned values)
                               :input-attrs {:max today}
                               :errors (:date-accessioned errors))]
         [:div {:class "spl-form-pair"}
          (ui.form/field :label "Received as"
                         :name "received-type"
                         :errors (:received-type errors)
                         :input (ui.form/enum-select "received-type"
                                                     accession.spec/received-type
                                                     (:received-type values)
                                                     :label-fn enum-label-fn))
          (ui.form/input-field :label "Quantity received"
                               :name "quantity-received"
                               :type "number"
                               :input-attrs {:min 0}
                               :value (:quantity-received values)
                               :errors (:quantity-received errors))]])])
   [:script {:type "module"
             :src (html/static-url "app/routes/accession/form.ts")}]])
