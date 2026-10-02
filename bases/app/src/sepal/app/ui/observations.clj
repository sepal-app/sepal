(ns sepal.app.ui.observations
  "Markup for resource observations: the tab's form and list, and the
  read-only panel section. One namespace because material and location both
  render the same observation; only the URLs and lookup options differ."
  (:require [ring.middleware.anti-forgery :refer [*anti-forgery-token*]]
            [sepal.app.datetime :as datetime]
            [sepal.app.json :as json]
            [sepal.app.ui.button :as ui.button]
            [sepal.app.ui.form :as ui.form]
            [sepal.app.ui.icons.lucide :as lucide]
            [sepal.app.ui.page :as ui.page]
            [sepal.i18n.interface :as i18n :refer [tr trc]])
  (:import [java.time LocalDate]))

(defn- control-id
  "A control's id: its field name, suffixed with the observation id in an
  inline edit form, so the create form and every item's edit form can each
  render the same field on one page."
  [name id-suffix]
  (str name (when id-suffix (str "-" id-suffix))))

(defn- type-value-fields
  "The Type and Value pair. Value's options track the selected Type in the
  browser, via a JSON map of every type's values passed in as x-data, and the
  field disappears entirely for a general observation, which carries no
  value.

  Alpine's `type` starts at the option the select renders as selected, so
  the Value options on first paint belong to the Type on screen. A form
  reset puts the select back to that option without firing a change event,
  so the reset listener reads it back into `type` and clears `value`."
  [& {:keys [id-suffix errors values type-options value-options-by-type]}]
  (let [type-id (control-id "type" id-suffix)
        value-id (control-id "value" id-suffix)]
    [:div {:class "spl-form-fields"
           :x-data (json/js {:type (:type values)
                             :value (:value values)
                             :valueOptionsByType value-options-by-type})
           :x-init (str "$el.closest('form').addEventListener('reset', () => "
                        "$nextTick(() => { type = $refs.type.value; value = '' }))")}
     (ui.form/field :label (tr "Type")
                    :name type-id
                    :errors (:type errors)
                    :input [:select {:name "type"
                                     :id type-id
                                     :class "spl-input spl-select"
                                     :autocomplete "off"
                                     :required true
                                     :x-ref "type"
                                     :x-model "type"}
                            (for [{:observation-type/keys [code label]} type-options]
                              [:option {:value code
                                        :selected (when (= code (:type values)) "selected")}
                               (trc "observation_type" label)])])
     [:div {:x-show "type !== 'general'"}
      (ui.form/field :label (tr "Value")
                     :name value-id
                     :errors (:value errors)
                     :input [:select {:name "value"
                                      :id value-id
                                      :class "spl-input spl-select"
                                      :autocomplete "off"
                                      :x-model "value"}
                             [:option {:value ""} ""]
                             [:template {:x-for "opt in (valueOptionsByType[type] || [])"}
                              [:option {:x-bind:value "opt.value"
                                        :x-bind:selected "opt.value === value"
                                        :x-text "opt.label"}]]])]]))

(defn- fields
  "Every field an observation carries, shared by the create form and each
  item's inline edit form. `values` without a `:type` gets the first type,
  which is the option the select shows, and without an `:observed_on` gets
  `today`."
  [& {:keys [id-suffix errors values type-options value-options-by-type today]}]
  (let [values (cond-> values
                 (nil? (:type values)) (assoc :type (:observation-type/code (first type-options))))]
    (ui.form/section
      :children
      (list
        (type-value-fields :id-suffix id-suffix
                           :errors errors
                           :values values
                           :type-options type-options
                           :value-options-by-type value-options-by-type)
        (ui.form/input-field :id (control-id "observed_on" id-suffix)
                             :name "observed_on"
                             :label (tr "Observed on")
                             :type "date"
                             :value (or (:observed_on values) today)
                             ;; The routes reject a future date too; max keeps
                             ;; the picker from offering one.
                             :input-attrs {:max today}
                             :errors (:observed_on errors))
        (ui.form/input-field :id (control-id "observed_by" id-suffix)
                             :name "observed_by"
                             :label (tr "Observed by")
                             :value (:observed_by values)
                             :errors (:observed_by errors))
        (ui.form/input-field :id (control-id "next_check_on" id-suffix)
                             :name "next_check_on"
                             :label (tr "Next check")
                             :type "date"
                             :value (:next_check_on values)
                             :errors (:next_check_on errors))
        (ui.form/textarea-field :id (control-id "note" id-suffix)
                                :name "note"
                                :label (tr "Notes")
                                :value (:note values)
                                :errors (:note errors))))))

