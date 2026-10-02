(ns sepal.app.ui.notes
  "Markup for resource notes: the tab's form and list, and the read-only panel
  section. One namespace because accession, material and taxon all render the
  same note; only the URLs differ."
  (:require [ring.middleware.anti-forgery :refer [*anti-forgery-token*]]
            [sepal.app.datetime :as datetime]
            [sepal.app.json :as json]
            [sepal.app.ui.avatar :as ui.avatar]
            [sepal.app.ui.button :as ui.button]
            [sepal.app.ui.form :as ui.form]
            [sepal.app.ui.icons.lucide :as lucide]
            [sepal.app.ui.page :as ui.page]
            [sepal.i18n.interface :as i18n :refer [tr]]))

(defn- written-at
  "When a note was written, in the garden's timezone, with the full time as a
  tooltip. Falls back to the stored text for a value that doesn't parse."
  [s timezone]
  (or (some-> s datetime/sqlite-datetime->instant (datetime/datetime timezone))
      s))

(defn- note-item
  "One note as a timeline entry, and an edit form for it hidden behind an
  Alpine flag. Carrying the form here rather than fetching it saves a route
  and a handler on each of the three resources."
  [& {:keys [note note-url-fn timezone]}]
  (let [{:note/keys [id body created-at author-email]} note
        url (note-url-fn id)
        instant (datetime/sqlite-datetime->instant created-at)]
    [:div {:class "spl-changelog-entry"
           :key id
           :data-note-id id
           :x-data (json/js {:editing false})
           :x-on:form-saved "editing = false"}
     [:div {:class "spl-changelog-avatar"}
      (if author-email
        (ui.avatar/avatar :email author-email :size :sm)
        [:span {:class "flex size-8 items-center justify-center rounded-full bg-surface-alt text-text-dim"
                :aria-hidden "true"}
         (lucide/clipboard-list :class "size-4")])]
     [:div {:class "spl-changelog-body"}
      [:div {:x-show "!editing"}
       [:div {:class "spl-entry-head"}
        [:p {:class "spl-changelog-line"}
         ;; The 1,643 notes imported from Bauble have no author. The element
         ;; is absent rather than empty, so nothing renders a stray separator.
         (when author-email
           (list [:span {:class "spl-changelog-actor" :data-note-author ""} author-email] " "))
         (if instant
           (datetime/clock-time instant timezone :class "spl-changelog-time ml-0")
           [:span {:class "spl-changelog-time ml-0"} created-at])]
        [:div {:class "spl-entry-actions"}
         (ui.button/icon-button :icon (lucide/pencil)
                                :label (tr "Edit note")
                                :attrs {:x-on:click "editing = true"})
         (ui.button/icon-button :icon (lucide/trash-2)
                                :label (tr "Delete note")
                                :danger? true
                                :attrs (merge ui.page/region-swap
                                              {:hx-delete url
                                               :hx-headers (json/js {"X-CSRF-Token" *anti-forgery-token*})
                                               :hx-confirm "Delete this note?"}))]]
       [:p {:class "spl-note-body mt-1 whitespace-pre-wrap text-sm"} body]]
     ;; x-cloak keeps the form hidden until Alpine applies x-show, rather
     ;; than flashing every note's edit form on a full page load.
      [:div {:x-show "editing"
             :x-cloak true}
       (ui.form/form
         (merge ui.page/region-swap {:hx-post url})
         [:div {:class "spl-form"}
          (ui.form/anti-forgery-field)
          (ui.form/section
            :children (ui.form/textarea-field :label (tr "Note")
                                              :name "body"
                                              :id (str "body-" id)
                                              :value body))
          [:div {:class "flex justify-end gap-2"}
           [:button {:type "button"
                     :class "spl-btn spl-btn--ghost spl-btn--sm"
                     :x-on:click "editing = false"}
            (tr "Cancel")]
           (ui.form/submit-button {:class "spl-btn spl-btn--primary spl-btn--sm"} (tr "Save"))]])]]]))

(defn- note-day
  "The garden's date a note was written on, or nil for a timestamp that
  doesn't parse."
  [note timezone]
  (some-> (:note/created-at note)
          datetime/sqlite-datetime->instant
          (datetime/local-date timezone)))

(defn note-list
  "The list of notes as a timeline, newest first and grouped under a heading
  per day written. Carries the id every swap targets."
  [& {:keys [notes note-url-fn timezone]}]
  [:div {:id "notes-list"}
   (if (seq notes)
     [:div {:class "spl-changelog px-0"}
      (for [day-notes (partition-by #(note-day % timezone) notes)
            :let [day (note-day (first day-notes) timezone)]]
        (list
          (when day
            [:h2 {:class "spl-changelog-day"}
             (datetime/day-label day (datetime/today timezone))])
          (for [note day-notes]
            ^{:key (:note/id note)}
            (note-item :note note :note-url-fn note-url-fn :timezone timezone))))]
     [:p {:data-notes-empty ""
          :class "text-text-soft text-sm"}
      (tr "No notes yet.")])])

(defn note-form
  "The new-note form. Posts to the tab's own URL and morphs the page.

  It clears itself after a successful post: the morph keeps the form's live
  values, so x-form-state copies the server-rendered defaults back in on
  `form-saved`, and a second click cannot write the same note twice. The server
  renders the textarea empty. A rejected post fires no `form-saved`, so the
  text stays where the curator can fix it."
  [& {:keys [action errors values]}]
  (ui.form/form
    (merge ui.page/region-swap
           {:id "note-form"
            :hx-post action})
    [:div {:class "spl-form"}
     (ui.form/anti-forgery-field)
     (ui.form/section
       :children (ui.form/textarea-field :label (tr "Note")
                                         :name "body"
                                         :id "body"
                                         :value (:body values)
                                         :errors (:body errors)))
     [:div {:class "flex justify-end"}
      (ui.form/submit-button {:class "spl-btn spl-btn--primary"} (tr "Add note"))]]))

(defn notes-body
  "The whole tab body: the form above the list."
  [& {:keys [notes create-url note-url-fn errors values timezone]}]
  [:div {:class "spl-notes"}
   (note-form :action create-url :errors errors :values values)
   (note-list :notes notes :note-url-fn note-url-fn :timezone timezone)])

(defn panel-section
  "The read-only panel body. A reader never reaches the tab — it is behind the
  edit permission — so this is where an imported note is visible to them."
  [& {:keys [notes note-count more-url timezone]}]
  [:div {:class "space-y-2"}
   (for [note notes]
     ^{:key (:note/id note)}
     [:div {:class "spl-card bg-surface shadow-sm"}
      [:div {:class "spl-card-body p-3"}
       [:div {:class "text-xs text-text-soft"} (written-at (:note/created-at note) timezone)]
       [:p {:class "text-sm whitespace-pre-wrap"} (:note/body note)]]])
   (when (and more-url note-count (> note-count (count notes)))
     [:a {:href more-url
          :class "spl-link text-sm"}
      (tr "See all %1" (i18n/format-number note-count))])])
