(ns sepal.app.ui.pages.detail
  "Detail page layout with optional resource panel."
  (:require [sepal.app.ui.page :as ui.page]))

(def panel-container-id "detail-panel-content")

(defn page-content-with-panel
  "Detail page content with resource panel.

   Options:
   - :content       - Main form/content
   - :panel-content - Panel content (rendered inline, no HTMX fetch needed)"
  [& {:keys [content panel-content]}]
  [:div {:class "flex flex-row gap-8"}
   [:div {:class "flex-1 min-w-0"}
    content]
   ;; Panel - always visible on detail pages
   [:div {:class "w-80 shrink-0 bg-base-100 border border-base-300 rounded-(--radius-box) overflow-y-auto"}
    [:div {:id panel-container-id}
     panel-content]]])
