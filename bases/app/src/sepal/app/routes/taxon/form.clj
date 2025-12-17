(ns sepal.app.routes.taxon.form
  (:require [sepal.app.html :as html]
            [sepal.app.json :as json]
            [sepal.app.routes.taxon.routes :as taxon.routes]
            [sepal.app.ui.form :as form]
            [sepal.app.ui.icons.heroicons :as heroicons]
            [sepal.taxon.interface.spec :as taxon.spec]
            [zodiac.core :as z]))

(defn footer-buttons []
  [[:button {:class "btn"
             ;; TODO: form.reset() would be better but it doesn't reset the TomSelect of the rank field
             ;; :x-on:click "dirty && confirm('Are you sure you want to lose your changes?') && $refs.taxonForm.reset()"
             :x-on:click "confirm('Are you sure you want to lose your changes?') && location.reload()"}
    "Cancel"]
   [:button {:class "btn btn-primary"
             :x-on:click "$dispatch('taxon-form:submit')"
             :x-bind:disabled "!valid"}
    "Save"]])

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
    [:parent-id [:maybe :string]]
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
       [(form/anti-forgery-field)
        [:div {:class "flex flex-row md:flex-nowrap sm:flex-wrap gap-2"}
         (form/input-field :label "Name"
                           :name "name"
                           :required true
                           :read-only read-only
                           :value (:name values)
                           :errors (:name errors))
         (form/input-field :label "Author"
                           :name "author"
                           :read-only read-only
                           :value (:author values)
                           :errors (:author errors))]

        (if read-only
          [:div {:class "w-1/2"}
           (form/input-field :label "Parent"
                             :name "parent-id"
                             ;; :required true
                             :read-only read-only
                             :value (:parent-name values))]

          (let [url (z/url-for taxon.routes/index)]
            [:div {:class "w-1/2"}
             (form/field :label "Parent"
                         :name "parent-id"
                         :input [:select {:x-taxon-field (json/js {:url url})
                                          :name "parent-id"
                                          :id "parent-id"
                                          ;; :required true
                                          :read-only read-only
                                          :autocomplete "off"}
                                 (when (:parent-id values)
                                   [:option {:value (:parent-id values)}
                                    (:parent-name values)])])]))

        (if read-only
          [:div {:class "w-1/2"}
           (form/input-field :label "Rank"
                             :name "rank"
                             :read-only read-only
                             :value (:rank values))]
          [:div  {:class "w-1/2"}
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
                                  rank])])])]
       [:fieldset {:class "fieldset mt-6"
                   :x-data (json/js {:vernacularNames (or (:vernacular-names values)
                                                          [])})}
        [:legend {:class "fieldset-legend text-md"}
         "Vernacular names"
         [:button {:type "button"
                   :class "btn btn-xs btn-circle"
                   :x-on:click "vernacularNames.push({id: -1}); $data.dirty = true;"
                   :aria-label "Add vernacular name"}
          [:span {:aria-hidden true}
           (heroicons/plus-mini)]]]
        [:div {:class "flex flex-col gap-2"}
         [:template {:x-for "(vn, index) in vernacularNames"}
          [:div {:class "flex flex-row gap-2 items-center"}
           [:input {:name "vernacular-name-name"
                    :class "input flex-grow"
                    :x-model "vn.name"}]
           [:input {:name "vernacular-name-language"
                    :class "input flex-grow"
                    :x-model "vn.language"}]
           [:button {:type "button"
                     :class "btn btn-soft btn-error hover:text-white"
                     :x-on:click "vernacularNames.splice(index, 1); $data.dirty = true;"
                     :aria-label "Delete"}
            [:span {:aria-hidden true}
             (heroicons/outline-trash)]]]]]
        [:div {:x-show "!vernacularNames?.length"
               :class "bg-blue-50 p-6 rounded-xl"}
         "This taxon doesn't have any vernacular names"]])

     [:script {:type "module"
               :src (html/static-url "app/routes/taxon/form.ts")}]]))
