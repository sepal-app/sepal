(ns sepal.app.server
  (:require [integrant.core :as ig]
            [next.jdbc.connection :as jdbc.connection]
            [reitit.ring]
            [ring.middleware.stacktrace :as stacktrace]
            [sepal.app.middleware :as middleware]
            [sepal.app.routes.accession.core :as accession]
            [sepal.app.routes.activity.core :as activity]
            [sepal.app.routes.auth.core :as auth]
            [sepal.app.routes.dashboard.core :as dashboard]
            [sepal.app.routes.location.core :as location]
            [sepal.app.routes.material.core :as material]
            [sepal.app.routes.media.core :as media]
            [sepal.app.routes.taxon.core :as taxon]
            [sepal.database.interface :as db.i]
            [zodiac.core :as z]
            [zodiac.ext.assets :as z.assets]
            [zodiac.ext.sql :as z.sql]))

(defn routes []
  (concat
    (auth/routes)
    ["" {:middleware [stacktrace/wrap-stacktrace-web
                      middleware/htmx-request]}
     ["/" (dashboard/routes)]
     ["/ok" {:name :ok
             :handler (constantly {:status 204})}]
     ["/accession" (accession/routes)]
     ["/activity" (activity/routes)]
     ["/location" (location/routes)]
     ["/material" (material/routes)]
     ["/taxon" (taxon/routes)]
     ["/media" (media/routes)]]))

(defmethod ig/init-key ::zodiac-sql [_ {:keys [spec context-key]}]
  (db.i/init)
  ;; Building the url instead of passing the spec as-is makes it work on google
  ;; cloud in production where host and port are nil
  (let [url (cond-> spec
              (empty? (:socketFactory spec)) (dissoc :socketFactory)
              :always (jdbc.connection/jdbc-url))]
    (z.sql/init {:context-key context-key
                 :jdbc-options db.i/jdbc-options
                 :spec (merge (select-keys spec [:maxPoolSize])
                              {:jdbcUrl url})})))

(defmethod ig/init-key ::zodiac-assets [_ options]
  (z.assets/init options))

(defmethod ig/init-key ::zodiac [_ options]
  (z/start (merge options
                  {:routes #'routes})))

(defmethod ig/halt-key! ::zodiac [_ zodiac]
  (z/stop zodiac))
