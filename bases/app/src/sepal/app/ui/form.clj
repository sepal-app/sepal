(ns sepal.app.ui.form
  (:require [clojure.string :as str]
            [ring.middleware.anti-forgery :refer [*anti-forgery-token*]]))

(def anti-forgery-field-name "__anti-forgery-token")

(def AntiForgeryField
  [(keyword anti-forgery-field-name) :string])

(defn form
  "Every form in the app.

  Cmd/Ctrl+Enter submits from any field in it. Save is a plain button on most
  of these forms rather than a submit button, so Enter alone does nothing, and
  a textarea needs the modifier regardless. Two listeners because Alpine ANDs
  the modifiers on one — there is no way to say cmd-or-ctrl in a single
  directive. `requestSubmit` is what the Save button ends up calling too, so
  both routes run native validation and go through HTMX the same way.

  One submission at a time, so a double-click cannot send a POST twice. HTMX
  would queue a second request behind one in flight, and `hx-sync` makes it
  drop it instead. A plain POST form ignores a repeat submit while
  `submitting`, which lasts until the page is replaced or restored from the
  back-forward cache."
  [attrs & children]
  [:form (merge {:x-data "{ submitting: false }"
                 :x-ref "form"
                 :class "grid gap-1"
                 :x-form-state {}
                 :hx-sync "this:drop"
                 :x-on:submit "submitting ? $event.preventDefault() : (submitting = true)"
                 :x-on:htmx:after-request "submitting = false"
                 :x-on:pageshow.window "submitting = false"
                 :x-on:keydown.enter.cmd.prevent "$el.requestSubmit()"
                 :x-on:keydown.enter.ctrl.prevent "$el.requestSubmit()"}
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

(defn suggestion-listener
  "An element that asks the server for a suggestion and applies the answer.

  Hidden and empty: it exists only to carry the hx-* attributes, which stay off
  the control they suggest for. htmx marks its requesting element with
  htmx-request, htmx-swapping and htmx-settling as a request runs, and a picker
  that reacted to its own class list changing is how suggesting mid-search used
  to close the completions.

  The value the endpoint reads has to be named in :include, because a div
  carries no value of its own the way the select did. :apply-fn names a global
  taking the response body, and :id is what says in the markup which
  suggestion this is."
  [& {:keys [id url trigger include params apply-fn]}]
  [:div (cond-> {:id id
                 :hidden true
                 :hx-get url
                 :hx-trigger trigger
                 :hx-swap "none"
                 (keyword "hx-on::after-request")
                 (str "if (event.detail.successful) "
                      apply-fn "(event.detail.xhr.responseText)")}
          include (assoc :hx-include include)
          params (assoc :hx-params params))])

(defn input-field
  "A labelled input. The label, help, and error-list ids derive from `:id`
  when it is given, not `:name`, so a field that appears in more than one form
  on a page gets its errors on the right one."
  [& {:keys [id label name read-only required type value errors
             help minlength maxlength input-attrs]}]
  (let [control-id (or id name)]
    (field :errors errors
           :name control-id
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
                                   (describedby control-id {:help help :errors errors})
                                   (assoc :aria-describedby
                                          (describedby control-id {:help help :errors errors})))
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
           :name control-id
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
                               (describedby control-id {:help help :errors errors})
                               (assoc :aria-describedby
                                      (describedby control-id {:help help :errors errors})))
                   value])))

(defn footer-buttons
  "Cancel and Save for a record form, as the footer expects them.

  `form-event` names the Alpine event the form listens for — an accession form
  listens for `accession-form:submit`, so pass \"accession-form\".

  `on-cancel` is `:back` to leave the page or `:reload` to stay and discard.
  Reloading is what a tab inside a record page wants; a create page wants back.

  One definition rather than five near-copies, which had drifted: two asked
  before discarding only when the form was dirty and three asked every time,
  even with nothing to lose, and Save was disabled while invalid on three of
  the five. `x-form-state` puts `dirty` and `valid` on every form this
  namespace builds, so both are safe everywhere."
  [& {:keys [form-event on-cancel] :or {on-cancel :back}}]
  (let [discard (case on-cancel
                  :reload "location.reload()"
                  "history.back()")]
    [[:button {:type "button"
               :class "spl-btn"
               :x-on:click (str "if (!dirty || confirm('Are you sure you want "
                                "to lose your changes?')) " discard)}
      "Cancel"]
     [:button {:type "button"
               :class "spl-btn spl-btn--primary"
               :x-on:click (str "$dispatch('" form-event ":submit')")
               :x-bind:disabled "!valid"}
      "Save"]]))

(defn submit-button
  ([children]
   (submit-button {} children))
  ([attrs children]
   [:button (merge {:type "submit"
                    :x-bind:disabled "!dirty || !valid || submitting"}
                   attrs)
    children]))

(defn enum-select
  "Helper for the common case of building a <select/> from a malli :enum spec.

  `attrs` is merged onto the control, for the odd field that needs one of its
  own — the accession form marks Provenance Type as touched from here."
  [name enum value & {:keys [label-fn value-fn filter-fn attrs]
                      :or {value-fn clojure.core/name
                           label-fn clojure.core/name
                           filter-fn keyword?}}]
  [:select (merge {:name name
                   :class "spl-input spl-select"
                   :autocomplete "off"
                   :id name
                   :value value}
                  attrs)
   [:option ""]
   (for [[val label] (map #(vector (value-fn %) (label-fn %))
                          (->> enum rest (filter filter-fn)))]
     ;; Compare using value-fn on both sides to handle keyword vs string
     [:option {:value val
               :selected (when (= val (some-> value value-fn))
                           "selected")}
      label])])

(defn footer
  "The form's action bar: right-aligned, on the tinted surface, with a hairline
  above it.

  Always present. It used to be `x-show=\"dirty\"`, so landing on a create page
  showed no way to submit until you typed something — the primary action of the
  screen was invisible on arrival."
  [& {:keys [buttons]}]
  [:div {:class "spl-form-footer"}
   buttons])
