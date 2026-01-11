(ns sepal.scheduler.interface-test
  (:require [clojure.test :refer [deftest is testing]]
            [integrant.core :as ig]
            [sepal.scheduler.interface :as scheduler.i])
  (:import [java.time Instant]))

(defn- future-instants
  "Generate a sequence of instants starting from now + offset, with given interval."
  [offset-ms interval-ms count]
  (let [start (.plusMillis (Instant/now) offset-ms)]
    (take count (iterate #(.plusMillis ^Instant % interval-ms) start))))

(deftest test-scheduler-lifecycle
  (testing "scheduler can be started and stopped"
    (let [scheduler (ig/init-key ::scheduler.i/scheduler {})]
      (is (some? scheduler))
      (is (= [] (scheduler.i/list-jobs scheduler)))
      (ig/halt-key! ::scheduler.i/scheduler scheduler))))

(deftest test-schedule-and-cancel
  (testing "can schedule and cancel a job"
    (let [scheduler (ig/init-key ::scheduler.i/scheduler {})
          call-count (atom 0)
          task-fn (fn [_time] (swap! call-count inc))
          schedule (future-instants 1000 1000 10)]
      (try
        ;; Schedule a job
        (is (true? (scheduler.i/schedule! scheduler :test-job schedule task-fn)))
        (is (= [{:id :test-job}] (scheduler.i/list-jobs scheduler)))

        ;; Cancel the job
        (is (true? (scheduler.i/cancel! scheduler :test-job)))
        (is (= [] (scheduler.i/list-jobs scheduler)))

        ;; Cancelling again returns false
        (is (false? (scheduler.i/cancel! scheduler :test-job)))
        (finally
          (ig/halt-key! ::scheduler.i/scheduler scheduler))))))

(deftest test-job-execution
  (testing "scheduled job executes at the right time"
    (let [scheduler (ig/init-key ::scheduler.i/scheduler {})
          executed (promise)
          task-fn (fn [time] (deliver executed time))
          ;; Schedule to run in 50ms
          schedule [(-> (Instant/now) (.plusMillis 50))]]
      (try
        (scheduler.i/schedule! scheduler :test-job schedule task-fn)
        ;; Wait for execution (with timeout)
        (let [result (deref executed 500 :timeout)]
          (is (not= :timeout result) "Job should have executed")
          (is (instance? Instant result)))
        (finally
          (ig/halt-key! ::scheduler.i/scheduler scheduler))))))

(deftest test-replace-existing-job
  (testing "scheduling with same id replaces existing job"
    (let [scheduler (ig/init-key ::scheduler.i/scheduler {})
          first-executed (atom false)
          second-executed (promise)
          ;; First job scheduled far in future
          first-schedule [(-> (Instant/now) (.plusMillis 10000))]
          ;; Second job scheduled soon
          second-schedule [(-> (Instant/now) (.plusMillis 50))]]
      (try
        (scheduler.i/schedule! scheduler :test-job first-schedule
                               (fn [_] (reset! first-executed true)))
        (scheduler.i/schedule! scheduler :test-job second-schedule
                               (fn [_] (deliver second-executed true)))

        ;; Only one job should be listed
        (is (= 1 (count (scheduler.i/list-jobs scheduler))))

        ;; Wait for second job to execute
        (is (true? (deref second-executed 500 false)))
        ;; First job should not have executed
        (is (false? @first-executed))
        (finally
          (ig/halt-key! ::scheduler.i/scheduler scheduler))))))

(deftest test-empty-schedule
  (testing "empty schedule does not create a job"
    (let [scheduler (ig/init-key ::scheduler.i/scheduler {})]
      (try
        (is (false? (scheduler.i/schedule! scheduler :test-job [] (fn [_]))))
        (is (= [] (scheduler.i/list-jobs scheduler)))
        (finally
          (ig/halt-key! ::scheduler.i/scheduler scheduler))))))
