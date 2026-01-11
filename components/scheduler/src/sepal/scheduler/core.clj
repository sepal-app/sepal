(ns sepal.scheduler.core
  "Core scheduler implementation using Chime."
  (:require [chime.core :as chime]
            [clojure.tools.logging :as log]))

(defn create-scheduler
  "Create a new scheduler instance. Returns an atom containing a map of job-id -> job."
  [_opts]
  (log/info "Starting scheduler")
  (atom {}))

(defn stop-scheduler
  "Stop all scheduled jobs and clean up the scheduler."
  [scheduler]
  (log/info "Stopping scheduler")
  (doseq [[id job] @scheduler]
    (log/debug "Cancelling job" id)
    (.close ^java.lang.AutoCloseable job))
  (reset! scheduler {}))

(defn schedule!
  "Schedule a job. If a job with the same id exists, it will be replaced."
  [scheduler id schedule-seq task-fn]
  (when-let [existing (get @scheduler id)]
    (log/debug "Replacing existing job" id)
    (.close ^java.lang.AutoCloseable existing))
  (if (seq schedule-seq)
    (let [job (chime/chime-at schedule-seq
                              task-fn
                              {:error-handler (fn [e]
                                                (log/error e "Error in scheduled job" id)
                                                true)})] ; true = continue scheduling
      (log/info "Scheduled job" id)
      (swap! scheduler assoc id job)
      true)
    (do
      (log/debug "Empty schedule for job" id ", not scheduling")
      false)))

(defn cancel!
  "Cancel a scheduled job by id. Returns true if job was found and cancelled."
  [scheduler id]
  (if-let [job (get @scheduler id)]
    (do
      (log/info "Cancelling job" id)
      (.close ^java.lang.AutoCloseable job)
      (swap! scheduler dissoc id)
      true)
    false))

(defn list-jobs
  "List all active job ids."
  [scheduler]
  (map (fn [[id _job]] {:id id}) @scheduler))
