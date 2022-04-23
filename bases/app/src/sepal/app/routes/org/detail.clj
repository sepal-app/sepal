(ns sepal.app.routes.org.detail
  (:require [reitit.core :as r]
            [sepal.app.html :as html]
            [sepal.app.ui.layout :as layout]))

(defn page-content []
  [:div "Page content"]
  )

(defn page [& {:keys [org router viewer]}]
  (as-> (page-content) $
   (layout/page-layout :content $
                       :router router
                       :org org
                       :user viewer)
   (html/root-template :content $)))

(defn handler [{:keys [::r/router viewer]}]
  (let [org {:organization/id 1}]
    (-> (page :org org :router router :user viewer)
        (html/render-html))))
