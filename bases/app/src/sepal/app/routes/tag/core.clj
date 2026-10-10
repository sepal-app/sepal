(ns sepal.app.routes.tag.core
  (:require [sepal.app.middleware :as middleware]
            [sepal.app.routes.tag.delete :as delete]
            [sepal.app.routes.tag.detail :as detail]
            [sepal.app.routes.tag.index :as index]
            [sepal.app.routes.tag.routes :as routes]
            [sepal.tag.interface :as tag.i]
            [sepal.tag.interface.permission :as tag.perm]
            [zodiac.core :as z]))

(defn tag-loader
  [{:keys [::z/context path-params]}]
  (let [{:keys [db]} context]
    (tag.i/get-by-id db (parse-long (:id path-params)))))

(defn routes []
  [""
   {:feature :tags}
   ["/" {:name routes/index
         :permission tag.perm/view :get #'index/handler}]
   ["/:id" {:middleware [[middleware/resource-loader tag-loader]]}
    ["/" {:name routes/detail
          :permission tag.perm/view
          :feature-read-only? true
          :get #'detail/get-handler
          :post {:permission tag.perm/edit
                 :handler #'detail/post-handler}}]
    ["/delete/" {:name routes/delete
                 :permission tag.perm/delete
                 :permission-redirect routes/detail
                 :get #'delete/handler
                 :post #'delete/handler}]]])
