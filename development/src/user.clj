(ns user
  (:require [sepal.malli.interface :as malli.i]))

;; Namespaces like sepal.app.routes.activity.core name :time/instant in
;; schemas evaluated at load, so the registry has to exist before they load.
;; start-process! runs :sepal.malli.interface/init before start! loads them, so
;; production and the dispatcher are covered — but tests and REPL tooling
;; require app namespaces directly, ahead of any system. Clojure auto-loads
;; user.clj from the classpath first, which makes this the earliest hook there
;; is. It used to happen as a side effect of loading sepal.app.system.
(malli.i/init)

(add-tap println)

;; Nothing in the ns form above may require an application namespace, and
;; sepal.app.dev-user-ns-test fails the build if one does.
;;
;; Clojure auto-loads user.clj into every JVM with development/src on its
;; classpath, which is every test run through the :dev alias. Requiring
;; sepal.app.instance here loaded the whole application before Kaocha started —
;; 19 seconds a run, whether or not the tests being run had any use for it.
;; Focusing a component test that touches no application went from 30 seconds to
;; 9 once it stopped.
;;
;; So go and stop resolve what they need when they are called. The cost of that
;; is real: a symbol renamed out from under them fails at the REPL rather than
;; at compile time, which is the other half of what that test covers.

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
  (let [env-opts (requiring-resolve 'sepal.app.main/env-opts)
        start-process! (requiring-resolve 'sepal.app.instance/start-process!)
        start! (requiring-resolve 'sepal.app.instance/start!)
        provision! (requiring-resolve 'sepal.app.instance/provision!)
        migrate! (requiring-resolve 'sepal.app.instance/migrate!)
        schema-version (requiring-resolve 'sepal.app.instance/schema-version)
        latest-schema-version (requiring-resolve 'sepal.app.instance/latest-schema-version)
        exists? (requiring-resolve 'babashka.fs/exists?)
        {:keys [process instance]} (env-opts (System/getenv))
        started-process (start-process! process)
        db-path (:db-path instance)]
    (when-not (exists? db-path)
      (provision! {:db-path db-path}))
    (when (not= (schema-version {:db-path db-path})
                (latest-schema-version))
      (migrate! {:db-path db-path}))
    (let [garden (start! started-process
                         (merge instance dev-opts {:start-server? true}))]
      (alter-var-root #'*process* (constantly started-process))
      (alter-var-root #'*garden* (constantly garden))
      (alter-var-root #'*system* (constantly (:system garden)))
      ;; The literal keyword, not ::z.sql/db. An alias would mean requiring
      ;; zodiac.ext.sql in the ns form, which is the thing this file must not do.
      (alter-var-root #'*db* (constantly (get-in garden [:system
                                                         :sepal.app.server/zodiac
                                                         :zodiac.ext.sql/db])))
      garden)))

(defn stop []
  ;; The instance halts first: it holds the connection pool and the Jetty, and
  ;; releases its claim on the process registry. Halting the process first would
  ;; leave both running with nothing left to stop them.
  (when *garden*
    ((requiring-resolve 'sepal.app.instance/stop!) *garden*))
  (when *process*
    ((requiring-resolve 'sepal.app.instance/stop-process!) *process*))
  (alter-var-root #'*garden* (constantly nil))
  (alter-var-root #'*process* (constantly nil))
  (alter-var-root #'*system* (constantly nil))
  (alter-var-root #'*db* (constantly nil)))

(defn restart []
  (stop)
  (go))
