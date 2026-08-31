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
   [:div {:class "spl-detail-main"}
    [:div {:class "spl-detail-content"} content]
    footer]
   [:div {:class "spl-detail-panel"}
    [:div {:id panel-container-id}
     panel-content]]])
