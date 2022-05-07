(ns sepal.app.routes.taxon.views.index
  (:require [sepal.app.html :as html]
            [sepal.app.http-response :refer [->path]]
            [sepal.app.ui.layout :as layout]
            [sepal.app.ui.button :as button]
            [sepal.app.ui.table :as table]))

(defn table-cell-link [getter]
  (fn [taxon]
    (tap> taxon)
    ;; Build the route manually since we don't have a router
    [:a {:href (format "/org/%s/taxon/%s/"
                       (:taxon/organization-id taxon)
                       (:taxon/id taxon))}
     (getter taxon)]))

(defn page-content [& {:keys [router org taxa]}]
  [:div
   [:div {:class "flex flex-row content-between"}
    [:h1 {:class "grow text-2xl"} "Taxa"]
    (button/link :text "Add taxon"
                 :href (->path router :taxon/new {:org-id (:organization/id org)}))]
   (table/table :rows taxa
                :columns [{:name "Name"
                           :cell (table-cell-link :taxon/name)}])])

(defn render [& {:keys [router org taxa viewer]}]
  (as-> (page-content :org org :router router :taxa taxa) $
    (layout/page-layout :content $
                        :router router
                        :org org
                        :user viewer)
    (html/root-template :content $)))
