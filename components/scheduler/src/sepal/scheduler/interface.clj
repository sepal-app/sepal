(ns sepal.scheduler.interface
  "Generic job scheduling infrastructure using Chime.
   
   Provides Integrant lifecycle for managing scheduled jobs."
  (:require [integrant.core :as ig]
            [sepal.scheduler.core :as core]))

(defmethod ig/init-key ::scheduler [_ opts]
  (core/create-scheduler opts))

(defmethod ig/halt-key! ::scheduler [_ scheduler]
  (core/stop-scheduler scheduler))

(defn schedule!
  "Schedule a recurring job.
   
   - scheduler: the scheduler instance (from Integrant)
   - id: unique keyword identifying the job (e.g., :backup)
   - schedule-seq: sequence of java.time.Instant for when to run
   - task-fn: function to run at each scheduled time, receives the scheduled time as argument
   
   If a job with the same id already exists, it will be cancelled and replaced."
  [scheduler id schedule-seq task-fn]
  (core/schedule! scheduler id schedule-seq task-fn))

(defn cancel!
  "Cancel a scheduled job by id. Returns true if job was found and cancelled."
  [scheduler id]
  (core/cancel! scheduler id))

(defn list-jobs
  "List all scheduled jobs.
   
   Returns a sequence of maps with :id key for each active job."
  [scheduler]
  (core/list-jobs scheduler))
