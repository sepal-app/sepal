(ns sepal.app.routes.taxon.form
  (:require [sepal.app.html :as html]
            [sepal.app.json :as json]
            [sepal.app.routes.taxon.routes :as taxon.routes]
            [sepal.app.ui.form :as form]
            [sepal.app.ui.icons.heroicons :as heroicons]
            [sepal.app.ui.tooltip :as tooltip]
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

(defn- vernacular-name-decoder [form-data]
  (let [names (cond-> (:vernacular-name-name form-data)
                (-> form-data :vernacular-name-name string?)
                vector)
        langs (cond-> (:vernacular-name-language form-data)
                (-> form-data :vernacular-name-language string?)
                vector)
        vernacular-names (mapv (fn [name lang]
                                 {:name name
                                  :language lang})
                               names langs)]
    (-> form-data
        (assoc :vernacular-names vernacular-names)
        (dissoc :vernacular-name-name
                :vernacular-name-language))))

(def FormParams
  [:and
   [:map {:decode/form {:enter vernacular-name-decoder}}
    [:name [:string {:min 1}]]
    [:author :string]
    [:rank [:string {:min 1}]]
    [:parent-id {:optional true} [:maybe :string]]
    [:distribution {:optional true :decode/form validation.i/empty->nil} [:maybe :string]]
    [:vernacular-names [:* [:map
                            [:name [:string {:min 1}]]
                            [:language [:maybe :string]]]]]]])

(defn form
  "The create page and the edit page share this.

  `guess-rank-url` is create-only. Given it, the Name field asks the server
  what rank the name implies and the page fills Rank in — until you set Rank
  yourself, after which it stops. The edit form never passes it, so nothing
  can quietly re-rank a taxon you are only renaming."
  [& {:keys [action errors read-only values guess-rank-url]}]
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
               (form/field :label "Parent"
                           :name "parent-id"
                           :input [:select {:x-taxon-field (json/js {:url url})
                                            :name "parent-id"
                                            :id "parent-id"
                                            :read-only read-only
                                            :autocomplete "off"}
                                   ;; A single select must hold a selection, so
                                   ;; without this SlimSelect selects the first
                                   ;; search result and hideSelected hides it —
                                   ;; a search matching one taxon showed an
                                   ;; empty list. It doubles as "no parent".
                                   [:option {:value "" :data-placeholder "true"} ""]
                                   (when (:parent-id values)
                                     ;; `selected` is load-bearing: the
                                     ;; placeholder is the first option, and a
                                     ;; browser takes the first one unless told
                                     ;; otherwise.
                                     [:option {:value (:parent-id values)
                                               :selected "selected"}
                                      (:parent-name values)])])))
           (if read-only
             (form/input-field :label "Rank"
                               :name "rank"
                               :read-only read-only
                               :value (:rank values))
             (form/field :label "Rank"
                         :name "rank"
                         :input [:select {:name "rank"
                                          :x-rank-field {}
                                          :autocomplete "off"
                                          :id "rank"
                                          :read-only read-only
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
                    :x-data (json/js {:vernacularNames (or (:vernacular-names values)
                                                           [])})}
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
           [:div {:x-show "vernacularNames?.length"
                  :class "grid grid-cols-[1fr_1fr_32px] gap-2 items-center"}
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
               :side "left")]]]
          ;; Inside the section, so it takes the same 576px column as the
          ;; fields. As a bare div it ran the full width of the page.
          [:p {:x-show "!vernacularNames?.length"
               :class "spl-help"}
           "None yet."]]]])

     [:script {:type "module"
               :src (html/static-url "app/routes/taxon/form.ts")}]]))
