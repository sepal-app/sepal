(ns user
  (:require [babashka.fs :as fs]
            [sepal.app.instance :as instance]
            [sepal.app.main :as main]
            [sepal.malli.interface :as malli.i]
            [zodiac.ext.sql :as z.sql]))

;; Namespaces like sepal.app.routes.activity.core name :time/instant in
;; schemas evaluated at load, so the registry has to exist before they load.
;; start-process! runs :sepal.malli.interface/init before start! loads them, so
;; production and the dispatcher are covered — but tests and REPL tooling
;; require app namespaces directly, ahead of any system. Clojure auto-loads
;; user.clj from the classpath first, which makes this the earliest hook there
;; is. It used to happen as a side effect of loading sepal.app.system.
(malli.i/init)

(add-tap println)

(defonce ^:dynamic *process* nil)
(defonce ^:dynamic *garden* nil)
(defonce ^:dynamic *system* nil)
(defonce ^:dynamic *db* nil)

(def ^:private dev-opts
  "The three things the REPL wants and no other caller does. Paths are relative
  to the repository root, which is where the REPL is expected to start: vite
  runs as a subprocess with :package-json-dir as its working directory."
  {:vite {:mode :dev-server
          :config-file "vite.config.dev.js"
          :package-json-dir "bases/app"}
   :hot-reload {:watch-paths ["bases/app/src"]
                :watch-extensions #{".clj" ".cljc" ".edn" ".html"}}
   :reload-per-request? true})

(defn go
  "Start Sepal the way the self-hosted entry point does, plus the dev options.
  Reads the same environment -main reads, so a mistake in that mapping shows up
  here rather than in production."
  []
  (let [{:keys [process instance]} (main/env-opts (System/getenv))
        started-process (instance/start-process! process)
        db-path (:db-path instance)]
    (when-not (fs/exists? db-path)
      (instance/provision! {:db-path db-path}))
    (when (not= (instance/schema-version {:db-path db-path})
                (instance/latest-schema-version))
      (instance/migrate! {:db-path db-path}))
    (let [garden (instance/start! started-process
                                  (merge instance dev-opts {:start-server? true}))]
      (alter-var-root #'*process* (constantly started-process))
      (alter-var-root #'*garden* (constantly garden))
      (alter-var-root #'*system* (constantly (:system garden)))
      (alter-var-root #'*db* (constantly (get-in garden [:system :sepal.app.server/zodiac ::z.sql/db])))
      garden)))

(defn stop []
  ;; The instance halts first: it holds the connection pool and the Jetty, and
  ;; releases its claim on the process registry. Halting the process first would
  ;; leave both running with nothing left to stop them.
  (when *garden*
    (instance/stop! *garden*))
  (when *process*
    (instance/stop-process! *process*))
  (alter-var-root #'*garden* (constantly nil))
  (alter-var-root #'*process* (constantly nil))
  (alter-var-root #'*system* (constantly nil))
  (alter-var-root #'*db* (constantly nil)))

(defn restart []
  (stop)
  (go))
