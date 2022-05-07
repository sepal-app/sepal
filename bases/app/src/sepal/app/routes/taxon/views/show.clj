(ns sepal.app.routes.taxon.views.show
  (:require
   [reitit.core :as r]
   [sepal.app.html :as html]
   [sepal.app.http-response :refer [->path found see-other]]
   [sepal.app.routes.taxon.views.form :as form]
   [sepal.app.ui.button :as button]
   [sepal.app.ui.layout :as layout]
   [sepal.taxon.interface :as taxon.i]))

(defn page-content [& {:keys [router org form-values]}]
  [:div
   [:div {:class "flex flex-row content-between"}
    [:h1 {:class "grow text-2xl"} "Taxa"]]
   (form/form :action (->path router :taxon/update {:org-id (:organization/id org)})
              :method "post"
              :values form-values)])

(defn render [& {:keys [form-values org router viewer]}]
  (as-> (page-content :form-values form-values
                      :org org
                      :router router) $
    (layout/page-layout :content $
                        :router router
                        :org org
                        :user viewer)
    (html/root-template :content $)))
