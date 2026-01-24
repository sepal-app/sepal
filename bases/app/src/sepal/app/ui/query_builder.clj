(ns sepal.app.ui.query-builder
  "Query builder dropdown for advanced search.

   Renders a dropdown below the search input that allows users to
   build search queries visually by selecting fields and values.

   The Alpine.js component logic is in js/query-builder.ts"
  (:require [sepal.app.html :as html]
            [sepal.app.json :as json]
            [sepal.app.ui.icons.lucide :as lucide]))

(defn- field-select
  "Dropdown to select a field to filter on."
  []
  [:select {:class "select select-bordered select-sm w-40 leading-none"
            :x-model "selectedField"
            :x-on:change "onFieldChange()"}
   [:option {:value ""} "Select field..."]
   [:template {:x-for "field in fields" :key "field.key"}
    [:option {:x-bind:value "field.key" :x-text "field.label"}]]])

(defn- operator-select
  "Dropdown to select the comparison operator."
  []
  [:select {:class "select select-bordered select-sm w-44 leading-none"
            :x-model "selectedOpIndex"}
   [:template {:x-for "(op, index) in availableOps" :key "index"}
    [:option {:x-bind:value "index" :x-text "op.label"}]]])

(defn- value-input
  "Input for the filter value - text input or dropdown based on field type."
  []
  [:div {:class "flex-1"}
   ;; Text input for text/fts/date/number/count fields
   [:template {:x-if "currentFieldType === 'text' || currentFieldType === 'fts' || currentFieldType === 'date' || currentFieldType === 'number' || currentFieldType === 'count'"}
    [:input {:type "text"
             :class "input input-bordered input-sm w-full"
             :placeholder "Value..."
             :x-model "selectedValue"}]]
   ;; Dropdown for enum fields
   [:template {:x-if "currentFieldType === 'enum'"}
    [:select {:class "select select-bordered select-sm w-full leading-none"
              :x-model "selectedValue"}
     [:option {:value ""} "Select value..."]
     [:template {:x-for "val in currentFieldValues" :key "val"}
      [:option {:x-bind:value "val" :x-text "val"}]]]]])

(defn- add-button
  "Button to add the filter to the query."
  []
  [:button {:type "button"
            :class "btn btn-primary btn-sm"
            :x-on:click "addFilter()"
            :x-bind:disabled "!canAddFilter"}
   (lucide/plus :class "w-4 h-4")
   "Add"])

(defn query-builder-dropdown
  "Dropdown for building search queries.

   Options:
   - :fields - Sequence of field maps from search.i/field-options
               [{:key \"code\" :label \"Code\" :type :text :values [...]} ...]
   - :input-id - ID of the search input to update (default: \"q\")"
  [& {:keys [fields input-id]
      :or {input-id "q"}}]
  (let [fields-json (json/write-str fields)]
    [:div {:class "relative"
           :x-data (str "queryBuilder(" fields-json ", '" input-id "')")}

     ;; Toggle button
     [:button {:type "button"
               :class (html/attr "btn" "btn-ghost" "btn-sm" "gap-1")
               :x-on:click "open = !open"
               :aria-label "Add filter"}
      (lucide/filter-icon :class "w-4 h-4")
      [:span {:class "hidden sm:inline"} "Filter"]]

     ;; Dropdown panel
     [:div {:class (html/attr "absolute" "top-full" "left-0" "mt-1" "z-50"
                              "bg-base-100" "border" "border-base-300"
                              "rounded-box" "shadow-lg" "p-3"
                              "min-w-80")
            :x-cloak true
            :x-show "open"
            :x-on:click.outside "open = false"
            :x-transition:enter "transition ease-out duration-100"
            :x-transition:enter-start "opacity-0 scale-95"
            :x-transition:enter-end "opacity-100 scale-100"
            :x-transition:leave "transition ease-in duration-75"
            :x-transition:leave-start "opacity-100 scale-100"
            :x-transition:leave-end "opacity-0 scale-95"}

      [:div {:class "text-sm font-medium mb-2"} "Add Filter"]

      [:div {:class "flex flex-col gap-2"}
       ;; Row 1: Field select
       (field-select)

       ;; Row 2: Operator select
       [:div {:x-show "selectedField"}
        (operator-select)]

       ;; Row 3: Value input (shown when field needs a value)
       [:div {:x-show "selectedField && needsValue"}
        (value-input)]

       ;; Row 4: Add button (with extra spacing above)
       [:div {:class "mt-3"
              :x-show "selectedField"}
        (add-button)]]]]))

(defn search-field-with-builder
  "Search field with integrated query builder dropdown.

   Options:
   - :q - Current query string value
   - :fields - Field options for the builder
   - :input-id - ID for the input (default: \"q\")
   - :placeholder - Placeholder text for the input"
  [& {:keys [q fields input-id placeholder]
      :or {input-id "q"
           placeholder "Search..."}}]
  [:div {:class "flex flex-row gap-2 items-center"}
   [:input {:name input-id
            :id input-id
            :class "input input-md bg-white w-96"
            :type "search"
            :value q
            :placeholder placeholder}]
   (query-builder-dropdown :fields fields :input-id input-id)])
