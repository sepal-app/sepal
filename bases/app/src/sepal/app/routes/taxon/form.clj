(ns sepal.app.routes.taxon.form
  (:require [sepal.app.html :as html]
            [sepal.app.json :as json]
            [sepal.app.routes.taxon.routes :as taxon.routes]
            [sepal.app.ui.form :as form]
            [sepal.app.ui.icons.heroicons :as heroicons]
            [sepal.taxon.interface.spec :as taxon.spec]
            [zodiac.core :as z]))

(defn footer-buttons []
  (form/footer-buttons :form-event "taxon-form" :on-cancel :reload))

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
    [:vernacular-names [:* [:map
                            [:name [:string {:min 1}]]
                            [:language [:maybe :string]]]]]]])

(defn form [& {:keys [action errors read-only values]}]
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
                           :errors (:name errors))
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
                                 (when (:parent-id values)
                                   [:option {:value (:parent-id values)}
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
                                  rank])]))])

        [:fieldset {:class "spl-form-section spl-fieldset"
                    :x-data (json/js {:vernacularNames (or (:vernacular-names values)
                                                           [])})}
         [:legend {:class "spl-form-section-title flex items-center gap-2"}
          "Vernacular names"
          [:button {:type "button"
                    :class "spl-btn spl-btn--sm spl-btn--icon"
                    :x-on:click "vernacularNames.push({id: -1}); $data.dirty = true;"
                    :aria-label "Add vernacular name"}
           [:span {:aria-hidden true}
            (heroicons/plus-mini)]]]
         [:div {:class "spl-form-fields"}
          [:template {:x-for "(vn, index) in vernacularNames"}
           [:div {:class "flex flex-row gap-2 items-center"}
            [:input {:name "vernacular-name-name"
                     :class "spl-input flex-grow"
                     :aria-label "Vernacular name"
                     :x-model "vn.name"}]
            [:input {:name "vernacular-name-language"
                     :class "spl-input flex-grow"
                     :aria-label "Language"
                     :x-model "vn.language"}]
            [:button {:type "button"
                      :class "spl-btn spl-btn--danger spl-btn--icon"
                      :x-on:click "vernacularNames.splice(index, 1); $data.dirty = true;"
                      :aria-label "Delete"}
             [:span {:aria-hidden true}
              (heroicons/outline-trash)]]]]
          ;; Inside the section, so it takes the same 576px column as the
          ;; fields. As a bare div it ran the full width of the page.
          [:p {:x-show "!vernacularNames?.length"
               :class "spl-help"}
           "None yet."]]]])

     [:script {:type "module"
               :src (html/static-url "app/routes/taxon/form.ts")}]]))
