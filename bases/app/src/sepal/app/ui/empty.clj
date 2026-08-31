(ns sepal.app.ui.empty
  "What a page shows when it has nothing to show.

   An empty list is a page in its own right, not a missing one: it says what
   would be here and offers the first step. Activity had a version of this and
   the media grid had a bare blue box half the width of the page, so there is
   one definition now.")

(defn empty-state
  "Options:
   - :icon    optional glyph above the heading, already rendered
   - :title   what is missing, as a sentence fragment
   - :body    optional line explaining what would appear here
   - :actions optional buttons — the first thing to do about it"
  [& {:keys [icon title body actions]}]
  [:div {:class "spl-empty"}
   (when icon
     [:div {:class "spl-empty-icon" :aria-hidden "true"} icon])
   [:h2 {:class "spl-empty-title"} title]
   (when body
     [:p {:class "spl-empty-body"} body])
   (when actions
     [:div {:class "spl-empty-actions"} actions])])
