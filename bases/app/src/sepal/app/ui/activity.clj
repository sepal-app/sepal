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
    :taxon (lucide/trees :size size)
    :location (lucide/map-pin :size size)
    :media (lucide/image :size size)
    :note (lucide/pencil :size size)
    :observation (lucide/eye :size size)
    :propagation (lucide/bean :size size)
    :contact (lucide/contact-round)
    :setup (lucide/circle-check :size size)
    :import (lucide/download :size size)
    ;; Default fallback
    nil))

(def ^:private action-labels
  "Where the word a person would use differs from the event's name. A media
  item is created by uploading it."
  {:media/created "uploaded"})

(defn action-label
  "The verb shown for an activity type."
  [activity-type]
  (get action-labels activity-type (name activity-type)))

(defn action-badge
  "A badge for an activity action — created, updated, deleted, completed.

  Uses the four semantic colours principle 1 permits beyond the accent, and
  always carries the action word as its own text, so the colour is never the
  only carrier of the meaning."
  [activity-type]
  (let [action (action-label activity-type)
        badge-class (case action
                      "created" "spl-badge--ok"
                      "uploaded" "spl-badge--ok"
                      "completed" "spl-badge--ok"
                      "updated" "spl-badge--info"
                      "deleted" "spl-badge--danger"
                      "linked" "spl-badge--info"
                      "spl-badge--neutral")]
    [:span {:class (html/attr "spl-badge" badge-class)}
     action]))
