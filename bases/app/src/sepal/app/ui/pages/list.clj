(ns sepal.app.ui.pages.list
  (:require [sepal.app.ui.icons.heroicons :as heroicons]
            [sepal.app.ui.icons.lucide :as lucide]))

(def list-container-id "list-container")

(defn filter-badge
  "A single filter badge with label, value, and clear button.

   Options:
   - :label      - Filter label (e.g., \"Taxon\")
   - :value      - Filter value to display (e.g., \"Quercus alba\")
   - :clear-href - URL to navigate to when clearing this filter"
  [{:keys [label value clear-href]}]
  [:div {:class "badge badge-neutral gap-1"}
   [:span (str label ": ")]
   [:span {:class "font-semibold"} value]
   [:a {:href clear-href
        :class "hover:text-error"
        :aria-label (str "Clear " label " filter")}
    (lucide/x :class "w-3 h-3")]])

(defn filter-badges
  "Renders a list of active filter badges.

   Options:
   - :filters - Sequence of filter maps with :label, :value, :clear-href"
  [filters]
  (when (seq filters)
    [:div {:class "flex flex-wrap gap-2 mt-2"}
     (for [filter filters]
       (filter-badge filter))]))

(defn search-field [q]
  [:div {:class "flex flex-row"}
   [:input {:name "q"
            :class "input input-md w-fill max-w-xs bg-white w-96"
            :type "search"
            :value q
            :placeholder "Search..."}]
   [:button
    {:type "button",
     :class ["inline-flex" "items-center" "mx-2" "px-2.5" "py-1.5" "border"
             "border-gray-300" "text-xs" "font-medium" "rounded"
             "text-gray-700" "bg-white" "hover:bg-gray-50" "focus:outline-none"
             "focus:ring-2" "focus:ring-offset-2" "focus:ring-indigo-500"]
     :onclick "document.getElementById('q').value = null; this.form.submit()"}
    (heroicons/outline-x :size 20)]])

(defn page-content [& {:keys [table-actions content]}]
  [:form {:method "get"
          :hx-get " "
          :hx-trigger "keyup delay:200ms,change"
          :hx-select (str "#" list-container-id)
          :hx-target (str "#" list-container-id)
          :hx-push-url "true"
          :hx-swap "outerHTML"}
   [:div {:class "w-full mt-8"}
    table-actions]
   [:div {:id list-container-id
          :class "mt-4 flex flex-col"}
    content]])

(def panel-container-id "preview-panel-content")

(defn page-content-with-panel
  "List page content with optional preview panel.

   Options:
   - :table-actions - Action buttons/forms above table
   - :content       - Main table content"
  [& {:keys [table-actions content]}]
  [:div {:x-data "{ panelOpen: false, selectedId: null }"}
   [:div {:class "mt-8"}
    [:form {:method "get"
            :hx-get " "
            :hx-trigger "keyup delay:200ms,change"
            :hx-select (str "#" list-container-id)
            :hx-target (str "#" list-container-id)
            :hx-push-url "true"
            :hx-swap "outerHTML"}
     [:div {:class "w-full"}
      table-actions]]
    ;; Table and panel in same row, outside the form
    [:div {:class "mt-4 flex flex-row gap-8"}
     ;; Table content
     [:div {:id list-container-id
            :class "flex-1 min-w-0"}
      content]
     ;; Preview panel - hidden until a row is selected
     [:div {:class "w-80 shrink-0 bg-base-100 border-1 rounded-(--radius-box) border-base-300 overflow-y-auto"
            :x-show "selectedId"}
      ;; Panel header with close button
      [:div {:class "sticky top-0 bg-base-100 border-b border-base-300 p-2 flex justify-end"}
       [:button {:class "btn btn-ghost btn-sm btn-square"
                 :x-on:click "panelOpen = false; selectedId = null"
                 :aria-label "Close panel"}
        (lucide/x :class "w-5 h-5")]]
      ;; Panel content - loaded via HTMX
      [:div {:id panel-container-id}]]]]])
