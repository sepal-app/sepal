(ns sepal.app.routes.tag.index
  (:require [sepal.app.routes.tag.routes :as tag.routes]
            [sepal.app.ui.empty :as ui.empty]
            [sepal.app.ui.page :as ui.page]
            [sepal.app.ui.table :as ui.table]
            [sepal.tag.interface :as tag.i]
            [zodiac.core :as z]))

(defn- table-columns []
  [{:name "Name"
    :type :name
    :priority 1
    :stacked (fn [tag]
               [:span {:class "spl-stacked-line"}
                (:tag/description tag)
                " · "
                (:tag/link-count tag)
                " links"])
    :cell (fn [tag]
            [:a {:class "spl-link"
                 :href (z/url-for tag.routes/detail {:id (:tag/id tag)})}
             (:tag/name tag)])}
   {:name "Description"
    :type :text
    :priority 2
    :cell :tag/description}
   {:name "Links"
    :type :number
    :priority 2
    :cell :tag/link-count}])

(defn- table [tags]
  (ui.table/table :columns (table-columns)
                  :rows tags
                  :empty-state (ui.empty/empty-state
                                 :title "No tags yet"
                                 :body "Ad-hoc groupings a curator can filter a list by later.")))

(defn render [& {:keys [tags]}]
  (ui.page/page :content (table tags)
                :breadcrumbs ["Tags"]))

(defn handler [{:keys [::z/context]}]
  (let [{:keys [db]} context]
    (render :tags (tag.i/list-all db))))
