(ns sepal.database.connection
  "How Sepal opens a SQLite database. One definition of the pragmas and the
  extensions, so the web server's pool and the CLI's cannot disagree about what
  a Sepal database is."
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [lambdaisland.uri :as uri]))

(def ^:private pragmas
  "Set as JDBC URL query parameters on every connection.

  busy_timeout is not decoration: a pooled datasource can have several
  connections writing to one database, and without it the loser of a write race
  gets SQLITE_BUSY immediately instead of waiting."
  {:journal_mode "WAL"
   :foreign_keys "ON"
   :enable_load_extension "true"
   :busy_timeout "5000"})

(def ^:private extensions
  ["mod_spatialite"])

(defn- connection-init-sql
  [extension-library-path]
  (->> extensions
       (map (fn [extension]
              (let [path (if extension-library-path
                           (str (fs/path extension-library-path extension))
                           extension)]
                (format "SELECT load_extension('%s')" path))))
       (str/join "; ")))

(defn hikari-spec
  "The HikariCP spec for a Sepal SQLite database: the pragmas as JDBC URL query
  parameters, and connectionInitSql loading the SQLite extensions into every
  connection in the pool."
  [{:keys [db-path extension-library-path]}]
  {:jdbcUrl (format "jdbc:sqlite:%s?%s" db-path (uri/map->query-string pragmas))
   :connectionInitSql (connection-init-sql extension-library-path)})
