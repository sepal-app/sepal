(ns sepal.app.ui.combobox
  "A record picker: <sepal-combobox> over a search endpoint.

  The markup is rendered here, the way every other field is, and the element
  wires behaviour to what it finds. Light DOM, so the spl- layer styles it and
  <label for> gives it a real name — a picker has to announce as \"Location\"
  rather than as \"Combobox\".

  See js/record-combobox-element.ts for why this is a custom element rather
  than a library."
  (:require [sepal.app.ui.form :as form]))

(defn option
  "One row a picker offers.

  `content` is hiccup, so a scientific name keeps its serif and a synonym match
  can say what it matched — formatting that belongs here rather than being
  concatenated into a string in TypeScript. `text` is what goes in the field
  once it is chosen, which is plain by definition."
  [& {:keys [id text content selected?]}]
  [:li {:role "option"
        :class "spl-combobox-option"
        :data-value (str id)
        :data-text text
        :aria-selected (str (boolean selected?))}
   (or content text)])

(defn option-content
  "The usual shape of a row: an icon, what the record is called, and a line of
  detail under it.

  A picker is where you tell two similar records apart, so a row can carry more
  than one string — a code beside a name, a rank and author beside a scientific
  name, a thumbnail. `title` and `meta` are hiccup, so a name can keep its
  italics rather than being flattened into text."
  [& {:keys [icon title meta]}]
  (list
    (when icon
      [:span {:class "spl-combobox-option-icon" :aria-hidden "true"} icon])
    [:span {:class "spl-combobox-option-body"}
     [:span {:class "spl-combobox-option-title"} title]
     (when meta [:span {:class "spl-combobox-option-meta"} meta])]))

(defn note
  "A row that says something rather than offering something. Not selectable."
  [text]
  [:li {:class "spl-combobox-note" :aria-disabled "true"} text])

(defn options-fragment
  "What a picker's endpoint answers: the rows, and a last line when there are
  more than it will show.

  Anything past the limit is unreachable — the list does not page — so the only
  way to it is a narrower query, which is what the line says."
  [& {:keys [items total]}]
  (let [shown (count items)]
    (list
      (for [item items] (apply option (mapcat identity item)))
      (cond
        (zero? shown) (note "No matches")
        (> total shown) (note (format "Showing %d of %d — keep typing to narrow"
                                      shown total))))))

(defn combobox
  "One record picker.

  :name       the field the form submits, e.g. \"location-id\"
  :label      what it is called, and what a screen reader announces
  :url        an endpoint answering {options, total}
  :selected   {:id :text} for the record already chosen, or nil
  :required   whether a value has to be chosen
  :items      rows to send with the page, for a list that never changes; the
              field then filters these rather than asking :url
  :help       a line under the field
  :errors     messages for this field
  :label-hidden? when the caller already renders a label — the media link form
              wraps four of these in one labelled field — in which case the
              name is carried by aria-label instead. A field still has to say
              what it is; the only question is whether it says it twice."
  [& {:keys [name label url items selected required help errors label-hidden?]}]
  (let [input-id (str name "-input")
        list-id (str name "-listbox")
        described-by (form/describedby name {:help help :errors errors})]
    [:sepal-combobox
     (cond-> {:class "spl-field"
              ;; A real name attribute, not just the data- one: a
              ;; form-associated custom element submits under this, and
              ;; ElementInternals/setFormValue writes nothing without it.
              :name name
              ;; htmx's hx-include selects by id, and a suggestion endpoint
              ;; needs the chosen record rather than the text on screen.
              :id name
              :data-name name
              :data-label label}
       url (assoc :data-url url)
       selected (assoc :data-value (str (:id selected))
                       :data-text (:text selected))
       required (assoc :data-required ""))

     (when-not label-hidden?
       [:label {:class "spl-label" :for input-id} label
        (when required [:span {:class "spl-required" :aria-hidden "true"} " *"])])

     [:div {:class "spl-combobox"}
      [:input (cond-> {:id input-id
                       :type "text"
                       :class "spl-input spl-combobox-input"
                       :role "combobox"
                       :autocomplete "off"
                       :aria-expanded "false"
                       :aria-autocomplete "list"
                       :aria-controls list-id
                       :value (:text selected)}
                label-hidden? (assoc :aria-label label)
                described-by (assoc :aria-describedby described-by)
                (seq errors) (assoc :aria-invalid "true"))]
      [:button {:type "button"
                :class "spl-combobox-button"
                ;; Out of the tab order: the input is the control, and a
                ;; second stop on the way past a field is noise.
                :tabindex "-1"
                :aria-label (str "Show " label " options")}
       [:svg {:class "spl-combobox-chevron" :viewBox "0 0 20 20"
              :fill "none" :stroke "currentColor" :aria-hidden "true"}
        [:path {:d "M6 8l4 4 4-4" :stroke-width "1.5"
                :stroke-linecap "round" :stroke-linejoin "round"}]]]
      ;; `items` is for a list that is fixed and small enough to send with the
      ;; page — the timezones. Everything else leaves this empty and the rows
      ;; arrive from :url as they are typed.
      [:ul {:id list-id
            :role "listbox"
            :class "spl-combobox-options"
            :aria-label label
            :hidden true}
       (for [item items] (apply option (mapcat identity item)))]]

     ;; What a reader who cannot see the list appear is told instead.
     [:span {:class "sr-only"
             :role "status"
             :aria-live "polite"
             :data-combobox-status true}]

     (when help
       [:span {:class "spl-help" :id (form/description-id name)} help])
     (form/error-list name errors)]))
