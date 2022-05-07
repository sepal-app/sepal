(ns sepal.app.routes.org.detail
  (:require [reitit.core :as r]
            [sepal.app.html :as html]
            [sepal.app.ui.layout :as layout]
            [sepal.app.ui.table :as table]))

(defn page-content []
  [:div
   (table/table)])

(defn page [& {:keys [org router]}]
  (as-> (page-content) $
    (layout/page-layout :content $
                        :router router
                        :org org)
    (html/root-template :content $)))

(defn handler [{:keys [::r/router session viewer]}]
  (let [org (:organization session)]
    (-> (page :org org :router router :user viewer)
        (html/render-html))))
