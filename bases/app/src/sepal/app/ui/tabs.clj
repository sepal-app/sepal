(ns sepal.app.ui.tabs
  "Section navigation for a record's edit pages.

  These are links to separate documents — /accession/1/general/,
  /collection/, /media/ — so they are a nav, not a tablist. ARIA tab semantics
  promise a panel in the same document that the tab controls; putting them on
  cross-document links misdescribes the widget to a screen reader.")

(defn item
  "One section link.

  :active   marks the current section, emitting aria-current=page.
  :disabled is the reason the section is unavailable, as a string. A disabled
            item renders as a span rather than an anchor, so it is neither
            focusable nor activatable, and the reason is a real element
            referenced by aria-describedby — a CSS tooltip reaches neither a
            keyboard nor a screen reader."
  [label & [{:keys [href active disabled]}]]
  (if disabled
    (let [reason-id (str "tab-reason-" (hash label))]
      [:span {:class "spl-tab spl-tab--disabled"
              :aria-disabled "true"
              :aria-describedby reason-id}
       label
       [:span {:id reason-id :class "spl-tab-reason"} disabled]])
    [:a (cond-> {:href href
                 :class (cond-> ["spl-tab"]
                          active (conj "spl-tab--current"))}
          active (assoc :aria-current "page"))
     label]))

(defn tabs
  "The section nav. `:label` names the landmark for a screen reader; `:items`
  are the results of `item`."
  [{:keys [label items]}]
  [:nav {:class "spl-tabs" :aria-label (or label "Sections")}
   items])
