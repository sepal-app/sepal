(ns sepal.app.routes.taxon.form
  (:require [sepal.app.html :as html]
            [sepal.app.json :as json]
            [sepal.app.routes.taxon.routes :as taxon.routes]
            [sepal.app.ui.form :as form]
            [sepal.taxon.interface.spec :as taxon.spec]
            [zodiac.core :as z]))

(defn footer-buttons []
  [[:button {:class "btn btn-primary"
             :x-on:click "$dispatch('taxon-form:submit')"
             :x-bind:disabled "!valid"}
    "Save"]
   [:button {:class "btn btn-secondary"
             ;; TODO: form.reset() would be better but it doesn't reset the TomSelect of the rank field
             ;; :x-on:click "dirty && confirm('Are you sure you want to lose your changes?') && $refs.taxonForm.reset()"
             :x-on:click "confirm('Are you sure you want to lose your changes?') && location.reload()"}
    "Cancel"]])

(defn form [& {:keys [action errors read-only values]}]
  (let [ranks (->> taxon.spec/rank rest (mapv name))]
    [:div
     (form/form
       {:action action
        :method "POST"
        :id "taxon-form"
        :x-on:taxon-form:submit.window "$el.submit()"
        :x-on:taxon-form:reset.window "$el.reset()"}
       [(form/anti-forgery-field)
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
                          :errors (:author errors))

        (if read-only
          (form/input-field :label "Parent"
                            :name "parent-id"
                            :required true
                            :read-only read-only
                            :value (:parent-name values))

          (let [url (z/url-for taxon.routes/index)]
            (form/field :label "Parent"
                        :name "parent-id"
                        :input [:select {:x-taxon-field (json/js {:url url})
                                         :class "select select-bordered select-md w-full max-w-xs px-2"
                                         :name "parent-id"
                                         :id "parent-id"
                                         :required true
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
                                       :x-rank-field (json/js {:test "test"})
                                       :class "select select-bordered select-md w-full max-w-xs px-2"
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
     [:script {:type "module"
               :src (html/static-url "app/routes/taxon/form.ts")}]]))
