(ns sepal.app.routes.taxon.index
  (:require [reitit.core :as r]
            [sepal.app.html :as html]
            [sepal.taxon.interface :as taxon.i]
            [sepal.app.http-response :refer [->path]]
            [sepal.app.ui.layout :as layout]
            [sepal.app.ui.button :as button]
            [sepal.app.ui.table :as table]))

(defn page-content [& {:keys [router org taxa]}]
  [:div
   [:div {:class "flex flex-row content-between"}
    [:h1 {:class "grow text-2xl"} "Taxa"]
    (button/link :text "Add taxon"
                 :href (->path router :taxon-create {:org-id (:organization/id org)}))]
   (table/table :rows taxa
                :columns [:name "Name"
                          :cell #(:name %)])])

(defn page [& {:keys [org router taxa viewer]}]
  (as-> (page-content :org org :router router :taxa taxa) $
    (layout/page-layout :content $
                        :router router
                        :org org
                        :user viewer)
    (html/root-template :content $)))

(defn handler [{:keys [context ::r/router session viewer]}]
  (tap> (str "taxon idnex handler"))
  (let [{:keys [db]} context
        org (:organization session)
        ;; TODO: get the taxa for the organization
        ;; TODO: apply query, page, sort order, etc
        taxa (taxon.i/query db)]
    (-> (page :org org :router router :taxa taxa :user viewer)
        (html/render-html))))
