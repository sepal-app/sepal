(ns sepal.app.ui.activity
  (:require [sepal.app.html :as html]
            [sepal.app.ui.icons.lucide :as lucide]
            [sepal.i18n.interface :refer [trc]]))

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

(defn- action
  "The action an activity type records, by the name a person would use: a
  media item is created by uploading it."
  [activity-type]
  (if (= :media/created activity-type) "uploaded" (name activity-type)))

(defn action-label
  "The verb shown for an activity type, translated."
  [activity-type]
  (let [a (action activity-type)]
    (case a
      "created" (trc "activity" "created")
      "uploaded" (trc "activity" "uploaded")
      "updated" (trc "activity" "updated")
      "deleted" (trc "activity" "deleted")
      "completed" (trc "activity" "completed")
      "linked" (trc "activity" "linked")
      "unlinked" (trc "activity" "unlinked")
      "archived" (trc "activity" "archived")
      "unarchived" (trc "activity" "unarchived")
      a)))

(defn action-badge
  "A badge for an activity action — created, updated, deleted, completed.

  Uses the four semantic colours principle 1 permits beyond the accent, and
  always carries the action word as its own text, so the colour is never the
  only carrier of the meaning. The colour follows the activity type rather
  than the word, which changes with the language."
  [activity-type]
  (let [badge-class (case (action activity-type)
                      "created" "spl-badge--ok"
                      "uploaded" "spl-badge--ok"
                      "completed" "spl-badge--ok"
                      "updated" "spl-badge--info"
                      "deleted" "spl-badge--danger"
                      "linked" "spl-badge--info"
                      "spl-badge--neutral")]
    [:span {:class (html/attr "spl-badge" badge-class)}
     (action-label activity-type)]))
