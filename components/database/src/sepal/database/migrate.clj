(ns sepal.database.migrate
  "Schema versioning for one SQLite database.

  Execution goes through the sqlite3 binary rather than JDBC because migrations
  contain CREATE TRIGGER ... BEGIN ... END; bodies, which naive statement
  splitting breaks. -bail is not optional: without it sqlite3 reports an error
  and carries on, which would leave a database half-applied and marked done."
  (:require [babashka.fs :as fs]
            [clojure.java.io :as io]
            [clojure.java.shell :as shell]
            [clojure.string :as str]
            [next.jdbc :as jdbc])
  (:import [java.io File]
           [java.net JarURLConnection]))

(def ^:private migrations-root "database/migrations")

(defn- version-of
  "20251213120000_initial_migration.sql -> 20251213120000"
  [filename]
  (first (str/split filename #"_" 2)))

(defn- enumerate-migrations
  "The .sql filenames under the migrations resource directory, whether Sepal is
  running from source (a file: URL) or from a jar (a jar: URL). tools.build
  writes directory entries into the jar, so the directory resource resolves."
  [url]
  (case (.getProtocol url)
    "file"
    (->> (io/file url) .listFiles (map #(.getName %)))

    "jar"
    (let [^JarURLConnection conn (.openConnection url)
          prefix (str migrations-root "/")]
      (with-open [jar (.getJarFile conn)]
        (->> (enumeration-seq (.entries jar))
             (map #(.getName %))
             (filter #(str/starts-with? % prefix))
             (map #(subs % (count prefix)))
             doall)))))

(defn migration-files
  "The migration filenames, ordered by their timestamp prefix. Enumerated from
  the classpath rather than a hand-maintained index. Throws if none are found:
  an empty result would make every database look up to date, which is the silent
  failure this whole path exists to avoid."
  []
  (let [url (io/resource migrations-root)]
    (when-not url
      (throw (ex-info "The migrations resource directory is not on the classpath"
                      {:reason :migrations-resource-missing :resource migrations-root})))
    (let [files (->> (enumerate-migrations url)
                     (filter #(str/ends-with? % ".sql"))
                     sort
                     vec)]
      (when (empty? files)
        (throw (ex-info "No migrations found on the classpath"
                        {:reason :no-migrations-found :resource migrations-root})))
      files)))

(defn latest-version []
  (some-> (migration-files) last version-of))

(def ^:private minimum-supported
  "The oldest schema version this build can serve.

  Every database at or above this version must work against this code. That is a
  constraint on the code, not on the data: a feature needing a column added after
  this version has to check the garden's schema version and fall back.

  Bump it only when dropping support for a version is deliberate, and only after
  confirming every hosted garden is at or above the new value — a garden below the
  floor serves a maintenance page and nothing else. The CI matrix runs the unit
  suite at this version, so raising it also narrows what CI is checking."
  "20260113120000")

(defn minimum-supported-version [] minimum-supported)

(defn- applied-versions
  [db-path]
  (let [ds (jdbc/get-datasource {:jdbcUrl (str "jdbc:sqlite:" db-path)})]
    (->> (jdbc/execute! ds ["select version from schema_version"])
         (map (comp str first vals))
         set)))

(defn schema-version
  "The highest applied migration version, or nil for a database with none."
  [{:keys [db-path]}]
  (let [versions (applied-versions db-path)]
    (when (seq versions)
      (apply max-key parse-long versions))))

(defn- migration-sources
  "Pairs of [version sql-text], ordered. :migrations-dir overrides the classpath
  index and is for tests. :up-to, when given, drops migrations newer than it,
  which is how a database is built at a version the code no longer defaults to."
  [migrations-dir up-to]
  (let [all (if migrations-dir
              (->> (fs/list-dir migrations-dir)
                   (map str)
                   (filter #(str/ends-with? % ".sql"))
                   sort
                   (mapv (fn [path] [(version-of (str (fs/file-name path))) (slurp path)])))
              (mapv (fn [filename]
                      [(version-of filename)
                       (slurp (io/resource (str "database/migrations/" filename)))])
                    (migration-files)))]
    (if up-to
      (filterv (fn [[version _]] (<= (parse-long version) (parse-long up-to))) all)
      all)))

(defn pending
  "Ordered versions not yet applied to this database."
  [{:keys [db-path migrations-dir up-to]}]
  (let [applied (applied-versions db-path)]
    (->> (migration-sources migrations-dir up-to)
         (remove (comp applied first))
         (mapv first))))

(defn- apply-one!
  "Run one migration and record its version, in a single transaction. Throws on
  failure, leaving the database as it was."
  [db-path [version sql]]
  (let [script (str "begin transaction;\n"
                    sql "\n"
                    (format "insert into schema_version (version) values ('%s');\n" version)
                    "commit;\n")
        file (File/createTempFile (str "sepal-migration-" version) ".sql")]
    (try
      (spit file script)
      (let [{:keys [exit err]} (shell/sh "sqlite3" "-bail" db-path
                                         :in (str ".read " (.getAbsolutePath file) "\n"))]
        (when-not (zero? exit)
          (throw (ex-info (format "Migration %s failed" version)
                          {:reason :migration-failed
                           :version version
                           :db-path db-path
                           :stderr err}))))
      (finally
        (.delete file)))))

(defn migrate!
  "Apply every pending migration, oldest first, one transaction each. Stops at
  the first failure with the database at its previous version.

  :up-to caps how far it goes. Migrations newer than it stay pending, which is
  what lets a test build a database at the supported-version floor."
  [{:keys [db-path migrations-dir up-to]}]
  (let [applied (applied-versions db-path)
        todo (remove (comp applied first) (migration-sources migrations-dir up-to))]
    (doseq [migration todo]
      (apply-one! db-path migration))
    {:applied (mapv first todo)}))

(defn preflight!
  "Copy the database with VACUUM INTO, migrate the copy, and report. The live
  database is never opened for writing. VACUUM INTO gives a consistent snapshot
  of a live database, so no downtime is needed."
  [{:keys [db-path migrations-dir up-to]}]
  (let [dir (fs/create-temp-dir {:prefix "sepal-preflight"})
        snapshot (str (fs/path dir "snapshot.db"))]
    (try
      (let [{:keys [exit err]} (shell/sh "sqlite3" "-bail" db-path
                                         (format "vacuum into '%s';" snapshot))]
        (when-not (zero? exit)
          (throw (ex-info "Could not snapshot the database"
                          {:reason :snapshot-failed :db-path db-path :stderr err}))))
      (try
        {:ok? true
         :applied (:applied (migrate! {:db-path snapshot
                                       :migrations-dir migrations-dir
                                       :up-to up-to}))}
        (catch clojure.lang.ExceptionInfo e
          {:ok? false
           :version (:version (ex-data e))
           :error (ex-message e)
           :stderr (:stderr (ex-data e))}))
      (finally
        (fs/delete-tree dir)))))
