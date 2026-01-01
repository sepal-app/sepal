(ns sepal.app.server
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [integrant.core :as ig]
            [lambdaisland.uri :as uri]
            [reitit.ring]
            [ring.middleware.stacktrace :as stacktrace]
            [sepal.app.middleware :as middleware]
            [sepal.app.routes.accession.core :as accession]
            [sepal.app.routes.activity.core :as activity]
            [sepal.app.routes.auth.core :as auth]
            [sepal.app.routes.contact.core :as contact]
            [sepal.app.routes.dashboard.core :as dashboard]
            [sepal.app.routes.location.core :as location]
            [sepal.app.routes.material.core :as material]
            [sepal.app.routes.media.core :as media]
            [sepal.app.routes.settings.core :as settings]
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
     ["/contact" (contact/routes)]
     ["/location" (location/routes)]
     ["/material" (material/routes)]
     ["/taxon" (taxon/routes)]
     ["/media" (media/routes)]
     ["/settings" (settings/routes)]]))

(defn- build-jdbc-url
  "Build a SQLite JDBC URL with optional pragma query parameters."
  [db-path pragmas]
  (if (seq pragmas)
    (format "jdbc:sqlite:%s?%s" db-path (uri/map->query-string pragmas))
    (format "jdbc:sqlite:%s" db-path)))

(defn- build-connection-init-sql
  "Build connectionInitSql string to load SQLite extensions.
   Takes a list of extension names and an optional library path."
  [extensions extension-library-path]
  (when (seq extensions)
    (->> extensions
         (map (fn [ext]
                (let [ext-path (if extension-library-path
                                 (str (fs/path extension-library-path ext))
                                 ext)]
                  (format "SELECT load_extension('%s')" ext-path))))
         (str/join "; "))))

(defn- get-data-home
  "Get Sepal data home directory.
   Priority: SEPAL_DATA_HOME > XDG_DATA_HOME/Sepal > platform default"
  []
  (or (System/getenv "SEPAL_DATA_HOME")
      (when-let [xdg (System/getenv "XDG_DATA_HOME")]
        (str (fs/path xdg "Sepal")))
      (if (= "Mac OS X" (System/getProperty "os.name"))
        (str (fs/path (System/getProperty "user.home") "Library" "Application Support" "Sepal"))
        (str (fs/path (fs/xdg-data-home) "Sepal")))))

(defmethod ig/init-key ::zodiac-sql [_ {:keys [database-path pragmas spec extensions extension-library-path context-key]}]
  (let [db-path (or database-path
                    (str (fs/path (get-data-home) "sepal.db")))
        parent-dir (fs/parent db-path)
        jdbc-url (build-jdbc-url db-path pragmas)
        connection-init-sql (build-connection-init-sql extensions extension-library-path)
        spec (cond-> (assoc spec :jdbcUrl jdbc-url)
               connection-init-sql (assoc :connectionInitSql connection-init-sql))]
    (when (and parent-dir (not (fs/exists? parent-dir)))
      (fs/create-dirs parent-dir))
    (db.i/init)
    (z.sql/init {:context-key context-key
                 :jdbc-options db.i/jdbc-options
                 :spec spec})))

(defmethod ig/init-key ::zodiac-assets [_ options]
  (z.assets/init options))

(defmethod ig/init-key ::zodiac [_ options]
  (z/start (merge options
                  {:routes #'routes})))

(defmethod ig/halt-key! ::zodiac [_ zodiac]
  (z/stop zodiac))
