(ns sepal.app.routes.tag.index
  (:require [sepal.app.list-query :as list-query]
            [sepal.app.list-view :as list-view]
            [sepal.app.routes.tag.routes :as tag.routes]
            [sepal.app.ui.empty :as ui.empty]
            [sepal.app.ui.page :as ui.page]
            [sepal.app.ui.pages.list :as pages.list]
            [sepal.app.ui.table :as ui.table]
            [sepal.i18n.interface :refer [tr trn]]
            [sepal.tag.interface :as tag.i]
            [zodiac.core :as z]))

(defn- table-columns [timezone]
  (into
   [{:name (tr "Name")
     :key :name
     :type :name
     :priority 1
     :sort [[:lower :t.name]]
     :stacked (fn [tag]
                [:span {:class "spl-stacked-line"}
                 (:tag/description tag)
                 " · "
                 (trn "%1 link" "%1 links" (or (:tag/link-count tag) 0))])
     :cell (fn [tag]
             [:a {:class "spl-link"
                  :href (z/url-for tag.routes/detail {:id (:tag/id tag)})}
              (:tag/name tag)])}
    {:name (tr "Description") :key :description :type :text :priority 2
     :sort [[:lower :t.description]] :cell :tag/description}
    {:name (tr "Links") :key :links :type :number :priority 2
     :sort [:tag__link_count] :cell :tag/link-count}]
   (ui.table/timestamp-columns :created [:t.created_at :tag/created-at]
                               :updated [:t.updated_at :tag/updated-at]
                               :timezone timezone)))

(defn- table [tags table-opts]
  (ui.table/table (merge table-opts
                         {:rows tags
                          :empty-state (ui.empty/empty-state
                                        :title (tr "No tags yet")
                                        :body (tr "Ad-hoc groupings a curator can filter a list by later."))})))

(defn render [& {:keys [tags table-opts]}]
  (ui.page/page :content [:div {:id pages.list/list-container-id}
                          (table tags table-opts)]
                :breadcrumbs [(tr "Tags")]))

(defn handler [{:keys [::z/context uri] :as request}]
  (let [{:keys [db timezone]} context
        view (list-view/resolve request :tag (table-columns timezone))
        tags (tag.i/list-all db :order-by (when (:sort view)
                                            (list-query/order-by (:sort view)
                                                                 {:tiebreak [:t.id :asc]})))]
    (list-view/respond view
                       (render :tags tags :table-opts (list-view/table-opts view uri nil))
                       uri nil)))
