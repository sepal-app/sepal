(ns sepal.app.routes.taxon.form
  (:require [clojure.string :as str]
            [sepal.app.html :as html]
            [sepal.app.json :as json]
            [sepal.app.routes.taxon.routes :as taxon.routes]
            [sepal.app.ui.combobox :as combobox]
            [sepal.app.ui.form :as form]
            [sepal.app.ui.icons.heroicons :as heroicons]
            [sepal.app.ui.tooltip :as tooltip]
            [sepal.taxon.interface.name :as taxon.name]
            [sepal.taxon.interface.spec :as taxon.spec]
            [sepal.validation.interface :as validation.i]
            [zodiac.core :as z]))

(defn footer-buttons
  "The create page and the edit page share this form, and they want different
  things from Cancel. Reloading an edit page restores the saved values, which
  is the point; reloading the create page hands back the same empty form, so
  Cancel did nothing at all."
  [& {:keys [on-cancel] :or {on-cancel :reload}}]
  (form/footer-buttons :form-event "taxon-form" :on-cancel on-cancel))

(defn- vernacular-name-decoder
  "Collect the repeated name and language fields into one list.

  Rows with no name are dropped rather than rejected. The form always offers
  an empty row so there is somewhere to type without pressing anything first,
  and an untouched row is not something to complain about — the same rule the
  parentage slots follow."
  [form-data]
  (let [names (cond-> (:vernacular-name-name form-data)
                (-> form-data :vernacular-name-name string?)
                vector)
        langs (cond-> (:vernacular-name-language form-data)
                (-> form-data :vernacular-name-language string?)
                vector)
        vernacular-names (->> (mapv (fn [name lang]
                                      {:name name
                                       :language lang})
                                    names langs)
                              (filterv #(not (str/blank? (:name %)))))]
    (-> form-data
        (assoc :vernacular-names vernacular-names)
        (dissoc :vernacular-name-name
                :vernacular-name-language))))

(defn- parentage-decoder
  "Collect the indexed parentage rows into one ordered vector.

  Indexed rather than repeated, unlike the vernacular-name rows: every id a
  `<sepal-combobox>` renders is derived from its `name`, so two pickers
  sharing a name would share `parentage-parent-0-input` and break both
  `<label for>` and the aria wiring. One name per slot keeps them distinct.

  A slot with no taxon chosen is dropped rather than rejected — the form
  always offers a spare, and an unused spare is not an error. Order comes from
  the index, which is what the formula reads back as."
  [form-data]
  (let [rows (->> form-data
                  (keep (fn [[k v]]
                          (when-let [[_ i] (re-matches #"parentage-parent-(\d+)"
                                                       (name k))]
                            (when-not (str/blank? v)
                              {:index (parse-long i)
                               :parent-taxon-id v
                               :role (get form-data
                                          (keyword (str "parentage-role-" i)))}))))
                  (sort-by :index)
                  (mapv #(-> % (dissoc :index)
                             (update :role (fn [r] (if (str/blank? r) "unknown" r))))))]
    (-> form-data
        (assoc :parentage rows)
        (as-> fd (apply dissoc fd (filter #(str/starts-with? (name %) "parentage-")
                                          (keys fd)))))))

(def FormParams
  [:and
   [:map {:decode/form {:enter (comp parentage-decoder vernacular-name-decoder)}}
    [:name [:string {:min 1}]]
    [:author :string]
    [:rank [:string {:min 1}]]
    [:parent-id {:optional true} [:maybe :string]]
    [:distribution {:optional true :decode/form validation.i/empty->nil} [:maybe :string]]
    [:vernacular-names [:* [:map
                            [:name [:string {:min 1}]]
                            [:language [:maybe :string]]]]]
    [:parentage [:* [:map
                     [:parent-taxon-id [:string {:min 1}]]
                     [:role [:enum "seed" "pollen" "unknown"]]]]]]])

(defn- hybrid-name?
  "Whether this name carries a standalone hybrid marker.

   Goes through `normalize-hybrid-marker` rather than matching a regex here,
   so the rule about what counts as a marker is stated once — Ilex and Rumex
   carry an x that belongs to the word."
  [nm]
  (boolean (some-> nm
                   (taxon.name/normalize-hybrid-marker)
                   (str/includes? (str " " taxon.name/hybrid-marker " ")))))

(def ^:private hybrid-name-test
  "A standalone × or x is a hybrid marker. Ilex and Rumex carry an x that
   belongs to the word, which is why this needs the boundaries — the same rule
   `taxon.interface.name/normalize-hybrid-marker` applies server-side."
  "/(^|\\s)[\u00d7x](\\s|$)/")

(defn parentage-row
  "One parent slot. Public because the Add button fetches another from the
   server rather than cloning one in the browser: every id a
   `<sepal-combobox>` renders comes from its `name`, so a cloned row would
   duplicate `parentage-parent-0-input` and break `<label for>` with it."
  [& {:keys [index row errors]}]
  [:div {:class "grid grid-cols-[1fr_140px] gap-2 items-end"}
   (combobox/combobox
     :name (str "parentage-parent-" index)
     :label (if (zero? index) "Crossed from" "and")
     :url (z/url-for taxon.routes/index)
     :errors (:parentage errors)
     :selected (when row
                 {:id (:parent-taxon-id row)
                  :text (:parent-name row)}))
   (form/field
     :label "Role"
     :name (str "parentage-role-" index)
     :input [:select {:name (str "parentage-role-" index)
                      :id (str "parentage-role-" index)
                      :class "spl-input spl-select"
                      :autocomplete "off"}
             (for [[v label] [["unknown" "Unknown"]
                              ["seed" "Seed parent"]
                              ["pollen" "Pollen parent"]]]
               [:option {:value v
                         :selected (when (= v (some-> row :role
                                                      clojure.core/name))
                                     "selected")}
                label])])])

(defn parentage-add-button
  "Fetches one more slot, and swaps itself for a copy carrying the next index.

   The index has to come from somewhere, and the server is the only thing that
   knows how many slots it rendered — so the response replaces this button out
   of band rather than the page counting rows in JavaScript."
  [& {:keys [next-index]}]
  [:button {:type "button"
            :id "parentage-add"
            :class "spl-btn spl-btn--sm self-start"
            :hx-get (z/url-for taxon.routes/parentage-row nil
                               {:index next-index})
            :hx-target "#parentage-rows"
            :hx-swap "beforeend"}
   "Add another parent"])

(defn- parentage-section
  "What a hybrid was crossed from.

  Slots are indexed rather than repeated, and always one more than are filled:
  every id a `<sepal-combobox>` renders comes from its `name`, so a cloned row
  would share `parentage-parent-0-input` and break `<label for>` and the aria
  wiring alike. Two slots to begin with, because a cross usually has two
  parents and a form that starts empty asks you to press something first.

  Collapsed until the name carries a hybrid marker, not hidden and not
  disabled. Hidden makes the form's shape vary between taxa. Disabled was
  wrong for a subtler reason: the section is not unavailable, it is merely not
  relevant yet, and a hybrid whose name was typed without the marker could
  then never have its cross recorded at all. Closed-but-openable leaves that
  door open.

  A native `<details>`, so the toggle is keyboard-operable and announced as a
  disclosure without script. Alpine only opens it — typing a marker expands the
  section, and removing one leaves it where you put it rather than shutting it
  under you."
  [& {:keys [values errors read-only]}]
  (let [rows (vec (:parentage values))
        slots (max 2 (count rows))]
    [:details
     (cond-> {:class "spl-form-section spl-form-details"
              :data-section "parentage"
              :x-init (str "const d = $el, n = document.getElementById('name');"
                           " const f = () => { if (" hybrid-name-test
                           ".test(n?.value ?? '')) d.open = true };"
                           " f(); n?.addEventListener('input', f)")}
       (hybrid-name? (:name values)) (assoc :open true))
     [:summary {:class "spl-form-section-title"} "Hybrid parentage"]
     [:div {:class "spl-form-fields"}
      [:p {:class "spl-help"}
       "The taxa this hybrid was crossed from. Opens on its own once the name
        carries a hybrid marker (×)."]
      (if read-only
        (if (seq rows)
          (for [{:keys [parent-name role]} rows]
            [:p {:class "spl-help"}
             parent-name
             (when (and role (not= "unknown" (str (clojure.core/name role))))
               (str " (" (clojure.core/name role) ")"))])
          [:p {:class "spl-help"} "None recorded."])
        (list
          [:div {:id "parentage-rows" :class "flex flex-col gap-2"}
           (for [i (range slots)]
             (parentage-row :index i :row (get rows i) :errors errors))]
          (parentage-add-button :next-index slots)))]]))

(defn form
  "The create page and the edit page share this.

  `guess-rank-url` and `parent-suggestion-url` are create-only. Given them,
  the Name field asks the server what rank the name implies and which taxon it
  hangs off, and the page fills Rank and Parent in — each until you set that
  field yourself, after which it stops. The edit form passes neither, so
  nothing can quietly re-rank or re-parent a taxon you are only renaming."
  [& {:keys [action errors read-only values guess-rank-url parent-suggestion-url]}]
  (let [ranks (->> taxon.spec/rank rest (mapv name))]
    [:div
     (form/form
       {:action action
        :hx-post action
        :hx-swap "none"
        :id "taxon-form"
        :x-on:taxon-form:submit.window "$el.requestSubmit()"
        :x-on:taxon-form:reset.window "$el.reset()"}
       [:div {:class "spl-form"}
        (form/anti-forgery-field)
        (form/section
          :title "Identity"
          :hint "The scientific name, its author, and where it sits in the
                 taxonomy."
          :children
          [(form/input-field :label "Name"
                             :name "name"
                             :required true
                             :read-only read-only
                             :value (:name values)
                             :errors (:name errors)
                             :input-attrs
                             (when guess-rank-url
                               {:hx-get guess-rank-url
                                :hx-trigger "keyup changed delay:300ms"
                                ;; after-request, not after-swap, and nothing
                                ;; is swapped: htmx fires afterSwap on the
                                ;; swapped-in content, while afterRequest
                                ;; fires on the element that asked. Same trap
                                ;; ui/delete.clj records.
                                :hx-swap "none"
                                (keyword "hx-on::after-request")
                                (str "if (event.detail.successful) "
                                     "window.applyRankGuess(event.detail.xhr.responseText)")}))
           (form/input-field :label "Author"
                             :name "author"
                             :read-only read-only
                             :value (:author values)
                             :errors (:author errors))
           (if read-only
             (form/input-field :label "Parent"
                               :name "parent-id"
                               :read-only read-only
                               :value (:parent-name values))
             (let [url (z/url-for taxon.routes/index)]
               (list
                 (combobox/combobox
                   :name "parent-id"
                   :label "Parent"
                   :url url
                   :errors (:parent-id errors)
                   :selected (when (:parent-id values)
                               {:id (:parent-id values)
                                :text (:parent-name values)}))
                 (when parent-suggestion-url
                   (form/suggestion-listener
                     :id "parent-suggestion"
                     :url parent-suggestion-url
                     ;; It is the Name field that changes, so this listens
                     ;; there and sends that value rather than the picker's.
                     :trigger "keyup changed delay:300ms from:#name"
                     :include "#name"
                     :params "name"
                     :apply-fn "window.applyParentSuggestion")))))
           (if read-only
             (form/input-field :label "Rank"
                               :name "rank"
                               :read-only read-only
                               :value (:rank values))
             (form/field :label "Rank"
                         :name "rank"
                         ;; Built here rather than through `form/enum-select`
                         ;; because the rank guess drives it through
                         ;; `x-rank-field`. It still has to carry the same
                         ;; classes: without them the browser draws its own
                         ;; control, and this was the one select on any form
                         ;; with a different border and chevron.
                         :input [:select {:name "rank"
                                          :class "spl-input spl-select"
                                          :x-rank-field {}
                                          :autocomplete "off"
                                          :id "rank"
                                          :required true
                                          :value (:rank values)}
                                 (for [rank ranks]
                                   [:option {:value rank
                                             :selected (when (= rank (some-> values :rank name))
                                                         "selected")}
                                    rank])]))
           (form/input-field :label "Distribution"
                             :name "distribution"
                             :read-only read-only
                             :value (:distribution values)
                             :errors (:distribution errors))])

        [:fieldset {:class "spl-form-section spl-fieldset"
                    :data-section "vernacular-names"
                    ;; One empty row when there are none, rather than a line
                    ;; saying there are none: somewhere to type beats something
                    ;; to read, and it matches the parentage slots below.
                    :x-data (json/js {:vernacularNames
                                      (if (seq (:vernacular-names values))
                                        (:vernacular-names values)
                                        [{}])})}
         [:legend {:class "spl-form-section-title flex items-center gap-2"}
          "Vernacular names"
          (tooltip/wrap
            [:button {:type "button"
                      :class "spl-btn spl-btn--sm spl-btn--icon"
                      :x-on:click "vernacularNames.push({id: -1}); $data.dirty = true;"
                      :aria-label "Add vernacular name"}
             [:span {:aria-hidden true}
              (heroicons/plus-mini)]]
            "Add vernacular name"
            :side "right")]
         [:div {:class "spl-form-fields"}
          ;; The rows repeat, so the columns are headed once rather than each
          ;; input carrying its own label. Header and rows declare the same
          ;; grid template so they line up; the third column is the delete
          ;; button's width, from .spl-btn--icon. They share a wrapper because
          ;; .spl-form-fields spaces its children 15px apart, which is the gap
          ;; between fields, not between a heading and the row under it.
          [:div {:class "flex flex-col gap-2"}
           [:div {:class "grid grid-cols-[1fr_1fr_32px] gap-2 items-center"}
            [:span {:class "spl-label" :aria-hidden true} "Name"]
            [:span {:class "spl-label" :aria-hidden true} "Language"]
            [:span]]
           [:template {:x-for "(vn, index) in vernacularNames"}
            [:div {:class "grid grid-cols-[1fr_1fr_32px] gap-2 items-center"}
             [:input {:name "vernacular-name-name"
                      :class "spl-input min-w-0"
                      :aria-label "Vernacular name"
                      :x-model "vn.name"}]
             [:input {:name "vernacular-name-language"
                      :class "spl-input min-w-0"
                      :aria-label "Language"
                      :x-model "vn.language"}]
             (tooltip/wrap
               [:button {:type "button"
                         :class "spl-btn spl-btn--danger spl-btn--icon"
                         :x-on:click "vernacularNames.splice(index, 1); $data.dirty = true;"
                         :aria-label "Delete"}
                [:span {:aria-hidden true}
                 (heroicons/outline-trash)]]
               "Delete"
               :side "left")]]]]]

        (parentage-section :values values
                           :errors errors
                           :read-only read-only)])

     [:script {:type "module"
               :src (html/static-url "app/routes/taxon/form.ts")}]]))
