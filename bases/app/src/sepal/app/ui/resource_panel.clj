(ns sepal.app.ui.resource-panel
  "Reusable UI components for the resource panel.
   The resource panel displays comprehensive resource details as either:
   - A sidebar (w-80) alongside list or edit views
   - A main view (max-w-2xl) for read-only users

   Uses DaisyUI components for consistent theming:
   - collapse collapse-arrow for collapsible sections
   - card card-compact for activity items
   - drawer drawer-end for mobile sidebar"
  (:require [sepal.app.datetime :as datetime]
            [sepal.app.html :as html]
            [sepal.app.ui.activity :as ui.activity]
            [sepal.app.ui.icons.lucide :as lucide]))

;;; ---------------------------------------------------------------------------
;;; Collapsible Section
;;; ---------------------------------------------------------------------------

(defn collapsible-section
  "A collapsible section using DaisyUI collapse component.

   Options:
   - :title       - Section header text (required)
   - :count       - Optional count to display in header as '(count)'
   - :children    - Content to show when expanded
   - :default-open? - Whether section starts expanded (default: true)
   - :disabled?   - When true, section is collapsed, grayed out, not interactive
   - :empty-label - Label to show when disabled (default: 'none')"
  [& {:keys [title count children default-open? disabled? empty-label]
      :or {default-open? true
           disabled? false
           empty-label "none"}}]
  [:div {:class (html/attr "spl-collapse  rounded-none"
                           (when disabled? "opacity-50"))}
   ;; Hidden checkbox controls open/closed state
   [:input {:type "checkbox"
            :class "peer"
            :disabled disabled?
            :checked (when default-open? true)}]
   ;; Header (collapse-title)
   [:div {:class (html/attr "spl-collapse-title text-xs font-semibold uppercase tracking-wider min-h-0 py-3 px-4"
                            (if disabled?
                              "text-text-dim cursor-not-allowed"
                              "text-text-soft"))}
    [:span {:class "flex items-center gap-2"}
     title
     (if disabled?
       [:span {:class "text-text-dim normal-case font-normal"}
        (str "(" empty-label ")")]
       (when count
         [:span {:class "text-text-dim normal-case font-normal"}
          (str "(" count ")")]))]]
   ;; Collapsible content
   [:div {:class "spl-collapse-content px-4"}
    children]])

;;; ---------------------------------------------------------------------------
;;; Summary Section
;;; ---------------------------------------------------------------------------

(defn summary-section
  "Key-value details for a resource.

   Uses the shared `spl-kv` pair: a mono caps label in a fixed left column and
   the value beside it. Labels sit in a column so the eye runs down them; the
   previous treatment pushed label and value to opposite edges of the panel,
   which left a ragged gap between them and read as two lists rather than
   pairs.

   Takes a sequence of field maps with :label and :value keys."
  [& {:keys [fields]}]
  [:dl {:class "spl-kv"}
   ;; A seq splices into the parent, which is what puts each dt and dd directly
   ;; in the grid. Chassis has no fragment element — `[:<> …]` renders a
   ;; literal <<>> tag.
   (for [{:keys [label value]} fields
         :when value]
     (list [:dt {:class "spl-k"} label]
           [:dd {:class "spl-v"} value]))])

;;; ---------------------------------------------------------------------------
;;; Statistics Section
;;; ---------------------------------------------------------------------------

(defn stat-item
  "A single statistic with label and value."
  [& {:keys [label value href]}]
  (let [content [:div {:class "flex justify-between items-center"}
                 [:span {:class "text-text-soft text-sm"} label]
                 [:span {:class "text-sm font-semibold"} value]]]
    (if href
      [:a {:href href
           :class "block hover:bg-surface-alt -mx-2 px-2 py-1 rounded transition-colors"}
       content]
      [:div {:class "py-1"} content])))

(defn statistics-section
  "Statistics section showing counts with optional links.
   Takes a sequence of stat maps with :label, :value, and optional :href keys."
  [& {:keys [stats]}]
  [:div {:class "space-y-0"}
   (for [{:keys [label value href]} stats
         :when value]
     ^{:key label}
     (stat-item :label label :value value :href href))])

;;; ---------------------------------------------------------------------------
;;; Linked Resources Section
;;; ---------------------------------------------------------------------------

