(ns sepal.app.routes.register.core
  (:require [sepal.app.routes.register.index :as index]))

(def routes
  ["" {:name :register/index
       :handler #(index/handler %)}])
