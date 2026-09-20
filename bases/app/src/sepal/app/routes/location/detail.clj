(ns sepal.app.routes.location.detail
  (:require [sepal.app.authorization :as authz]
            [sepal.app.http-response :as http]
            [sepal.app.routes.location.panel :as location.panel]
            [sepal.app.routes.location.routes :as location.routes]
            [sepal.app.ui.page :as page]
            [sepal.location.interface.permission :as location.perm]
            [zodiac.core :as z]))

(defn render-panel-page
  "Render the panel view as a full page for read-only users."
  [& {:keys [location panel-data timezone]}]
  (page/page
    :breadcrumbs [[:a {:href (z/url-for location.routes/index)} "Locations"]
                  (:location/name location)]
    :content [:div {:class "max-w-2xl mx-auto"}
              (location.panel/panel-content
                :location (:location panel-data)
                :stats (:stats panel-data)
                :awaiting (:awaiting panel-data)
                :moved-out (:moved-out panel-data)
                :activities (:activities panel-data)
                :activity-count (:activity-count panel-data)
                :timezone timezone)]))

(defn handler [{:keys [::z/context viewer]}]
  (let [{:keys [db resource timezone]} context
        id (:location/id resource)]
    (if (authz/user-has-permission? viewer location.perm/edit)
      (http/found location.routes/detail-general {:id id})
      (let [panel-data (location.panel/fetch-panel-data db resource)]
        (render-panel-page :location resource :panel-data panel-data
                           :timezone timezone)))))
