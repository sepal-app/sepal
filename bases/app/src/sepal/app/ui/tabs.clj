(ns sepal.app.ui.tabs
  "Section navigation for a record's edit pages.

  These are links to separate documents — /accession/1/general/,
  /collection/, /media/ — so they are a nav, not a tablist. ARIA tab semantics
  promise a panel in the same document that the tab controls; putting them on
  cross-document links misdescribes the widget to a screen reader."
  (:require [sepal.app.ui.icons.lucide :as lucide]))

(defn item
  "One section link.

  :active   marks the current section, emitting aria-current=page.
  :disabled is the reason the section is unavailable, as a string. A disabled
            item renders as a span rather than an anchor, so there is no href
            to follow, and the reason is a real element referenced by
            aria-describedby — a CSS tooltip reaches neither a keyboard nor a
            screen reader. It keeps tabindex=0 all the same: aria-disabled
            marks a control unavailable without removing it from the tab order,
            and a reason a keyboard cannot reach is a reason nobody hears."
  [label & [{:keys [href active disabled]}]]
  (if disabled
    (let [reason-id (str "tab-reason-" (hash label))]
      [:span {:class "spl-tab spl-tab--disabled"
              :tabindex "0"
              :aria-disabled "true"
              :aria-describedby reason-id}
       label
       ;; Decoration: the reason below already says it in words, so a screen
       ;; reader hearing "Collection, lock" would only be hearing it twice.
       [:span {:class "spl-tab-lock" :aria-hidden "true"} (lucide/lock :size 12)]
       [:span {:id reason-id :class "spl-tab-reason"} disabled]])
    ;; Boosted, so changing section swaps the content column rather than
    ;; reloading the shell. Page scripts register from page.ts for this reason.
    [:a (cond-> {:href href
                 :class (cond-> ["spl-tab"]
                          active (conj "spl-tab--current"))
                 :hx-boost "true"
                 :hx-select ".spl-content"
                 :hx-target ".spl-content"
                 :hx-swap "outerHTML"}
          active (assoc :aria-current "page"))
     label]))

(defn tabs
  "The section nav.

  :label   names the landmark for a screen reader.
  :items   the results of `item`.
  :actions optional record-scoped controls, right-aligned on the same row —
           Delete and the like, which belong to the record rather than to any
           one section."
  [{:keys [label items actions]}]
  [:nav {:class "spl-tabs" :aria-label (or label "Sections")}
   items
   (when actions
     [:span {:class "spl-tabs-actions"} actions])])