(defn- summary
  "The type and, when it has one, the value: 'Phenology · Flowering'."
  [type-label value-label]
  (str (some->> type-label (trc "observation_type"))
       (when value-label (str " · " (trc "observation_value" value-label)))))

(defn- observation-item
  "One observation as a timeline entry, and an edit form for it hidden
  behind an Alpine flag. Carrying the form here rather than fetching it saves
  a route and a handler on each of the resources this is used from."
  [& {:keys [observation observation-url-fn type-options value-options-by-type today
             followed-up?]}]
  (let [{:observation/keys [id observed-on observed-by note observer
                            type-label value-label type value
                            next-check-on]} observation
        url (observation-url-fn id)
        overdue? (and next-check-on today (not followed-up?)
                      (<= (compare next-check-on today) 0))]
    [:div {:class "spl-changelog-entry"
           :key id
           :data-observation-id id
           :x-data (json/js {:editing false})
           :x-on:form-saved "editing = false"}
     [:div {:class "spl-changelog-avatar"}
      [:span {:class "flex size-8 items-center justify-center rounded-full bg-surface-alt text-text-dim"
              :aria-hidden "true"}
       (lucide/eye :size 16)]]
     [:div {:class "spl-changelog-body"}
      [:div {:x-show "!editing"}
       [:div {:class "spl-entry-head"}
        [:p {:class "spl-changelog-line"}
         [:span {:class "spl-badge spl-badge--info"} (summary type-label value-label)]
         ;; observer falls back from observed_by to the creating user, and an
         ;; imported row has neither -- the element is absent rather than
         ;; empty, the way ui/notes.clj handles the same gap.
         (when observer
           (list " " [:span {:data-observation-observer ""} (tr "by %1" observer)]))]
        [:div {:class "spl-entry-actions"}
         (ui.button/icon-button :icon (lucide/pencil)
                                :label (tr "Edit observation")
                                :attrs {:x-on:click "editing = true"})
         (ui.button/icon-button :icon (lucide/trash-2)
                                :label (tr "Delete observation")
                                :danger? true
                                :attrs (merge ui.page/region-swap
                                              {:hx-delete url
                                               :hx-headers (json/js {"X-CSRF-Token" *anti-forgery-token*})
                                               :hx-confirm (tr "Delete this observation?")}))]]
       (when next-check-on
         [:p {:class "spl-changelog-line mt-1 text-text-soft"}
          (tr "Next check %1" (datetime/format-date next-check-on))
          (when overdue?
            (list " " [:span {:class "spl-badge spl-badge--danger"} (tr "Overdue")]))])
       (when note
         [:p {:class "spl-note-body mt-1 whitespace-pre-wrap text-sm"} note])]
      ;; x-cloak keeps the form hidden until Alpine applies x-show, rather
      ;; than flashing every item's edit form on a full page load.
      [:div {:x-show "editing"
             :x-cloak true}
       (ui.form/form
         (merge ui.page/region-swap {:hx-post url})
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
                  :value-options-by-type value-options-by-type
                  :today today)
          [:div {:class "flex justify-end gap-2"}
           [:button {:type "button"
                     :class "spl-btn spl-btn--ghost spl-btn--sm"
                     :x-on:click "editing = false"}
            (tr "Cancel")]
           (ui.form/submit-button {:class "spl-btn spl-btn--primary spl-btn--sm"} (tr "Save"))]])]]]))

