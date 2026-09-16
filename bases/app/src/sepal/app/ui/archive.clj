(ns sepal.app.ui.archive
  "The archive action and its confirmation.

  Deliberately separate from ui.delete rather than a shared dialog with two
  labels: archiving is reversible and delete is not, and the two say different
  things about what happens next. A location is the only resource that has
  this so far — the one whose history makes it undeletable."
  (:require [sepal.app.delete :as app.delete]
            [sepal.app.ui.form :as ui.form]))

(defn modal-container
  "Where the confirmation swaps in. Its own id, so a page can carry this and
  the delete dialog at once — which the location page does."
  []
  [:div {:id "archive-modal-container"}])

(defn menu-item
  "Archive as an entry in the actions menu."
  [& {:keys [archive-url]}]
  [:button {:type "button"
            :class "spl-menu-item"
            :role "menuitem"
            :hx-get archive-url
            :hx-target "#archive-modal-container"
            :hx-swap "innerHTML"
            (keyword "hx-on::after-request")
            "if (event.detail.successful) document.getElementById('archive_modal').showModal()"}
   "Archive"])

(defn restore-item
  "Bringing one back. A plain post: there is nothing to confirm and nothing
  that can block it."
  [& {:keys [unarchive-url]}]
  [:form {:method "post" :action unarchive-url :role "none"}
   (ui.form/anti-forgery-field)
   [:button {:type "submit" :class "spl-menu-item" :role "menuitem"}
    "Restore"]])

(defn dialog
  "The confirmation. With blockers it explains; without them it offers the
  archive."
  [& {:keys [action label blockers]}]
  [:dialog#archive_modal {:class "spl-modal"}
   [:div {:class "spl-modal-box"}
    [:h3 {:class "font-bold text-lg"} (str "Archive " label "?")]
    (if (seq blockers)
      [:div {:class "py-4 flex flex-col gap-2"}
       [:p "This location cannot be archived yet:"]
       [:ul {:class "list-disc pl-5"}
        (for [blocker blockers]
          [:li {:key (:reason blocker)} (app.delete/blocker-label blocker)])]
       [:p {:class "text-text-soft text-sm"}
        "Move that material somewhere else first, so the garden still says
         where it is."]
       [:div {:class "spl-modal-actions"}
        [:button {:type "button"
                  :class "spl-btn"
                  :onclick "archive_modal.close()"}
         "Close"]]]
      [:form {:method "post"
              :action action
              :class "py-4 flex flex-col gap-2"}
       (ui.form/anti-forgery-field)
       [:p "The location stops appearing when you file material, and keeps its
            place in the history of everything that has already moved through
            it. You can restore it at any time."]
       [:div {:class "spl-modal-actions"}
        [:button {:type "button"
                  :class "spl-btn"
                  :onclick "archive_modal.close()"}
         "Cancel"]
        [:button {:type "submit"
                  :class "spl-btn spl-btn--primary"}
         "Archive"]]])]
   [:form {:method "dialog" :class "spl-modal-backdrop"}
    [:button "close"]]])
