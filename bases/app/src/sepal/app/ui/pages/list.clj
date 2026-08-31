(ns sepal.app.ui.pages.list
  (:require [sepal.app.ui.icons.heroicons :as heroicons]
            [sepal.app.ui.icons.lucide :as lucide]
            [sepal.app.ui.empty :as ui.empty]
            [sepal.app.ui.query-builder :as query-builder]
            [sepal.app.ui.table :as table]))

(def list-container-id "list-container")

(defn filter-badge
  "A single filter badge with label, value, and clear button.

   Options:
   - :label      - Filter label (e.g., \"Taxon\")
   - :value      - Filter value to display (e.g., \"Quercus alba\")
   - :clear-href - URL to navigate to when clearing this filter"
  [{:keys [label value clear-href]}]
  [:div {:class "spl-badge spl-badge--neutral gap-1"}
   [:span (str label ": ")]
   [:span {:class "font-semibold"} value]
   [:a {:href clear-href
        :class "hover:text-danger"
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

(defn search-field
  "A search box with a clear button.

  Nothing calls this — every list uses
  `query-builder/search-field-with-builder` instead. Left in place rather than
  removed, but it is worth deleting."
  [q]
  [:div {:class "spl-search"}
   [:input {:name "q"
            :class "spl-input spl-input--search"
            :type "search"
            :value q
            :placeholder "Search..."}]
   [:button
    {:type "button"
     :class "spl-btn spl-btn--ghost spl-btn--sm"
     :aria-label "Clear search"
     :onclick "document.getElementById('q').value = null; this.form.submit()"}
    (heroicons/outline-x :size 20)]])

(defn create-button
  "The primary action in a list's top bar.

  `label` defaults to \"Create\" — the word every list but Accessions used,
  which said \"New accession\" for no reason anyone recorded.

  The default is `or`, not `:or`: `:or` fills a key that is absent, and callers
  pass `:label nil` when they have nothing to say. That rendered a green pill
  with no text in it."
  [& {:keys [href label]}]
  [:a {:class "spl-btn spl-btn--primary"
       :href href}
   (or label "Create")])

(defn empty-list
  "What a list shows when it has no rows.

  A search that matched nothing is a different situation from a resource you
  have not created yet: one wants the query changed, the other wants the first
  record. Offering \"Create\" to someone whose search just missed is the wrong
  advice."
  [& {:keys [noun body searching? create-href create-label]}]
  (if searching?
    (ui.empty/empty-state
      :title "Nothing matched"
      :body "No results for that search. Try fewer terms, or clear the filters.")
    (ui.empty/empty-state
      :title (str "No " noun " yet")
      :body body
      :actions (when create-href
                 (create-button :href create-href :label create-label)))))

(defn toolbar
  "The bar above every list: search on the left, the row count and the actions
  on the right.

  Options:
  - :q, :fields, :placeholder    the search field and its query builder
  - :filters                     extra controls beside the search
  - :page, :page-size, :total    the row count
  - :actions                     buttons at the right end

  One definition rather than five, so the lists cannot drift apart on spacing,
  on how the loaded count is derived, or on where the bar breaks when it wraps
  onto a second line."
  [& {:keys [q fields placeholder filters page page-size total actions]}]
  (list
    [:div {:class "spl-toolbar-search"}
     (query-builder/search-field-with-builder :q q
                                              :fields fields
                                              :placeholder placeholder)
     filters]
    [:div {:class "spl-toolbar-end"}
     (table/row-count :loaded (min (* page page-size) total) :total total)
     actions]))

(defn page-content [& {:keys [table-actions content]}]
  [:form {:method "get"
          :hx-get " "
          :hx-trigger "keyup delay:200ms,change"
          :hx-select (str "#" list-container-id)
          :hx-target (str "#" list-container-id)
          :hx-push-url "true"
          :hx-swap "outerHTML"}
   [:div {:class "spl-toolbar"}
    table-actions]
   [:div {:id list-container-id
          :class "spl-list"}
    content]])

(def panel-container-id "preview-panel-content")

(defn row-attrs
  "Attributes that make a list row open the resource panel when clicked.

  One definition rather than five. Four of the lists had the selected class set
  to the same surface-alt the hover state uses, so clicking a row changed
  nothing you could see.

  The click is an enhancement — the anchor in the first cell is the real
  affordance and is what a keyboard user reaches."
  [& {:keys [id panel-url]}]
  {:class "spl-row"
   :x-bind:class (str "selectedId === " id " ? 'spl-row--selected' : ''")
   :x-on:click (str "selectRow(" id ", $el)")
   :hx-get panel-url
   :hx-trigger "panel-select"
   :hx-target (str "#" panel-container-id)
   :hx-swap "innerHTML"
   :hx-push-url "false"})

(defn page-content-with-panel
  "List page content with optional preview panel.

   Options:
   - :table-actions - Action buttons/forms above table
   - :content       - Main table content"
  [& {:keys [table-actions content]}]
  ;; Classed and stretched: as a bare block div this sized to its content and
  ;; broke the height chain from the viewport down to the scrolling rows, which
  ;; is what stopped infinite scroll from ever firing.
  [:div {:class "spl-list-page"
         :x-data "{ selectedId: null,
                    selectRow(id, el) {
                      if (this.selectedId === id) { this.closePanel(); }
                      else { this.selectedId = id; el.dispatchEvent(new Event('panel-select')); }
                    },
                    closePanel() {
                      this.selectedId = null;
                      document.getElementById('preview-panel-content').innerHTML = '';
                    } }"}
   ;; The toolbar is a bar under the top bar, not a padded band — search,
   ;; filters and export sit on the same surface as the breadcrumbs, and the
   ;; table starts immediately beneath. This is the shape the workbench mockup
   ;; has: chrome, then data.
   [:div {:class "spl-list-body"}
    [:form {:class "spl-toolbar"
            :method "get"
            :hx-get " "
            :hx-trigger "keyup delay:200ms,change"
            :hx-select (str "#" list-container-id)
            :hx-target (str "#" list-container-id)
            :hx-push-url "true"
            :hx-swap "outerHTML"
            :x-on:htmx:before-request (str "selectedId = null; document.getElementById('" panel-container-id "').innerHTML = ''")}
     table-actions]
    ;; Table and panel in same row, outside the form
    [:div {:class "spl-panes"}
     ;; Table content
     [:div {:id list-container-id
            :class "spl-list"}
      content]
     ;; Preview panel — appears on row click, and must be dismissible. Escape
     ;; closes it too, so a keyboard user is never trapped with it open.
     [:div {:class "spl-panel"
            :x-show "selectedId"
            :x-cloak ""
            :x-on:keydown.escape.window "closePanel()"}
      [:div {:class "spl-panel-head"}
       [:button {:type "button"
                 :class "spl-panel-close"
                 :data-panel-close ""
                 :aria-label "Close panel"
                 :x-on:click "closePanel()"}
        "✕"]]
      ;; Panel content - loaded via HTMX
      [:div {:id panel-container-id}]]]]])
