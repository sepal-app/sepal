(ns sepal.database.timeout
  "Running a query under a deadline.

  next.jdbc's :timeout sets Statement.setQueryTimeout, and sqlite-jdbc applies
  that to waiting for a lock rather than to a statement that is merely slow: a
  query measured at 12.6s still took 12.6s with a 2s timeout set. cancel()
  reaches sqlite3_interrupt, which does stop one, so that is what fires here.

  A bounded query fails; an unbounded one takes the process with it. The
  gardens share a single vCPU, so a search that burns CPU for ten seconds is
  ten seconds nobody else's request is being served either."
  (:refer-clojure :exclude [count])
  (:require [honey.sql :as honey.sql]
            [next.jdbc :as jdbc])
  (:import [java.sql Statement]
           [java.util.concurrent Executors ScheduledExecutorService ThreadFactory TimeUnit]))

(def default-timeout-ms
  "Long enough that no ordinary query is near it, short enough that a request
  answering this late has already failed the reader."
  5000)

(defonce ^:private watchdog
  (delay
    (Executors/newSingleThreadScheduledExecutor
      (reify ThreadFactory
        (newThread [_ r]
          (doto (Thread. ^Runnable r "sepal-query-watchdog")
            (.setDaemon true)))))))

(defn- with-deadline
  "Call `f`, cancelling `stmt` if it has not returned within `timeout-ms`.

  The scheduled task is cancelled on the way out, so a query that finishes in
  time leaves nothing behind. Cancelling a statement that has already completed
  is a no-op the driver tolerates, which is what makes the race between the two
  harmless."
  [^Statement stmt timeout-ms f]
  (let [task (.schedule ^ScheduledExecutorService @watchdog
                        ^Runnable (fn []
                                    (try (.cancel stmt)
                                         (catch Exception _)))
                        (long timeout-ms)
                        TimeUnit/MILLISECONDS)]
    (try
      (f)
      (finally
        (.cancel task false)))))

(defn- run
  [db stmt timeout-ms opts execute]
  (let [sql-params (if (map? stmt) (honey.sql/format stmt) stmt)
        ;; The db handed to a request is a next.jdbc DefaultOptions wrapper, and
        ;; preparing against a raw connection would lose the column naming it
        ;; carries.
        opts (merge (:options db) opts)]
    (with-open [conn (jdbc/get-connection db)
                ps (jdbc/prepare conn sql-params opts)]
      (with-deadline ps timeout-ms #(execute ps opts)))))

(defn execute!
  "Rows for `stmt`, or a SQLiteException once it has run past `timeout-ms`."
  ([db stmt] (execute! db stmt default-timeout-ms {}))
  ([db stmt timeout-ms] (execute! db stmt timeout-ms {}))
  ([db stmt timeout-ms opts]
   (run db stmt timeout-ms opts #(jdbc/execute! %1 nil %2))))

(defn count
  "How many rows `stmt` returns, under the same deadline as execute!.

  A list page runs this beside the query for the rows, over the same WHERE, so
  bounding one and not the other bounds nothing."
  ([db stmt] (count db stmt default-timeout-ms {}))
  ([db stmt timeout-ms] (count db stmt timeout-ms {}))
  ([db stmt timeout-ms opts]
   {:pre [(map? stmt)]}
   (-> (run db
            {:select [[[:count :*] :count]] :from [[stmt :c]]}
            timeout-ms
            opts
            #(jdbc/execute-one! %1 nil %2))
       :count)))
