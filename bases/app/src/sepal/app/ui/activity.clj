(ns sepal.app.ui.activity
  (:require [sepal.app.html :as html]
            [sepal.app.ui.icons.bootstrap :as bootstrap]
            [sepal.app.ui.icons.heroicons :as heroicons]
            [sepal.app.ui.icons.lucide :as lucide]))

(defn resource-icon
  "Return the appropriate icon for a resource type.
   Resource types match the activity type namespace (e.g., :accession, :taxon)."
  [resource-type & {:keys [size] :or {size 20}}]
  (case resource-type
    :accession (heroicons/outline-rectangle-group :size size)
    :material (heroicons/outline-tag :size size)
    :taxon (bootstrap/flower1 :size size)
    :location (heroicons/outline-map-pin :size size)
    :media (heroicons/outline-photo :size size)
    :contact (lucide/contact-round)
    :setup (lucide/circle-check :size size)
    ;; Default fallback
    nil))

(defn action-badge
  "Render a soft-style colored badge for an activity action type.
   Extracts the action from the activity type keyword (e.g., :accession/created -> created)."
  [activity-type]
  (let [action (name activity-type)
        badge-class (case action
                      "created" "badge-success"
                      "updated" "badge-info"
                      "deleted" "badge-error"
                      "completed" "badge-success"
                      "badge-ghost")]
    [:span {:class (html/attr "badge" "badge-soft" "badge-sm" badge-class)}
     action]))
