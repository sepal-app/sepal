(ns sepal.app.ui.observations
  "Markup for resource observations: the tab's form and list, and the
  read-only panel section. One namespace because material and location both
  render the same observation; only the URLs and lookup options differ."
  (:require [ring.middleware.anti-forgery :refer [*anti-forgery-token*]]
            [sepal.app.json :as json]
            [sepal.app.ui.form :as ui.form])
  (:import [java.time LocalDate]))

(defn- today []
  (str (LocalDate/now)))

(defn- dated-field
  "A date input. Not `ui.form/input-field`: that helper keys the error-list
  id off the same `:name` it submits under, and the create form and every
  item's own inline edit form each render a field named `observed_on` --
  passing an item's suffixed id straight through as `input-field`'s `:name`
  would submit under the wrong key. Building the input here keeps the real
  `name` attribute fixed while the id -- and so the error-list id `field`
  derives from it -- can carry the suffix."
  [& {:keys [id-suffix name label value errors]}]
  (let [id (str name (when id-suffix (str "-" id-suffix)))
        described-by (ui.form/describedby id {:errors errors})]
    (ui.form/field :label label
                   :name id
                   :for id
                   :errors errors
                   :input [:input (cond-> {:autocomplete "off"
                                           :class "spl-input"
                                           :id id
                                           :name name
                                           :type "date"
                                           :value value}
                                    (seq errors) (assoc :aria-invalid "true")
                                    described-by (assoc :aria-describedby described-by))])))

(defn- text-input-field
  "A plain text input, for the same reason `dated-field` exists rather than
  reusing `ui.form/input-field`."
  [& {:keys [id-suffix name label value errors]}]
  (let [id (str name (when id-suffix (str "-" id-suffix)))
        described-by (ui.form/describedby id {:errors errors})]
    (ui.form/field :label label
                   :name id
                   :for id
                   :errors errors
                   :input [:input (cond-> {:autocomplete "off"
                                           :class "spl-input"
                                           :id id
                                           :name name
                                           :type "text"
                                           :value value}
                                    (seq errors) (assoc :aria-invalid "true")
                                    described-by (assoc :aria-describedby described-by))])))

(defn- note-field
  "The Notes textarea, for the same reason `dated-field` exists rather than
  reusing `ui.form/textarea-field`."
  [& {:keys [id-suffix value errors]}]
  (let [id (str "note" (when id-suffix (str "-" id-suffix)))
        described-by (ui.form/describedby id {:errors errors})]
    (ui.form/field :label "Notes"
                   :name id
                   :for id
                   :errors errors
                   :input [:textarea (cond-> {:autocomplete "off"
                                              :class "spl-input spl-textarea"
                                              :id id
                                              :name "note"}
                                       (seq errors) (assoc :aria-invalid "true")
                                       described-by (assoc :aria-describedby described-by))
                           value])))

(defn- type-value-fields
  "The Type and Value pair. Value's options track the selected Type in the
  browser, via a JSON map of every type's values passed in as x-data, and the
  field disappears entirely for a general observation, which carries no
  value.

  `id-suffix` keeps ids unique when the same pair appears more than once on a
  page — the create form and each observation's own inline edit form all
  render one."
  [& {:keys [id-suffix errors values type-options value-options-by-type]}]
  (let [suffix (when id-suffix (str "-" id-suffix))
        type-id (str "type" suffix)
        value-id (str "value" suffix)]
    [:div {:x-data (json/js {:type (:type values)
                             :value (:value values)
                             :valueOptionsByType value-options-by-type})}
     (ui.form/field :label "Type"
                    :name type-id
                    :for type-id
                    :errors (:type errors)
                    :input [:select {:name "type"
                                     :id type-id
                                     :class "spl-input spl-select"
                                     :autocomplete "off"
                                     :required true
                                     :x-model "type"}
                            (for [{:observation-type/keys [code label]} type-options]
                              [:option {:value code
                                        :selected (when (= code (:type values)) "selected")}
                               label])])
     [:div {:x-show "type !== 'general'"}
      (ui.form/field :label "Value"
                     :name value-id
                     :for value-id
                     :errors (:value errors)
                     :input [:select {:name "value"
                                      :id value-id
                                      :class "spl-input spl-select"
                                      :autocomplete "off"
                                      :x-model "value"}
                             [:option {:value ""} ""]
                             [:template {:x-for "opt in (valueOptionsByType[type] || [])"}
                              [:option {:x-bind:value "opt.value"
                                        :x-text "opt.label"}]]])]]))

(defn- fields
  "Every field an observation carries, shared by the create form and each
  item's inline edit form."
  [& {:keys [id-suffix errors values type-options value-options-by-type]}]
  (list
    (type-value-fields :id-suffix id-suffix
                       :errors errors
                       :values values
                       :type-options type-options
                       :value-options-by-type value-options-by-type)
    (dated-field :id-suffix id-suffix
                 :name "observed_on"
                 :label "Observed on"
                 :value (or (:observed_on values) (today))
                 :errors (:observed_on errors))
    (text-input-field :id-suffix id-suffix
                      :name "observed_by"
                      :label "Observed by"
                      :value (:observed_by values)
                      :errors (:observed_by errors))
    (dated-field :id-suffix id-suffix
                 :name "next_check_on"
                 :label "Next check"
                 :value (:next_check_on values)
                 :errors (:next_check_on errors))
    (note-field :id-suffix id-suffix
                :value (:note values)
                :errors (:note errors))))

