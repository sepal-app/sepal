(ns sepal.app.ui.activity
  (:require [sepal.app.html :as html]
            [sepal.app.ui.icons.lucide :as lucide]))

(defn resource-icon
  "Return the appropriate icon for a resource type.
   Resource types match the activity type namespace (e.g., :accession, :taxon).

   Same icon for a resource here as in the section rail — see
   `sepal.app.ui.page/sections`. A feed row and a rail entry naming the same
   thing with different glyphs teaches the reader nothing."
  [resource-type & {:keys [size] :or {size 20}}]
  (case resource-type
    :accession (lucide/clipboard-list :size size)
    :material (lucide/sprout :size size)
    :taxon (lucide/flower-2 :size size)
    :location (lucide/map-pin :size size)
    :media (lucide/image :size size)
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
