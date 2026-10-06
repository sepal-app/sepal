(ns sepal.app.routes.list-columns.core
  (:require [sepal.app.authorization :as authz]
            [sepal.app.routes.list-columns.routes :as routes]
            [sepal.app.routes.list-columns.save :as save]))

(defn routes []
  [""
   ;; profile-edit: every role has it, and the choice is the viewer's own.
   ["/:list/columns" {:name routes/save
                      :permission authz/profile-edit
                      :post #'save/handler}]])
