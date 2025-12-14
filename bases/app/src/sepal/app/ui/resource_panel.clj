(ns sepal.app.ui.resource-panel
  "Reusable UI components for the resource panel.
   The resource panel displays comprehensive resource details as either:
   - A sidebar (w-80) alongside list or edit views
   - A main view (max-w-2xl) for read-only users

   Uses DaisyUI components for consistent theming:
   - collapse collapse-arrow for collapsible sections
   - card card-compact for activity items
   - drawer drawer-end for mobile sidebar"
  (:require [sepal.app.html :as html]
            [sepal.app.routes.activity.index :as activity.index]
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
  [:div {:class (html/attr "collapse collapse-arrow rounded-none"
                           (when disabled? "opacity-50"))}
   ;; Hidden checkbox controls open/closed state
   [:input {:type "checkbox"
            :class "peer"
            :disabled disabled?
            :checked (when default-open? true)}]
   ;; Header (collapse-title)
   [:div {:class (html/attr "collapse-title text-xs font-semibold uppercase tracking-wider min-h-0 py-3 px-4"
                            (if disabled?
                              "text-base-content/60 cursor-not-allowed"
                              "text-base-content/90"))}
    [:span {:class "flex items-center gap-2"}
     title
     (if disabled?
       [:span {:class "text-base-content/30 normal-case font-normal"}
        (str "(" empty-label ")")]
       (when count
         [:span {:class "text-base-content/40 normal-case font-normal"}
          (str "(" count ")")]))]]
   ;; Collapsible content
   [:div {:class "collapse-content px-4"}
    children]])

;;; ---------------------------------------------------------------------------
;;; Summary Section
;;; ---------------------------------------------------------------------------

(defn summary-field
  "A single field in the summary section.
   Options:
   - :label - Field label
   - :value - Field value (can be hiccup)"
  [& {:keys [label value]}]
  (when value
    [:div {:class "flex justify-between items-baseline gap-2"}
     [:dt {:class "text-base-content/80 text-sm"} label]
     [:dd {:class "text-sm font-medium text-right"} value]]))

(defn summary-section
  "Summary section showing key resource details.
   Takes a sequence of field maps with :label and :value keys."
  [& {:keys [fields]}]
  [:dl {:class "space-y-1"}
   (for [{:keys [label value]} fields
         :when value]
     ^{:key label}
     (summary-field :label label :value value))])

;;; ---------------------------------------------------------------------------
;;; Statistics Section
;;; ---------------------------------------------------------------------------

(defn stat-item
  "A single statistic with label and value."
  [& {:keys [label value href]}]
  (let [content [:div {:class "flex justify-between items-center"}
                 [:span {:class "text-base-content/80 text-sm"} label]
                 [:span {:class "text-sm font-semibold"} value]]]
    (if href
      [:a {:href href
           :class "block hover:bg-base-200 -mx-2 px-2 py-1 rounded transition-colors"}
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
       :class "flex items-center gap-2 text-sm hover:bg-base-200 -mx-2 px-2 py-1.5 rounded transition-colors"}
   (when icon
     [:span {:class "text-base-content/40"} icon])
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
     [:span {:class "text-base-content/40"} icon])
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
  [activity]
  [:div {:class "card card-compact bg-base-100 shadow-sm"}
   [:div {:class "card-body p-3"}
    ;; Top row: badge + relative time
    [:div {:class "flex items-center justify-between"}
     (ui.activity/action-badge (:activity/type activity))
     [:time {:class "text-sm text-base-content/80"
             :title (activity.index/format-full-datetime
                      (:activity/created-at activity) nil)}
      (activity.index/relative-time (:activity/created-at activity))]]
    ;; Bottom row: user email
    [:div {:class "text-sm text-base-content/80"}
     (:user/email (:activity/user activity))]]])

(defn activity-section
  "Activity section for the resource panel.

   Options:
   - :activities   - Sequence of activity maps to display
   - :total-count  - Total number of activities (for 'Load more' button)
   - :load-more-url - URL for loading more activities via HTMX"
  [& {:keys [activities total-count load-more-url]}]
  (let [showing (count activities)
        remaining (when total-count (- total-count showing))]
    [:div {:class "space-y-2"}
     ;; Activity items
     (for [activity activities]
       ^{:key (:activity/id activity)}
       (activity-item-compact activity))
     ;; Load more button
     (when (and load-more-url remaining (pos? remaining))
       [:button {:class "btn btn-ghost btn-sm w-full"
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
  [:div {:class (html/attr "divide-y divide-base-300" class)}
   children])

(defn panel-header
  "Header for the resource panel showing resource title.

   Options:
   - :title     - Main title (e.g., accession code)
   - :subtitle  - Optional subtitle (e.g., taxon name)
   - :on-close  - When provided, shows close button (for list page panel)"
  [& {:keys [title subtitle on-close]}]
  [:div {:class "flex items-start justify-between gap-2 p-4"}
   [:div
    [:h2 {:class "text-lg font-semibold"} title]
    (when subtitle
      [:p {:class "text-sm text-base-content/80"} subtitle])]
   (when on-close
     [:button {:class "btn btn-ghost btn-sm btn-square"
               :x-on:click on-close}
      (lucide/x :class "w-5 h-5")])])
