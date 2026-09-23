(ns sepal.app.observation.digest
  "A daily email listing every observation whose next check is due."
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log]
            [integrant.core :as ig]
            [sepal.app.datetime :as datetime]
            [sepal.mail.interface :as mail.i]
            [sepal.observation.interface :as observation.i]
            [sepal.scheduler.interface :as scheduler.i]
            [sepal.settings.interface :as settings.i])
  (:import [java.time LocalTime ZoneId ZonedDateTime]
           [java.time.temporal ChronoUnit]))

;; -----------------------------------------------------------------------------
;; Configuration

(def ^:private setting-keys
  {:enabled "observation.digest_enabled"
   :send-at "observation.digest_send_at"
   :recipient "observation.digest_recipient"})

(def ^:private default-send-at
  "07:00")

;; Falls back to this when the job's :from is unset -- the same default
;; sepal.app.instance uses for invitation and password-reset mail.
(def ^:private default-digest-email-from
  "noreply@sepal.app")

(defn get-config
  "Digest configuration from settings.

  Disabled by default. This is deliberate: it is the feature a competitor
  markets by name and the first thing a garden turns off, and both are true
  at once. Opt-in means nobody has to turn it off."
  [db]
  (let [settings (settings.i/get-values db "observation")]
    {:enabled (= "true" (get settings (:enabled setting-keys)))
     :send-at (or (get settings (:send-at setting-keys)) default-send-at)
     :recipient (get settings (:recipient setting-keys))}))

(defn set-config!
  "Save digest configuration to settings."
  [db config]
  (let [settings (cond-> {}
                   (contains? config :enabled)
                   (assoc (:enabled setting-keys) (str (boolean (:enabled config))))

                   (contains? config :send-at)
                   (assoc (:send-at setting-keys) (:send-at config))

                   (contains? config :recipient)
                   (assoc (:recipient setting-keys) (:recipient config)))]
    (settings.i/set-values! db settings)))

;; -----------------------------------------------------------------------------
;; Sending

(defn- format-observation
  [{:observation/keys [type-label value-label resource-type resource-id
                       next-check-on note]}]
  (str "- " type-label
       (when value-label (str ": " value-label))
       (format " (%s #%d)" (name resource-type) resource-id)
       " - due " next-check-on
       (when (seq note) (str " - " note))))

(defn- digest-subject
  [n]
  (format "Sepal: %d observation%s due" n (if (= n 1) "" "s")))

(defn- digest-body
  [observations]
  (str/join "\n" (map format-observation observations)))

(defn send-digest!
  "Email `recipients` a single message listing every observation due on or
  before `on-date` (an ISO-8601 string), from `from` (or the default address
  when nil). Returns how many were listed, or nil and sends nothing when none
  are due."
  [db mail recipients on-date from]
  (when-let [due (seq (observation.i/due db on-date))]
    (mail.i/send-message mail {:from (or from default-digest-email-from)
                               :to recipients
                               :subject (digest-subject (count due))
                               :body (digest-body due)})
    (count due)))

;; -----------------------------------------------------------------------------
;; Scheduler integration

(defn- daily-at
  "An infinite Chime schedule of Instants, one per day at `send-at` (an
  HH:mm time in `zone`, the garden's timezone), starting with the next
  occurrence from now. Mirrors sepal.app.backup.core's chime sequence
  construction."
  [send-at ^ZoneId zone]
  (let [now (ZonedDateTime/now zone)
        today-at (-> now
                     (.with (LocalTime/parse send-at))
                     (.truncatedTo ChronoUnit/MINUTES))
        next-at (if (.isAfter now today-at)
                  (.plusDays today-at 1)
                  today-at)]
    (map #(.toInstant ^ZonedDateTime %)
         (iterate #(.plusDays ^ZonedDateTime % 1) next-at))))

(defn- digest-task
  [db mail recipient from]
  (fn [_scheduled-time]
    (try
      (send-digest! db mail recipient
                    (str (datetime/today (datetime/get-timezone db)))
                    from)
      (catch Exception e
        (log/error e "Scheduled observation digest failed")))))

(defn schedule-digest!
  "Register the digest job with the scheduler based on current config, or
  cancel it. Called on app startup, and again whenever the settings screen
  saves a change, including a change to the organization's timezone -- a
  save the running job never picks up until the next restart is a job that
  quietly fails every run in between.

  Guards on the mail client rather than on the config: with no client this
  logs once and registers nothing, rather than scheduling a job that would
  fail on every run."
  [{:keys [db mail scheduler from]}]
  (if-not mail
    (log/info "No mail client; observation digest not scheduled")
    (let [{:keys [enabled send-at recipient]} (get-config db)]
      (if (and enabled recipient)
        (scheduler.i/schedule! scheduler :observation-digest
                               (daily-at send-at (ZoneId/of (datetime/get-timezone db)))
                               (digest-task db mail recipient from))
        (scheduler.i/cancel! scheduler :observation-digest)))))

;; -----------------------------------------------------------------------------
;; Integrant lifecycle

(defmethod ig/init-key :sepal.app.observation/digest-job [_ {:keys [scheduler zodiac mail from]}]
  (let [db (get zodiac :zodiac.ext.sql/db)]
    (schedule-digest! {:db db :mail mail :scheduler scheduler :from from})
    {:scheduler scheduler}))

(defmethod ig/halt-key! :sepal.app.observation/digest-job [_ {:keys [scheduler]}]
  (scheduler.i/cancel! scheduler :observation-digest))
