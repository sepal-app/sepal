(ns sepal.app.server
  (:require [integrant.core :as ig]
            [reitit.ring]
            [ring.middleware.stacktrace :as stacktrace]
            [sepal.app.http-response :as http-response]
            [sepal.app.middleware :as middleware]
            [sepal.app.routes.accession.core :as accession]
            [sepal.app.routes.auth.core :as auth]
            [sepal.app.routes.location.core :as location]
            [sepal.app.routes.material.core :as material]
            [sepal.app.routes.media.core :as media]
            [sepal.app.routes.org.core :as org]
            [sepal.app.routes.org.routes :as org.routes]
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
     ["/" {:name :root
           :handler (fn [_] (http-response/found org.routes/index))
           :middleware [[middleware/require-viewer]]}]
     ["/ok" {:name :ok
             :handler (constantly {:status 204})}]
     ["/accession" (accession/routes)]
     ["/location" (location/routes)]
     ["/material" (material/routes)]
     ["/org" (org/routes)]
     ["/taxon" (taxon/routes)]
     ["/media" (media/routes)]]))

(defmethod ig/init-key ::zodiac-sql [_ {:keys [spec context-key]}]
  (db.i/init)
  (z.sql/init {:context-key context-key
               :jdbc-options db.i/jdbc-options
               :spec spec}))

(defmethod ig/init-key ::zodiac-assets [_ options]
  (z.assets/init options))

(defmethod ig/init-key ::zodiac [_ options]
  (z/start (merge options
                  {:routes #'routes})))

(defmethod ig/halt-key! ::zodiac [_ zodiac]
  (z/stop zodiac))
