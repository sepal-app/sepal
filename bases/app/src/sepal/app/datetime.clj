(ns sepal.app.datetime
  "Server-side datetime formatting utilities.

   All timestamps are formatted on the server using the organization's timezone."
  (:require [clojure.string :as str]
            [sepal.i18n.interface :as i18n :refer [tr trn]]
            [sepal.settings.interface :as settings.i])
  (:import [java.time Duration Instant LocalDate ZoneId ZoneOffset ZonedDateTime]
           [java.time.format DateTimeFormatter FormatStyle]
           [java.time.temporal ChronoUnit]
           [java.util Locale]))

;;; ---------------------------------------------------------------------------
;;; Organization Timezone
;;; ---------------------------------------------------------------------------

(def default-timezone
  "Default timezone when organization timezone is not set."
  "UTC")

(def ^:private zone-ids (set (ZoneId/getAvailableZoneIds)))

(defn valid-timezone?
  "Whether `timezone` names a zone the JDK knows."
  [timezone]
  (contains? zone-ids timezone))

(defn timezone-options
  "The zones a garden can choose from, each labelled with its offset now: UTC,
  then every Region/City zone. Etc/ zones and legacy aliases are left out."
  []
  (->> zone-ids
       (filter #(re-matches #"^[A-Z][a-z]+/[A-Za-z_/-]+" %))
       (remove #(str/starts-with? % "Etc/"))
       sort
       (cons default-timezone)
       (mapv (fn [zone-id]
               (let [offset (.getOffset (ZonedDateTime/now (ZoneId/of zone-id)))]
                 {:value zone-id
                  ;; ZoneOffset/UTC prints as "Z".
                  :label (format "(UTC%s) %s"
                                 (if (= offset ZoneOffset/UTC) "+00:00" offset)
                                 zone-id)})))))

(defn get-timezone
  "The organization's timezone. UTC when it is unset, and when what is stored
  names no zone: a value saved before the setting was validated would
  otherwise break every page that shows a time."
  [db]
  (let [timezone (settings.i/get-value db "organization.timezone")]
    (if (valid-timezone? timezone) timezone default-timezone)))

(defn- ->zone-id
  "Convert a timezone string to a ZoneId. A missing timezone throws rather than
  falling back to UTC: a caller that forgot to pass it showed a garden's times
  hours off, with nothing to say they were wrong."
  ^ZoneId [timezone]
  (when (nil? timezone)
    (throw (IllegalArgumentException. "timezone is required")))
  (ZoneId/of timezone))

(defn today
  "The garden's current date. A code template renders {year} and {day} from
  this, so a server in UTC must not hand a garden in Belize tomorrow's number
  six hours early."
  [timezone]
  (LocalDate/now (->zone-id timezone)))

(defn sqlite-datetime->instant
  "Parse a SQLite datetime string as an Instant.

  Sepal writes `datetime('now')` — UTC, '2026-09-01 15:30:00' — and the
  import loader stores only UTC in that shape, so every value parses as UTC.
  A fractional part, '2011-02-11 00:00:00.373275', is dropped. Returns nil for
  anything unparseable rather than throwing."
  [s]
  (when s
    (try
      (Instant/parse (-> s
                         (str/replace " " "T")
                         (str/replace #"\..*" "")
                         (str "Z")))
      (catch java.time.format.DateTimeParseException _ nil))))

;;; ---------------------------------------------------------------------------
;;; Server-Side Formatting
;;; ---------------------------------------------------------------------------

;; English keeps these hand-written patterns. Every other locale uses the JDK's
;; localized styles for it, since a translated pattern would keep English word
;; order: "MMMM d, yyyy 'at' h:mm a" is not how Spanish writes a date.
(def ^:private english-formatters
  {:datetime (-> (DateTimeFormatter/ofLocalizedDateTime FormatStyle/MEDIUM FormatStyle/SHORT)
                 (.withLocale Locale/ENGLISH))
   :full (-> (DateTimeFormatter/ofPattern "MMMM d, yyyy 'at' h:mm a z")
             (.withLocale Locale/ENGLISH))
   :date (-> (DateTimeFormatter/ofPattern "MMM d, yyyy")
             (.withLocale Locale/ENGLISH))
   :day (-> (DateTimeFormatter/ofPattern "EEEE, MMMM d, yyyy")
            (.withLocale Locale/ENGLISH))
   :time (-> (DateTimeFormatter/ofPattern "h:mm a")
             (.withLocale Locale/ENGLISH))})

(defn- localized-formatter [kind ^Locale locale]
  (-> (case kind
        :datetime (DateTimeFormatter/ofLocalizedDateTime FormatStyle/MEDIUM FormatStyle/SHORT)
        :full (DateTimeFormatter/ofPattern
                (str (java.time.format.DateTimeFormatterBuilder/getLocalizedDateTimePattern
                       FormatStyle/LONG FormatStyle/SHORT
                       java.time.chrono.IsoChronology/INSTANCE locale)
                     " z"))
        :date (DateTimeFormatter/ofLocalizedDate FormatStyle/MEDIUM)
        :day (DateTimeFormatter/ofLocalizedDate FormatStyle/FULL)
        :time (DateTimeFormatter/ofLocalizedTime FormatStyle/SHORT))
      (.withLocale locale)))

;; Bounded by the locales with a catalog, so memoizing cannot grow without limit.
(def ^:private localized-formatter* (memoize localized-formatter))

(defn- formatter
  "The formatter for `kind` in the current locale."
  ^DateTimeFormatter [kind]
  (if i18n/*locale*
    (localized-formatter* kind (i18n/java-locale))
    (english-formatters kind)))

(defn format-date
  "Format an ISO date string, '2026-03-14', as 'Mar 14, 2026'. A date carries
  no time, so it takes no timezone.

  A stored value that is not a date, such as an import's '11/02/2011', comes
  back as it is: one bad row must not take down every page that lists it."
  [iso-date]
  (when iso-date
    (try
      (.format (LocalDate/parse iso-date) (formatter :date))
      (catch java.time.format.DateTimeParseException _ iso-date))))

(defn format-day
  "A date written out in full: 'Monday, March 14, 2026'."
  [^LocalDate day]
  (.format day (formatter :day)))

(defn day-label
  "A day heading: 'Today', 'Yesterday', or 'Monday, March 14, 2026'. `today`
  is the garden's date, from `today`, not the server's."
  [^LocalDate day ^LocalDate today]
  (cond
    (= day today) (tr "Today")
    (= day (.minusDays today 1)) (tr "Yesterday")
    :else (format-day day)))

(defn local-date
  "The garden's date at `instant`."
  [^Instant instant timezone]
  (.toLocalDate (.atZone instant (->zone-id timezone))))

(defn format-datetime
  "Format an Instant as a localized datetime string.
   Example: 'Jan 18, 2025, 2:30 PM'"
  [^Instant instant timezone]
  (when instant
    (let [zdt (.atZone instant (->zone-id timezone))]
      (.format (formatter :datetime) zdt))))

(defn format-datetime-full
  "Format an Instant with full date, time, and timezone.
   Example: 'January 18, 2025 at 2:30 PM EST'"
  [^Instant instant timezone]
  (when instant
    (let [zdt (.atZone instant (->zone-id timezone))]
      (.format (formatter :full) zdt))))

(defn clock-time
  "Render a <time> element with the time of day, '2:45 PM', and the full
  datetime as a tooltip. For a row under a heading that already names the day."
  [^Instant instant timezone & {:keys [class]}]
  (when instant
    [:time (cond-> {:datetime (str instant)
                    :title (format-datetime-full instant timezone)}
             class (assoc :class class))
     (.format (formatter :time) (.atZone instant (->zone-id timezone)))]))

(defn format-relative
  "Format an Instant as a relative time string (e.g., '2 hours ago', 'yesterday').

  Under a day it counts hours. Past that it counts the garden's calendar days,
  so 'yesterday' agrees with the day heading the activity feed files it under:
  at 7 AM, an event from 11 PM two days back is '2 days ago', not 'yesterday'."
  ([instant timezone]
   (format-relative instant timezone (Instant/now)))
  ([^Instant instant timezone ^Instant now]
   (when instant
     (let [duration (Duration/between instant now)
           minutes (.toMinutes duration)
           hours (.toHours duration)
           days (.between ChronoUnit/DAYS
                          (local-date instant timezone)
                          (local-date now timezone))]
       (cond
         (< minutes 1) (tr "just now")
         (< minutes 60) (trn "%1 minute ago" "%1 minutes ago" minutes)
         ;; A 25-hour day when the clocks go back keeps 24 hours on one date.
         (or (< hours 24) (< days 1)) (trn "%1 hour ago" "%1 hours ago" hours)
         (= days 1) (tr "yesterday")
         (< days 7) (trn "%1 day ago" "%1 days ago" days)
         (< days 30) (trn "%1 week ago" "%1 weeks ago" (quot days 7))
         :else (trn "%1 day ago" "%1 days ago" days))))))

;;; ---------------------------------------------------------------------------
;;; Hiccup Helpers (render <time> elements with server-side formatted content)
;;; ---------------------------------------------------------------------------

(defn relative-time
  "Render a <time> element with relative time and full datetime tooltip.
   Example: '2 hours ago' with tooltip 'January 18, 2025 at 2:30 PM EST'"
  [instant timezone & {:keys [class]}]
  (when instant
    [:time (cond-> {:datetime (str instant)
                    :title (format-datetime-full instant timezone)}
             class (assoc :class class))
     (format-relative instant timezone)]))

(defn datetime
  "Render a <time> element with formatted datetime and full tooltip."
  [instant timezone & {:keys [class]}]
  (when instant
    [:time (cond-> {:datetime (str instant)
                    :title (format-datetime-full instant timezone)}
             class (assoc :class class))
     (format-datetime instant timezone)]))

;;; ---------------------------------------------------------------------------
;;; Email Formatting
;;; ---------------------------------------------------------------------------

(defn format-for-email
  "Format an Instant for email display. Includes timezone indicator.

   Example: 'January 18, 2025 at 2:30 PM EST'

   Use this for backup notifications, system emails, etc."
  [^Instant instant timezone]
  (format-datetime-full instant timezone))