(defn- latest-by-type
  "The ids of the newest observation of each type in `observations`, which
  arrive newest first. Any other observation has been followed up, so its
  check is settled -- the rule `observation.i/due` applies."
  [observations]
  (->> observations
       (reduce (fn [seen {:observation/keys [id type]}]
                 (cond-> seen (not (contains? seen type)) (assoc type id)))
               {})
       vals
       set))

(defn observation-list
  "The list of observations as a timeline, newest observed first and grouped
  under a heading per observed day."
  [& {:keys [observations observation-url-fn type-options value-options-by-type today]}]
  (let [latest (latest-by-type observations)]
    [:div {:id "observations-list"}
     (if (seq observations)
       [:div {:class "spl-changelog px-0"}
        (for [day-observations (partition-by :observation/observed-on observations)
              :let [day (:observation/observed-on (first day-observations))]]
          (list
            [:h2 {:class "spl-changelog-day"}
             (datetime/day-label (LocalDate/parse day) (LocalDate/parse today))]
            (for [observation day-observations]
              ^{:key (:observation/id observation)}
              (observation-item :observation observation
                                :observation-url-fn observation-url-fn
                                :type-options type-options
                                :value-options-by-type value-options-by-type
                                :today today
                                :followed-up? (not (contains? latest (:observation/id observation)))))))]
       [:p {:data-observations-empty ""
            :class "text-text-soft text-sm"}
        (tr "No observations yet.")])]))

(defn observation-form
  "The new-observation form. Posts to the tab's own URL and morphs the page.

  It clears itself after a successful post: the morph keeps the form's live
  values, so x-form-state copies the server-rendered defaults back in on
  `form-saved`, and a second click cannot write the same observation twice.
  A failed post changes nothing — the values stay where the curator can fix
  them."
  [& {:keys [action errors values type-options value-options-by-type today]}]
  (ui.form/form
    (merge ui.page/region-swap
           {:id "observation-form"
            :hx-post action})
    [:div {:class "spl-form"}
     (ui.form/anti-forgery-field)
     (fields :errors errors
             :values values
             :type-options type-options
             :value-options-by-type value-options-by-type
             :today today)
     [:div {:class "flex justify-end"}
      (ui.form/submit-button {:class "spl-btn spl-btn--primary"} (tr "Add observation"))]]))

(defn observations-body
  "The whole tab body: the form above the list."
  [& {:keys [observations create-url observation-url-fn errors values
             type-options value-options-by-type today]}]
  [:div {:class "spl-notes"}
   (observation-form :action create-url
                     :errors errors
                     :values values
                     :type-options type-options
                     :value-options-by-type value-options-by-type
                     :today today)
   (observation-list :observations observations
                     :observation-url-fn observation-url-fn
                     :type-options type-options
                     :value-options-by-type value-options-by-type
                     :today today)])

(defn panel-section
  "The read-only panel body. A reader never reaches the tab — it is behind
  the edit permission — so this is where an observation is visible to them."
  [& {:keys [observations observation-count more-url]}]
  [:div {:class "space-y-2"}
   (for [{:observation/keys [id observed-on type-label value-label note]} observations]
     ^{:key id}
     [:div {:class "spl-card bg-surface shadow-sm"}
      [:div {:class "spl-card-body p-3"}
       [:div {:class "text-sm font-medium"} (summary type-label value-label)]
       [:div {:class "text-xs text-text-soft"} (datetime/format-date observed-on)]
       (when note
         [:p {:class "mt-1 text-sm whitespace-pre-wrap"} note])]])
   (when (and more-url observation-count (> observation-count (count observations)))
     [:a {:href more-url
          :class "spl-link text-sm"}
      (tr "See all %1" (i18n/format-number observation-count))])])
