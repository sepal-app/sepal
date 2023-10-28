(ns sepal.app.routes.register.core
  (:require [sepal.app.routes.register.index :as index]))

(defn routes []
  ["" {:name :register/index
       :handler #'index/handler}])
