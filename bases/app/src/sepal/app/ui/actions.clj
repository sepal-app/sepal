(ns sepal.app.ui.actions
  "The top-right actions on a page, as one value.

  Every page used to build this itself and they had drifted: the taxon name
  page had a dropdown written in raw utility classes, the media tabs passed an
  Upload button, the other record pages passed a bare Delete, and the index
  pages passed a create button. Nothing said what belonged there or in what
  order.

  Taking one value also closes the way that went wrong: `taxon/detail/name.clj`
  passed `:page-title-buttons` twice in one call to `ui.page/page`, and the
  later key won, so the Delete button never rendered at all. There is one
  argument to pass now, so there is nothing to pass twice."
  (:require [sepal.app.ui.archive :as ui.archive]
            [sepal.app.ui.delete :as ui.delete]
            [sepal.app.ui.form :as ui.form]
            [sepal.app.ui.icons.heroicons :as heroicons]))

(defn- menu-link [{:keys [label href post-url params]}]
  (if post-url
    ;; An action that writes. Posted through htmx, so a handler answering with
    ;; HX-Redirect lands the page where it says.
    [:li
     [:form {:hx-post post-url :hx-swap "none" :role "none"}
      (ui.form/anti-forgery-field)
      (for [[k v] params]
        [:input {:type "hidden" :name (name k) :value (str v)}])
      [:button {:type "submit" :class "spl-menu-item" :role "menuitem"} label]]]
    [:li [:a {:class "spl-menu-item" :href href :role "menuitem"} label]]))

(defn menu
  "The action bar.

  :primary    optional hiccup for a button that stays outside the menu, for a
              page whose main action should be one click away — Upload, on the
              media tabs.
  :items      maps of {:label :href}, in the order they should appear. An item
              with :post-url, and optional :params, posts instead of linking.
  :delete-url renders Delete as the last item, separated by a rule, along with
              the container its confirmation dialog swaps into. Passing this
              rather than an item is what keeps `ui.delete` the only place that
              knows how that dialog is fetched and opened.
  :archive-url / :unarchive-url render Archive or Restore above the rule, on
              the same terms. A record has one or the other, never both.

  Returns nil when there is nothing to show, so a page with no actions renders
  no empty bar."
  [& {:keys [primary items delete-url archive-url unarchive-url]}]
  (when (or primary (seq items) delete-url archive-url unarchive-url)
    [:div {:class "spl-actions"}
     primary
     (when (or (seq items) delete-url archive-url unarchive-url)
       [:div {:class "spl-actions-menu"
              :x-data "{open: false}"
              :x-id "['actions-menu']"
              :x-on:keydown.escape.window "open = false"}
        [:button {:type "button"
                  :class "spl-btn spl-btn--sm"
                  :x-on:click "open = !open"
                  :x-bind:aria-expanded "open"
                  :x-bind:aria-controls "$id('actions-menu')"
                  :aria-haspopup "menu"}
         "Actions"
         (heroicons/chevron-down)]
        [:ul {:class "spl-actions-panel spl-menu"
              :role "menu"
              :x-show "open"
              :x-on:click.outside "open = false"
              :x-bind:id "$id('actions-menu')"
              ;; Hidden until Alpine boots. x-show alone leaves the panel
              ;; painted on first render, and it sits over the page.
              :style "display: none;"}
         (map menu-link items)
         (when archive-url
           [:li (ui.archive/menu-item :archive-url archive-url)])
         (when unarchive-url
           [:li (ui.archive/restore-item :unarchive-url unarchive-url)])
         (when delete-url
           (list
             (when (or (seq items) archive-url unarchive-url)
               [:li [:hr {:class "spl-menu-sep"}]])
             [:li (ui.delete/menu-item :delete-url delete-url)]))]])
     (when delete-url
       (ui.delete/modal-container))
     (when archive-url
       (ui.archive/modal-container))]))
