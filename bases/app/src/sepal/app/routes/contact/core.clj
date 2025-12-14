(ns sepal.app.routes.contact.core
  (:require [sepal.app.middleware :as middleware]
            [sepal.app.routes.contact.create :as create]
            [sepal.app.routes.contact.detail :as detail]
            [sepal.app.routes.contact.index :as index]
            [sepal.app.routes.contact.panel :as panel]
            [sepal.app.routes.contact.routes :as routes]
            [sepal.contact.interface :as contact.i]))

(def contact-loader
  (middleware/default-loader contact.i/get-by-id
                             :id
                             parse-long))

(defn routes []
  ["" {:middleware [[middleware/require-viewer]]}
   ["/"
    {:name routes/index
     :handler #'index/handler}]
   ["/new/"
    {:name routes/new
     :handler #'create/handler
     :conflicting true}]
   ["/:id" {:middleware [[middleware/resource-loader contact-loader]]
            :conflicting true}
    ["/" {:name routes/detail
          :handler #'detail/handler}]
    ["/panel/" {:name routes/panel
                :handler #'panel/handler}]]])
