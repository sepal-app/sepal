(ns sepal.database.interface
  (:refer-clojure :exclude [count])
  (:require [integrant.core :as ig]
            [next.jdbc :as jdbc]
            [next.jdbc.date-time]
            [sepal.database.connection :as connection]
            [sepal.database.core :as core]
            [sepal.database.honeysql :as honeysql]
            [sepal.database.migrate :as migrate]
            [sepal.database.sqlite :as sqlite]
            [zodiac.ext.sql :as z.sql]))

(defn init []
  (sqlite/init)
  (honeysql/init))

(def jdbc-options
  "JDBC options that include SQLite-specific column reading.
  Merges core options (column naming) with SQLite options (boolean/JSON handling)."
  core/jdbc-options)

(def execute! #'z.sql/execute!)
(def execute-one! #'z.sql/execute-one!)
(def count #'z.sql/count)
(def exists? #'z.sql/exists?)

(def hikari-spec #'connection/hikari-spec)

(defmethod ig/init-key ::extensions [_ {:keys [zodiac extensions] :as config}]
  (let [db (::z.sql/db zodiac)
        extensions (if (string? extensions) [extensions] extensions)]
    (doseq [extension extensions]
      (execute! db [(format "select load_extension('%s');" extension)])))
  (core/load-schema! config))

(defmethod ig/init-key ::schema [_ config]
  ;; THIS IS ONLY FOR LOADING THE DB SCHEMA INTO A TEST DATABASE
  (core/load-schema! config))

(defn schema-initialized?
  "Check if the database schema has been initialized."
  [db]
  (core/schema-initialized? db))

(defn load-schema!
  "Load the SQLite schema from the classpath into the database at :db-path."
  [config]
  (core/load-schema! config))

(def schema-resource #'core/schema-resource)

(def migration-files #'migrate/migration-files)
(def latest-version #'migrate/latest-version)
(def schema-version #'migrate/schema-version)
(def pending #'migrate/pending)
(def migrate! #'migrate/migrate!)
(def preflight! #'migrate/preflight!)

(defmacro with-transaction [[sym transactable opts] & body]
  `(jdbc/with-transaction+options [~sym ~transactable ~opts]
     ~@body))
