(ns sepal.app.observation.digest-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [clojure.tools.logging :as log]
            [sepal.app.observation.digest :as digest]
            [sepal.app.test.system :refer [*db* *mail-client* default-system-fixture]]
            [sepal.observation.interface :as observation.i]
            [sepal.scheduler.interface :as scheduler.i]
            [sepal.settings.interface :as settings.i]
            [taoensso.telemere :as tel])
  (:import [java.time LocalDate]))

(use-fixtures :once default-system-fixture)

(defn- sent-messages []
  @(:sent-messages *mail-client*))

(defn- clear-sent-messages! []
  (reset! (:sent-messages *mail-client*) []))

(defn- clear-config! []
  (settings.i/set-values! *db* {"observation.digest_enabled" nil
                                "observation.digest_send_at" nil
                                "observation.digest_recipient" nil}))

;; resource-id carries no foreign key -- sepal.observation.interface.spec --
;; so a bare int stands in for a real material or location row here.
(defn- create-observation! [& {:as opts}]
  (observation.i/create! *db* (merge {:resource-type :location
                                      :resource-id 1
                                      :type "general"
                                      :observed-on "2026-01-01"}
                                     opts)))

(deftest test-get-config-defaults-to-disabled
  (testing "get-config reports disabled by default"
    (clear-config!)
    (is (false? (:enabled (digest/get-config *db*))))))

(deftest test-get-and-set-config-round-trip
  (testing "set-config! then get-config reflects it back"
    (clear-config!)
    (digest/set-config! *db* {:enabled true
                              :send-at "06:30"
                              :recipient "curator@test.com"})
    (let [config (digest/get-config *db*)]
      (is (true? (:enabled config)))
      (is (= "06:30" (:send-at config)))
      (is (= "curator@test.com" (:recipient config))))
    (clear-config!)))

(deftest test-send-digest-sends-nothing-when-none-due
  (testing "nothing due -> nil and no email"
    (clear-sent-messages!)
    (let [result (digest/send-digest! *db* *mail-client* "curator@test.com"
                                      (str (LocalDate/now)) "garden@test.com")]
      (is (nil? result))
      (is (empty? (sent-messages))))))

(deftest test-send-digest-sends-one-message-listing-every-due-observation
  (testing "several due observations -> a single message, not one per observation"
    (clear-sent-messages!)
    (let [today (LocalDate/now)
          on-date (str today)
          due-1 (create-observation! :next-check-on (str (.minusDays today 5))
                                     :note "Wilting badly")
          due-2 (create-observation! :next-check-on on-date
                                     :note "Check the irrigation")
          not-due (create-observation! :next-check-on (str (.plusDays today 5)))]
      (try
        (let [result (digest/send-digest! *db* *mail-client* "curator@test.com" on-date
                                          "garden@test.com")
              messages (sent-messages)]
          (is (= 2 result) "the count of due observations")
          (is (= 1 (count messages)) "one message, not one per observation")
          (let [body (:body (first messages))]
            (is (re-find #"Wilting badly" body))
            (is (re-find #"Check the irrigation" body))))
        (finally
          (doseq [o [due-1 due-2 not-due]]
            (observation.i/delete! *db* (:observation/id o))))))))

(deftest test-send-digest-uses-the-configured-from-address-when-present
  (testing "a configured from-address is used rather than the default"
    (clear-sent-messages!)
    (let [today (LocalDate/now)
          on-date (str today)
          due (create-observation! :next-check-on on-date)]
      (try
        (digest/send-digest! *db* *mail-client* "curator@test.com" on-date
                             "curator-noreply@garden.org")
        (is (= "curator-noreply@garden.org" (:from (first (sent-messages)))))
        (finally
          (observation.i/delete! *db* (:observation/id due)))))))

(deftest test-send-digest-falls-back-to-the-default-from-address-when-absent
  (testing "no from-address configured -> the default sender is used"
    (clear-sent-messages!)
    (let [today (LocalDate/now)
          on-date (str today)
          due (create-observation! :next-check-on on-date)]
      (try
        (digest/send-digest! *db* *mail-client* "curator@test.com" on-date nil)
        (is (= "noreply@sepal.app" (:from (first (sent-messages)))))
        (finally
          (observation.i/delete! *db* (:observation/id due)))))))

(deftest test-schedule-digest-does-nothing-when-mail-is-absent
  (testing "no mail client -> nothing scheduled or cancelled, logged once"
    (let [scheduled (atom [])
          cancelled (atom [])
          logged (atom [])]
      ;; sepal.app.instance-test boots instances with :log-level "WARN" at
      ;; least six times; each boot reaches sepal.logging.interface's global
      ;; (tel/set-min-level! nil "sepal.*" ...) through sepal.app.instance's
      ;; instance-config, and it is never restored. Depending on test order,
      ;; that can leave :info log/log* calls unsent here. Force it back so
      ;; this assertion does not depend on what ran before it.
      (tel/set-min-level! nil "sepal.*" :info)
      (with-redefs [scheduler.i/schedule! (fn [_ id _ _] (swap! scheduled conj id))
                    scheduler.i/cancel! (fn [_ id] (swap! cancelled conj id))
                    log/log* (fn [_ level _ message] (swap! logged conj [level message]))]
        (#'digest/schedule-digest! {:db *db* :mail nil :scheduler ::scheduler}))
      (is (empty? @scheduled) "nothing scheduled")
      (is (empty? @cancelled) "nothing cancelled")
      (is (= 1 (count @logged)) "logs exactly once"))))

(deftest test-schedule-digest-registers-when-enabled-and-recipient-set
  (testing "mail present, enabled, recipient set -> registers the job"
    (clear-config!)
    (digest/set-config! *db* {:enabled true :recipient "curator@test.com"})
    (let [scheduled (atom [])]
      (with-redefs [scheduler.i/schedule! (fn [_ id _ _] (swap! scheduled conj id))]
        (#'digest/schedule-digest! {:db *db* :mail *mail-client* :scheduler ::scheduler}))
      (is (= [:observation-digest] @scheduled)))
    (clear-config!)))

(deftest test-schedule-digest-cancels-when-disabled
  (testing "mail present but disabled -> cancels any existing job"
    (clear-config!)
    (let [cancelled (atom [])]
      (with-redefs [scheduler.i/cancel! (fn [_ id] (swap! cancelled conj id))]
        (#'digest/schedule-digest! {:db *db* :mail *mail-client* :scheduler ::scheduler}))
      (is (= [:observation-digest] @cancelled)))))