(defn resource-link
  "A link to a related resource."
  [& {:keys [label href icon]}]
  [:a {:href href
       :class "flex items-center gap-2 text-sm hover:bg-surface-alt -mx-2 px-2 py-1.5 rounded transition-colors"}
   (when icon
     [:span {:class "text-text-dim"} icon])
   [:span {:class "text-primary hover:underline"} label]])

(defn linked-resources-section
  "Section showing links to related resources.
   Takes a sequence of link maps with :label, :href, and optional :icon keys."
  [& {:keys [links]}]
  [:div {:class "space-y-0"}
   (for [{:keys [label href icon]} links
         :when href]
     ^{:key (str href "-" label)}
     (resource-link :label label :href href :icon icon))])

;;; ---------------------------------------------------------------------------
;;; External Links Section
;;; ---------------------------------------------------------------------------

(defn external-link
  "A standard link to an external resource."
  [& {:keys [label href icon]}]
  [:a {:href href
       :target "_blank"
       :rel "noopener noreferrer"
       :class "flex items-center gap-2 text-sm text-primary hover:underline"}
   (when icon
     [:span {:class "text-text-dim"} icon])
   label])

(defn external-links-section
  "Section showing external links.
   Takes a sequence of link maps with :label, :href, and optional :icon keys."
  [& {:keys [links]}]
  [:div {:class "space-y-1"}
   (for [{:keys [label href icon]} links
         :when href]
     ^{:key href}
     (external-link :label label :href href :icon icon))])

;;; ---------------------------------------------------------------------------
;;; Activity Section
;;; ---------------------------------------------------------------------------

(defn activity-item-compact
  "Compact activity item for the resource panel.
   Shows badge + time + user only. Uses DaisyUI card component."
  [activity timezone]
  [:div {:class "spl-card  bg-surface shadow-sm"}
   [:div {:class "spl-card-body p-3"}
    ;; Top row: badge + relative time
    [:div {:class "flex items-center justify-between"}
     (ui.activity/action-badge (:activity/type activity))
     (datetime/relative-time (:activity/created-at activity) timezone
                             :class "text-sm text-text-soft")]
    ;; Bottom row: user email
    [:div {:class "text-sm text-text-soft"}
     (:user/email (:activity/user activity))]]])

(defn activity-section
  "Activity section for the resource panel.

   Options:
   - :activities   - Sequence of activity maps to display
   - :total-count  - Total number of activities (for 'Load more' button)
   - :load-more-url - URL for loading more activities via HTMX
   - :timezone     - Timezone string for formatting timestamps"
  [& {:keys [activities total-count load-more-url timezone]}]
  (let [showing (count activities)
        remaining (when total-count (- total-count showing))]
    [:div {:class "space-y-2"}
     ;; Activity items
     (for [activity activities]
       ^{:key (:activity/id activity)}
       (activity-item-compact activity timezone))
     ;; Load more button
     (when (and load-more-url remaining (pos? remaining))
       [:button {:class "spl-btn spl-btn--ghost spl-btn--sm w-full"
                 :hx-get load-more-url
                 :hx-target "closest .space-y-2"
                 :hx-swap "beforeend"}
        (str "Load " remaining " more")])]))

;;; ---------------------------------------------------------------------------
;;; Panel Container
;;; ---------------------------------------------------------------------------

(defn panel-container
  "Container for the resource panel content.
   Wraps content with appropriate styling for sidebar or main view context.

   Options:
   - :children - Panel content
   - :class    - Additional CSS classes"
  [& {:keys [children class]}]
  [:div {:class (html/attr "divide-y divide-base-200" class)}
   children])

(defn panel-header
  "Header for the resource panel showing resource title.

   Options:
   - :title     - Main title (e.g., accession code)
   - :subtitle  - Optional subtitle (e.g., taxon name)
   - :on-close  - When provided, shows close button (for list page panel)"
  [& {:keys [title subtitle on-close]}]
  ;; Same identity treatment as a record page's header: the identifier in mono
  ;; brand green above the name. The panel and the page it opens from should
  ;; not label the same record two different ways.
  [:div {:class "spl-panel-header"}
   [:div {:class "spl-panel-identity"}
    [:p {:class "spl-panel-code"} title]
    (when subtitle
      [:p {:class "spl-panel-name"} subtitle])]
   (when on-close
     [:button {:class "spl-panel-close"
               :type "button"
               :data-panel-close ""
               :aria-label "Close panel"
               :x-on:click on-close}
      (lucide/x :class "w-4 h-4")])])
