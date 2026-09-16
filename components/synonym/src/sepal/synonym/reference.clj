(ns sepal.synonym.reference
  "The read-only WFO synonym reference file: one per machine, opened once per
  process.

  Deliberately not sepal.database.interface/hikari-spec. That spec sets
  journal_mode=WAL, which writes the database header and fails on a read-only
  file, and loads mod_spatialite into every connection, which this file has no
  use for."
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [next.jdbc :as jdbc]
            [next.jdbc.connection :as connection])
  (:import [com.zaxxer.hikari HikariDataSource]))

(defn- spec [path]
  {:jdbcUrl (format "jdbc:sqlite:file:%s?mode=ro&immutable=1" path)
   :maximumPoolSize 4})

(defn close! [pool]
  (when pool
    (.close ^HikariDataSource pool)))

(defn file-present?
  "Whether there is a file at `path` at all.

  Lets a caller tell 'this garden has no reference' apart from 'the file it
  has is not one', which want different things said about them."
  [path]
  (boolean (and path (seq path) (fs/exists? path))))

(def ^:private required-tables
  "What every read in this namespace needs, all three created by
  bin/build-synonym-ref.sh. A file holding fewer than these is not a
  reference, whatever else it is."
  #{"syn" "syn_fts" "metadata"})

(defn- reference?
  "Whether this pool is open on a synonym reference rather than some other
  SQLite file."
  [pool]
  (try
    (let [present (into #{}
                        (map #(or (:sqlite_master/name %) (:name %)))
                        (jdbc/execute! pool ["select name from sqlite_master
                                               where type = 'table'"]))]
      (every? present required-tables))
    (catch Exception _ false)))

(defn open
  "A pool over the reference file, or nil when there is no usable one.

  nil is a supported state, not an error: a garden without a reference runs
  with the WFO half of every synonym read empty. A file that exists but is not
  a reference -- a truncated download, or a path aimed at another database --
  gives nil for the same reason. Opening it anyway would let a pool out of
  here whose every read throws, and a taxon search that asks for WFO synonyms
  would answer 500 instead of answering with the garden's own rows."
  [path]
  (when (file-present? path)
    (let [pool (connection/->pool HikariDataSource (spec path))]
      (if (reference? pool)
        pool
        (do (close! pool) nil)))))

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

(defn- fts-match
  "`query` as an FTS5 MATCH expression, or nil when it carries no tokens.

  Every whitespace-separated token becomes a quoted string and the last one is
  prefix-extended, so `Encyclia cochleat` compiles to `\"Encyclia\" \"cochleat\"*`
  -- the same implicit AND with a trailing prefix that
  components/search/src/sepal/search/compiler.clj:67 gives taxon_fts.

  The quoting is the load-bearing part. FTS5 gives `:`, `.`, `'`, `(`, `)` and
  `-` meanings of their own inside a MATCH expression, so any raw string handed
  to MATCH is parsed as syntax: `sp.` and `Rosa 'Peace'` are syntax errors, and
  a bareword shaped like `accessions:>0` is `no such column: accessions`. Inside
  double quotes the token is a string the tokenizer splits instead, so nothing a
  user can type can be syntax. An embedded double quote is escaped FTS5's way,
  by doubling it."
  [query]
  (let [tokens (remove str/blank? (str/split (str/trim (or query "")) #"\s+"))]
    (when (seq tokens)
      (let [quoted (mapv #(str "\"" (str/replace % "\"" "\"\"") "\"") tokens)]
        (str/join " " (conj (vec (butlast quoted)) (str (last quoted) "*")))))))

(defn search
  "WFO synonyms whose name matches the query, prefix-extended.

  Safe against any string a user can type: see `fts-match`."
  [pool query]
  (if-let [match (when (and pool (seq query)) (fts-match query))]
    (mapv (fn [row]
            {:name (:syn/name row)
             :accepted-core (:syn/accepted_core row)
             :name-id (:syn/name_id row)})
          (jdbc/execute! pool
                         ["select s.name, s.accepted_core, s.name_id from syn s
                            join syn_fts f on f.rowid = s.rowid
                            where syn_fts match ?
                            limit 50" match]))
    []))
