(ns sepal.app.routes.material.detail
  (:require [sepal.app.authorization :as authz]
            [sepal.app.http-response :as http]
            [sepal.app.routes.material.panel :as material.panel]
            [sepal.app.routes.material.routes :as material.routes]
            [sepal.app.ui.page :as page]
            [sepal.material.interface.permission :as material.perm]
            [zodiac.core :as z]))

(defn render-panel-page
  "Render the panel view as a full page for read-only users."
  [& {:keys [material panel-data]}]
  (page/page
    :breadcrumbs [[:a {:href (z/url-for material.routes/index)} "Materials"]
                  (:material/code material)]
    :content [:div {:class "max-w-2xl mx-auto"}
              (material.panel/panel-content
                :material (:material panel-data)
                :accession (:accession panel-data)
                :taxon (:taxon panel-data)
                :location (:location panel-data)
                :activities (:activities panel-data)
                :activity-count (:activity-count panel-data))]))

(defn handler [{:keys [::z/context viewer]}]
  (let [{:keys [db resource]} context
        id (:material/id resource)]
    (if (authz/user-has-permission? viewer material.perm/edit)
      ;; Can edit -> redirect to edit tabs
      (http/found material.routes/detail-general {:id id})
      ;; Read-only -> render panel as full page
      (let [panel-data (material.panel/fetch-panel-data db resource)]
        (render-panel-page :material resource :panel-data panel-data)))))
