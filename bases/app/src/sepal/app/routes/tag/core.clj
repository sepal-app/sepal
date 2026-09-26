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
  ["" {:middleware [[middleware/require-viewer]]}
   ["/" {:name routes/index :handler #'index/handler}]
   ["/:id" {:middleware [[middleware/resource-loader tag-loader]]}
    ["/" {:name routes/detail
          :middleware [[(middleware/require-permission-or-redirect
                          tag.perm/edit (constantly routes/index))]]
          :handler #'detail/handler}]
    ["/delete/" {:name routes/delete
                 :middleware [[(middleware/require-permission-or-redirect
                                 tag.perm/delete (constantly routes/detail))]]
                 :get #'delete/handler
                 :post #'delete/handler}]]])
