(ns sepal.app.routes.taxon.detail
  (:require [sepal.app.authorization :as authz]
            [sepal.app.http-response :as http]
            [sepal.app.routes.taxon.panel :as taxon.panel]
            [sepal.app.routes.taxon.routes :as taxon.routes]
            [sepal.app.ui.page :as page]
            [sepal.taxon.interface.permission :as taxon.perm]
            [zodiac.core :as z]))

(defn render-panel-page
  "Render the panel view as a full page for read-only users."
  [& {:keys [taxon panel-data]}]
  (page/page
    :breadcrumbs [[:a {:href (z/url-for taxon.routes/index)} "Taxa"]
                  [:em (:taxon/name taxon)]]
    :content [:div {:class "max-w-2xl mx-auto"}
              (taxon.panel/panel-content
                :taxon (:taxon panel-data)
                :parent (:parent panel-data)
                :stats (:stats panel-data)
                :activities (:activities panel-data)
                :activity-count (:activity-count panel-data))]))

(defn handler [{:keys [::z/context viewer]}]
  (let [{:keys [db resource]} context
        id (:taxon/id resource)]
    (if (authz/user-has-permission? viewer taxon.perm/edit)
      ;; Can edit -> redirect to edit tabs
      (http/found taxon.routes/detail-name {:id id})
      ;; Read-only -> render panel as full page
      (let [panel-data (taxon.panel/fetch-panel-data db resource)]
        (render-panel-page :taxon resource :panel-data panel-data)))))
