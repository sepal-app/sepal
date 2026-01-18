(ns sepal.app.routes.accession.detail
  (:require [sepal.accession.interface.permission :as accession.perm]
            [sepal.app.authorization :as authz]
            [sepal.app.http-response :as http]
            [sepal.app.routes.accession.panel :as accession.panel]
            [sepal.app.routes.accession.routes :as accession.routes]
            [sepal.app.ui.page :as page]
            [zodiac.core :as z]))

(defn render-panel-page
  "Render the panel view as a full page for read-only users."
  [& {:keys [accession panel-data timezone]}]
  (page/page
    :breadcrumbs [[:a {:href (z/url-for accession.routes/index)} "Accessions"]
                  (:accession/code accession)]
    :content [:div {:class "max-w-2xl mx-auto"}
              (accession.panel/panel-content
                :accession (:accession panel-data)
                :taxon (:taxon panel-data)
                :supplier (:supplier panel-data)
                :stats (:stats panel-data)
                :activities (:activities panel-data)
                :activity-count (:activity-count panel-data)
                :timezone timezone)]))

(defn handler [{:keys [::z/context viewer]}]
  (let [{:keys [db resource timezone]} context
        id (:accession/id resource)]
    (if (authz/user-has-permission? viewer accession.perm/edit)
      ;; Can edit -> redirect to edit tabs
      (http/found accession.routes/detail-general {:id id})
      ;; Read-only -> render panel as full page
      (let [panel-data (accession.panel/fetch-panel-data db resource)]
        (render-panel-page :accession resource :panel-data panel-data :timezone timezone)))))
