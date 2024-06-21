(ns sepal.app.routes.org.detail
  (:require [reitit.core :as r]
            [sepal.app.html :as html]
            [sepal.app.ui.page :as page]))

(defn page-content []
  [:div "TODO:"])

(defn render [& {:keys [router]}]
  (-> (page/page :router router
                 :page-title "Dashboard"
                 :content (page-content))
      (html/render-html)))

(defn handler [{:keys [::r/router]}]
  (render :router router))
