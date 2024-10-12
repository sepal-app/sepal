(ns sepal.app.routes.org.detail
  (:require [sepal.app.ui.page :as page]))

(defn page-content []
  [:div "TODO:"])

(defn handler [{:keys []}]
  (page/page :page-title "Dashboard"
             :content (page-content)))
