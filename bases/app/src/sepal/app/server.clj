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
            [sepal.app.routes.auth.accept-invitation :as accept-invitation]
            [sepal.app.routes.auth.forgot-password :as forgot-password]
            [sepal.app.routes.auth.login :as login]
            [sepal.app.routes.auth.logout :as logout]
            [sepal.app.routes.auth.reset-password :as reset-password]
            [sepal.app.routes.auth.routes :as auth.routes]
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
            [zodiac.ext.headers :as z.headers]
            [zodiac.ext.sql :as z.sql]))

(defn routes []
  ["" {:middleware [middleware/htmx-request
                    middleware/wrap-org-settings
                    middleware/wrap-flash-messages
                    stacktrace/wrap-stacktrace-web]}
   ;; Auth routes (inlined so they're under the root middleware)
   ["/login" {:name auth.routes/login
              :handler #'login/handler}]
   ["/logout" {:name auth.routes/logout
               :handler #'logout/handler}]
   ["/forgot-password" {:name auth.routes/forgot-password
                        :handler #'forgot-password/handler}]
   ["/reset-password" {:name auth.routes/reset-password
                       :handler #'reset-password/handler}]
   ["/accept-invitation" {:name auth.routes/accept-invitation
                          :handler #'accept-invitation/handler}]
   ;; App routes
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
   ["/settings" (settings/routes)]])

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

;; CSP policy for Alpine.js and HTMX
;; - 'unsafe-eval' required for Alpine.js expression evaluation (x-show, x-bind, etc.)
;; - 'unsafe-inline' for script-src allows inline <script> tags (e.g., module imports)
;; - 'unsafe-inline' for style-src allows inline styles and x-cloak CSS
;; - 'data:' for img-src allows inline SVG data URIs
;; TODO: Consider switching to @alpinejs/csp build and nonces to tighten CSP
(def csp-headers
  (assoc z.headers/web
         :content-security-policy
         (str "default-src 'self'; "
              "script-src 'self' 'unsafe-inline' 'unsafe-eval'; "
              "style-src 'self' 'unsafe-inline'; "
              "img-src 'self' data: blob:; "
              "connect-src 'self' https:")))

(defmethod ig/init-key ::zodiac [_ {:keys [extensions] :as options}]
  (let [extensions (conj extensions (z.headers/init {:headers csp-headers}))]
    (z/start (merge options
                    {:routes #'routes
                     :extensions extensions}))))

(defmethod ig/halt-key! ::zodiac [_ zodiac]
  (z/stop zodiac))
