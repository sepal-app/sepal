(ns sepal.synonym.reference
  "The read-only WFO synonym reference file: one per machine, opened once per
  process.

  Deliberately not sepal.database.interface/hikari-spec. That spec sets
  journal_mode=WAL, which writes the database header and fails on a read-only
  file, and loads mod_spatialite into every connection, which this file has no
  use for."
  (:require [babashka.fs :as fs]
            [integrant.core :as ig]
            [next.jdbc :as jdbc]
            [next.jdbc.connection :as connection]
            [taoensso.telemere :as tel])
  (:import [com.zaxxer.hikari HikariDataSource]))

(defn- spec [path]
  {:jdbcUrl (format "jdbc:sqlite:file:%s?mode=ro&immutable=1" path)
   :maximumPoolSize 4})

(defn open
  "A pool over the reference file, or nil when there is no file.

  nil is a supported state, not an error: a garden with no reference file runs
  with the WFO half of every synonym read empty."
  [path]
  (when (and path (seq path) (fs/exists? path))
    (connection/->pool HikariDataSource (spec path))))

(defn close! [pool]
  (when pool
    (.close ^HikariDataSource pool)))

(defn version
  "The WFO release this file was built from, for logging."
  [pool]
  (when pool
    (-> (jdbc/execute-one! pool ["select value from metadata
                                  where key = 'wfo_plant_list.version'"])
        :metadata/value)))

(defn list-for-accepted-core
  "Every WFO synonym of the taxon with this 14-character id core."
  [pool core]
  (if-not pool
    []
    (mapv (fn [row]
            {:name (:syn/name row)
             :accepted-core (:syn/accepted_core row)
             :name-id (:syn/name_id row)})
          (jdbc/execute! pool ["select name, accepted_core, name_id from syn
                                 where accepted_core = ?" core]))))

(defn search
  "WFO synonyms whose name matches the query, prefix-extended.

  `(str query \"*\")` is the same treatment the search compiler gives taxon_fts
  at components/search/src/sepal/search/compiler.clj:67, so a synonym search
  behaves like a taxon search rather than like exact matching."
  [pool query]
  (if (or (nil? pool) (empty? query))
    []
    (mapv (fn [row]
            {:name (:syn/name row)
             :accepted-core (:syn/accepted_core row)
             :name-id (:syn/name_id row)})
          (jdbc/execute! pool
                         ["select s.name, s.accepted_core, s.name_id from syn s
                            join syn_fts f on f.rowid = s.rowid
                            where syn_fts match ?
                            limit 50" (str query "*")]))))

(defmethod ig/init-key ::pool [_ {:keys [path]}]
  (let [pool (open path)]
    (if pool
      (tel/log! {:level :info :data {:path path :wfo-version (version pool)}}
                "Opened the WFO synonym reference")
      (tel/log! {:level :info :data {:path path}}
                "No WFO synonym reference; synonym search covers local rows only"))
    pool))

(defmethod ig/halt-key! ::pool [_ pool]
  (close! pool))
