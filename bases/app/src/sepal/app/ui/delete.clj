(ns sepal.app.ui.delete
  "The delete button and its confirmation, shared by every resource.

  The blockers are computed when the dialog is fetched, not when the page is
  rendered, so a record that became undeletable while the page sat open says so
  rather than offering a delete the server will refuse."
  (:require [sepal.app.delete :as app.delete]
            [sepal.app.ui.form :as ui.form]))

(defn modal-container
  "Where the confirmation dialog swaps in. One per page."
  []
  [:div {:id "delete-modal-container"}])

(defn menu-item
  "Delete as an entry in the actions menu.

  The same fetch-then-open as the standalone button; only the classes differ.
  `ui.actions/menu` renders the container alongside it."
  [& {:keys [delete-url]}]
  [:button {:type "button"
            :class "spl-menu-item spl-menu-item--danger"
            :role "menuitem"
            :hx-get delete-url
            :hx-target "#delete-modal-container"
            :hx-swap "innerHTML"
            (keyword "hx-on::after-request")
            "if (event.detail.successful) document.getElementById('delete_modal').showModal()"}
   "Delete"])

(defn button
  "The topbar button, and the container its dialog swaps into.

  Returns a list rather than a fragment element. Chassis has no fragment, and
  reaching for one renders a literal tag into the page; a seq splices into its
  parent, which is what the fragment was wanted for."
  [& {:keys [delete-url]}]
  (list
    [:button {:type "button"
              :class "spl-btn spl-btn--danger spl-btn--sm"
              :hx-get delete-url
              :hx-target "#delete-modal-container"
              :hx-swap "innerHTML"
              ;; after-request, not after-swap. htmx fires htmx:afterSwap on
              ;; the swapped-in elements, which are inside the container --
              ;; a sibling of this button, and events bubble up rather than
              ;; sideways, so the handler would never run. htmx:afterRequest
              ;; is fired on the requesting element and fires after the swap,
              ;; which is why table.clj and notes.clj both use it.
              ;;
              ;; The two colons are not a valid keyword literal, hence
              ;; `keyword`.
              (keyword "hx-on::after-request")
              "if (event.detail.successful) document.getElementById('delete_modal').showModal()"}
     "Delete"]
    (modal-container)))

(defn dialog
  "The confirmation. With blockers it explains; without them it offers the
  delete."
  [& {:keys [action label blockers]}]
  [:dialog#delete_modal {:class "spl-modal"}
   [:div {:class "spl-modal-box"}
    [:h3 {:class "font-bold text-lg"} (str "Delete " label "?")]
    (if (seq blockers)
      [:div {:class "py-4 flex flex-col gap-2"}
       [:p "This record cannot be deleted yet:"]
       [:ul {:class "list-disc pl-5"}
        (for [blocker blockers]
          [:li {:key (:reason blocker)} (app.delete/blocker-label blocker)])]
       [:p {:class "text-text-soft text-sm"}
        "Delete or move those records first."]
       [:div {:class "spl-modal-actions"}
        [:button {:type "button"
                  :class "spl-btn"
                  :onclick "delete_modal.close()"}
         "Close"]]]
      [:form {:method "post"
              :action action
              :class "py-4 flex flex-col gap-2"}
       (ui.form/anti-forgery-field)
       [:p "This cannot be undone. The record and everything belonging to it will be removed."]
       [:div {:class "spl-modal-actions"}
        [:button {:type "button"
                  :class "spl-btn"
                  :onclick "delete_modal.close()"}
         "Cancel"]
        [:button {:type "submit"
                  :class "spl-btn spl-btn--danger"}
         "Delete"]]])]
   [:form {:method "dialog" :class "spl-modal-backdrop"}
    [:button "close"]]])
