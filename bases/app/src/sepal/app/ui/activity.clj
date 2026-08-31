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
  "A badge for an activity action — created, updated, deleted, completed.

  Uses the four semantic colours principle 1 permits beyond the accent, and
  always carries the action word as its own text, so the colour is never the
  only carrier of the meaning."
  [activity-type]
  (let [action (name activity-type)
        badge-class (case action
                      "created" "spl-badge--ok"
                      "completed" "spl-badge--ok"
                      "updated" "spl-badge--info"
                      "deleted" "spl-badge--danger"
                      "spl-badge--neutral")]
    [:span {:class (html/attr "spl-badge" badge-class)}
     action]))