(defn- observation-item
  "One observation: its type, value, note and metadata, and an edit form for
  it hidden behind an Alpine flag. Carrying the form here rather than
  fetching it saves a route and a handler on each of the resources this is
  used from."
  [& {:keys [observation observation-url-fn type-options value-options-by-type]}]
  (let [{:observation/keys [id observed-on observed-by note author-email
                            type-label value-label type value
                            next-check-on]} observation
        url (observation-url-fn id)]
    [:li {:class "spl-note"
          :data-observation-id id
          :x-data (json/js {:editing false})}
     [:div {:x-show "!editing"}
      [:div {:class "spl-note-meta text-xs text-text-soft flex items-center gap-2"}
       [:span observed-on]
       (when observed-by
         [:span {:data-observation-observed-by ""} observed-by])
       ;; The 1,643 notes imported from Bauble have no author. The element is
       ;; absent rather than empty, so nothing renders a stray separator.
       (when author-email
         [:span {:data-observation-author ""} author-email])]
      [:div {:class "text-sm font-medium"}
       type-label
       ;; A general observation has no value-label. Absent rather than an
       ;; empty span, the way the author-email line above works.
       (when value-label
         [:span (str ": " value-label)])]
      (when note
        [:p {:class "spl-note-body whitespace-pre-wrap"} note])
      [:div {:class "spl-note-actions flex gap-2"}
       [:button {:type "button"
                 :class "spl-btn spl-btn--ghost spl-btn--sm"
                 :x-on:click "editing = true"}
        "Edit"]
       [:button {:type "button"
                 :class "spl-btn spl-btn--ghost spl-btn--sm"
                 :hx-delete url
                 :hx-headers (json/js {"X-CSRF-Token" *anti-forgery-token*})
                 :hx-confirm "Delete this observation?"
                 :hx-target "#observations-list"
                 :hx-swap "outerHTML"}
        "Delete"]]]
     [:div {:x-show "editing"}
      (ui.form/form
        {:hx-post url
         :hx-target "#observations-list"
         :hx-swap "outerHTML"}
        [:div {:class "spl-form"}
         (ui.form/anti-forgery-field)
         (fields :id-suffix id
                 :errors nil
                 :values {:type type
                          :value value
                          :observed_on observed-on
                          :observed_by observed-by
                          :next_check_on next-check-on
                          :note note}
                 :type-options type-options
                 :value-options-by-type value-options-by-type)
         [:div {:class "flex justify-end gap-2"}
          [:button {:type "button"
                    :class "spl-btn spl-btn--ghost spl-btn--sm"
                    :x-on:click "editing = false"}
           "Cancel"]
          (ui.form/submit-button "Save")]])]]))

(defn observation-list
  "The list of observations, newest observed first. Carries the id every
  swap targets."
  [& {:keys [observations observation-url-fn type-options value-options-by-type]}]
  [:div {:id "observations-list"}
   (if (seq observations)
     [:ul {:class "spl-note-list"}
      (for [observation observations]
        ^{:key (:observation/id observation)}
        (observation-item :observation observation
                          :observation-url-fn observation-url-fn
                          :type-options type-options
                          :value-options-by-type value-options-by-type))]
     [:p {:data-observations-empty ""
          :class "text-text-soft text-sm"}
      "No observations yet."])])

(defn observation-form
  "The new-observation form. Posts to the tab's own URL and replaces the
  list.

  It clears itself after a successful post. The swap replaces the list, not
  this form, so without the reset the fields keep the values they just sent
  and a second click writes the same observation twice. `dirty` has to go
  back to false alongside the DOM reset: x-form-state only ever sets it true,
  and submit-button is bound to `!dirty || !valid`, so a reset without it
  leaves an enabled button over an empty form. A failed post resets nothing —
  the values stay where the curator can fix them."
  [& {:keys [action errors values type-options value-options-by-type]}]
  (ui.form/form
    {:id "observation-form"
     :hx-post action
     :hx-target "#observations-list"
     :hx-swap "outerHTML"
     (keyword "hx-on::after-request")
     "if (event.detail.successful) { this.reset(); Alpine.$data(this).dirty = false }"}
    [:div {:class "spl-form"}
     (ui.form/anti-forgery-field)
     (fields :errors errors
             :values values
             :type-options type-options
             :value-options-by-type value-options-by-type)
     [:div {:class "flex justify-end"}
      (ui.form/submit-button "Add observation")]]))

(defn observations-body
  "The whole tab body: the form above the list."
  [& {:keys [observations create-url observation-url-fn errors values
             type-options value-options-by-type]}]
  [:div {:class "spl-notes"}
   (observation-form :action create-url
                     :errors errors
                     :values values
                     :type-options type-options
                     :value-options-by-type value-options-by-type)
   (observation-list :observations observations
                     :observation-url-fn observation-url-fn
                     :type-options type-options
                     :value-options-by-type value-options-by-type)])

(defn panel-section
  "The read-only panel body. A reader never reaches the tab — it is behind
  the edit permission — so this is where an observation is visible to them."
  [& {:keys [observations observation-count more-url]}]
  [:div {:class "space-y-2"}
   (for [observation observations]
     ^{:key (:observation/id observation)}
     [:div {:class "spl-card bg-surface shadow-sm"}
      [:div {:class "spl-card-body p-3"}
       [:div {:class "text-xs text-text-soft"} (:observation/observed-on observation)]
       [:p {:class "text-sm whitespace-pre-wrap"} (:observation/note observation)]]])
   (when (and more-url observation-count (> observation-count (count observations)))
     [:a {:href more-url
          :class "spl-link text-sm"}
      (str "See all " observation-count)])])
