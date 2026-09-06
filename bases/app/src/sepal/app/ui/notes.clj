(ns sepal.app.ui.notes
  "Markup for resource notes: the tab's form and list, and the read-only panel
  section. One namespace because accession, material and taxon all render the
  same note; only the URLs differ."
  (:require [ring.middleware.anti-forgery :refer [*anti-forgery-token*]]
            [sepal.app.json :as json]
            [sepal.app.ui.form :as ui.form]))

(defn- note-item
  "One note: its text, and an edit form for it hidden behind an Alpine flag.
  Carrying the form here rather than fetching it saves a route and a handler on
  each of the three resources."
  [& {:keys [note note-url-fn]}]
  (let [{:note/keys [id body created-at author-email]} note
        url (note-url-fn id)]
    [:li {:class "spl-note"
          :data-note-id id
          :x-data (json/js {:editing false})}
     [:div {:x-show "!editing"}
      [:div {:class "spl-note-meta text-xs text-text-soft flex items-center gap-2"}
       [:span created-at]
       ;; The 1,643 notes imported from Bauble have no author. The element is
       ;; absent rather than empty, so nothing renders a stray separator.
       (when author-email
         [:span {:data-note-author ""} author-email])]
      [:p {:class "spl-note-body whitespace-pre-wrap"} body]
      [:div {:class "spl-note-actions flex gap-2"}
       [:button {:type "button"
                 :class "spl-btn spl-btn--ghost spl-btn--sm"
                 :x-on:click "editing = true"}
        "Edit"]
       [:button {:type "button"
                 :class "spl-btn spl-btn--ghost spl-btn--sm"
                 :hx-delete url
                 :hx-headers (json/js {"X-CSRF-Token" *anti-forgery-token*})
                 :hx-confirm "Delete this note?"
                 :hx-target "#notes-list"
                 :hx-swap "outerHTML"}
        "Delete"]]]
     [:div {:x-show "editing"}
      (ui.form/form
        {:hx-post url
         :hx-target "#notes-list"
         :hx-swap "outerHTML"}
        [:div {:class "spl-form"}
         (ui.form/anti-forgery-field)
         (ui.form/textarea-field :label "Note"
                                 :name "body"
                                 :id (str "body-" id)
                                 :value body)
         [:div {:class "flex justify-end gap-2"}
          [:button {:type "button"
                    :class "spl-btn spl-btn--ghost spl-btn--sm"
                    :x-on:click "editing = false"}
           "Cancel"]
          (ui.form/submit-button "Save")]])]]))

(defn note-list
  "The list of notes, newest first. Carries the id every swap targets."
  [& {:keys [notes note-url-fn]}]
  [:div {:id "notes-list"}
   (if (seq notes)
     [:ul {:class "spl-note-list"}
      (for [note notes]
        ^{:key (:note/id note)}
        (note-item :note note :note-url-fn note-url-fn))]
     [:p {:data-notes-empty ""
          :class "text-text-soft text-sm"}
      "No notes yet."])])

(defn note-form
  "The new-note form. Posts to the tab's own URL and replaces the list."
  [& {:keys [action errors values]}]
  (ui.form/form
    {:id "note-form"
     :hx-post action
     :hx-target "#notes-list"
     :hx-swap "outerHTML"}
    [:div {:class "spl-form"}
     (ui.form/anti-forgery-field)
     (ui.form/textarea-field :label "Note"
                             :name "body"
                             :id "body"
                             :value (:body values)
                             :errors (:body errors))
     [:div {:class "flex justify-end"}
      (ui.form/submit-button "Add note")]]))

(defn notes-body
  "The whole tab body: the form above the list."
  [& {:keys [notes create-url note-url-fn errors values]}]
  [:div {:class "spl-notes"}
   (note-form :action create-url :errors errors :values values)
   (note-list :notes notes :note-url-fn note-url-fn)])

(defn panel-section
  "The read-only panel body. A reader never reaches the tab — it is behind the
  edit permission — so this is where an imported note is visible to them."
  [& {:keys [notes note-count more-url]}]
  [:div {:class "space-y-2"}
   (for [note notes]
     ^{:key (:note/id note)}
     [:div {:class "spl-card bg-surface shadow-sm"}
      [:div {:class "spl-card-body p-3"}
       [:div {:class "text-xs text-text-soft"} (:note/created-at note)]
       [:p {:class "text-sm whitespace-pre-wrap"} (:note/body note)]]])
   (when (and more-url note-count (> note-count (count notes)))
     [:a {:href more-url
          :class "spl-link text-sm"}
      (str "See all " note-count)])])
