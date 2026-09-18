(ns sepal.app.ui.pages.detail
  "Detail page layout with a resource panel.")

(def panel-container-id "detail-panel-content")

(defn page-content-with-panel
  "Detail page content with resource panel.

   Options:
   - :content       - Main form/content
   - :panel-content - Panel content (rendered inline, no HTMX fetch needed)
   - :footer        - The form's action bar. It belongs at the foot of the
                      content column, not after the whole two-pane block —
                      rendered as a page-level sibling it landed below the
                      panel and off the bottom of the screen.

   The two panes share a top edge and the panel runs to the viewport's right
   edge, matching the workbench layout the lists use."
  [& {:keys [content panel-content footer]}]
  [:div {:class "spl-panes"}
   ;; Below 1024px the panel comes off-canvas from the right, so it needs a
   ;; state — the same checkbox-and-scrim the section rail uses, and no script.
   ;; Its control is in the top bar, which finds it by id; the panel and the
   ;; scrim are siblings of it, so they need only `~`.
   ;;
   ;; Never rendered checked. A reader never sees this at all: their detail
   ;; route renders the panel as the page, with no form beside it.
   [:input {:id "detail-panel-toggle"
            :type "checkbox"
            :class "spl-panel-toggle"}]
   [:div {:class "spl-detail-main"}
    [:div {:class "spl-detail-content"} content]
    footer]
   [:label {:for "detail-panel-toggle"
            :class "spl-panel-scrim"
            :aria-hidden "true"}]
   [:div {:class "spl-detail-panel"}
    [:div {:id panel-container-id}
     panel-content]]])
