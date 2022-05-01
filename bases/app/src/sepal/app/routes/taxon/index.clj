(ns sepal.app.routes.taxon.index
  (:require [reitit.core :as r]
            [sepal.app.html :as html]
            [sepal.app.ui.layout :as layout]
            [sepal.app.ui.table :as table]))

(defn page-content []
  [:div
   (table/table)])

(defn page [& {:keys [org router taxa viewer]}]
  (as-> (page-content) $
    (layout/page-layout :content $
                        :router router
                        :org org
                        :user viewer)
    (html/root-template :content $)))

(defn handler [{:keys [::r/router session viewer]}]
  (tap> (str "taxon idnex handler"))
  (let [org (:organization session)
        ;; TODO: get the taxa for the organization
        taxa []]
    (-> (page :org org :router router :taxa taxa :user viewer)
        (html/render-html))))
