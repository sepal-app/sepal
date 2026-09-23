(ns sepal.app.datetime
  "Server-side datetime formatting utilities.

   All timestamps are formatted on the server using the organization's timezone."
  (:require [clojure.string :as str]
            [sepal.settings.interface :as settings.i])
  (:import [java.time Duration Instant LocalDate ZoneId]
           [java.time.format DateTimeFormatter FormatStyle]
           [java.util Locale]))

;;; ---------------------------------------------------------------------------
;;; Organization Timezone
;;; ---------------------------------------------------------------------------

(def default-timezone
  "Default timezone when organization timezone is not set."
  "UTC")

(defn get-timezone
  "Get organization timezone from settings, defaulting to UTC."
  [db]
  (or (settings.i/get-value db "organization.timezone")
      default-timezone))

(defn- ->zone-id
  "Convert a timezone string to a ZoneId."
  [timezone]
  (ZoneId/of (or timezone default-timezone)))

(defn today
  "The garden's current date. A code template renders {year} and {day} from
  this, so a server in UTC must not hand a garden in Belize tomorrow's number
  six hours early."
  [timezone]
  (LocalDate/now (->zone-id timezone)))

(defn sqlite-datetime->instant
  "Parse a SQLite datetime string as an Instant.

  Sepal writes `datetime('now')` — UTC, '2026-09-01 15:30:00'. Bauble wrote
  naive local times, some with fractional seconds — '2011-02-11 00:00:00.373275'.
  Both parse as UTC: Sepal's are, and Bauble's carry no zone to respect.
  Returns nil for anything unparseable rather than throwing."
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

(def ^:private datetime-formatter
  "Formatter for datetime display: 'Jan 18, 2025, 2:30 PM'"
  (DateTimeFormatter/ofLocalizedDateTime FormatStyle/MEDIUM FormatStyle/SHORT))

(def ^:private datetime-full-formatter
  "Formatter for full datetime with timezone: 'January 18, 2025 at 2:30 PM EST'"
  (-> (DateTimeFormatter/ofPattern "MMMM d, yyyy 'at' h:mm a z")
      (.withLocale Locale/ENGLISH)))

(def ^:private date-formatter
  (-> (DateTimeFormatter/ofPattern "MMM d, yyyy")
      (.withLocale Locale/ENGLISH)))

(defn format-date
  "Format an ISO date string, '2026-03-14', as 'Mar 14, 2026'. A date carries
  no time, so it takes no timezone."
  [iso-date]
  (when iso-date
    (.format (LocalDate/parse iso-date) date-formatter)))

(def ^:private day-formatter
  (-> (DateTimeFormatter/ofPattern "EEEE, MMMM d, yyyy")
      (.withLocale Locale/ENGLISH)))

(defn day-label
  "A day heading: 'Today', 'Yesterday', or 'Monday, March 14, 2026'. `today`
  is the garden's date, from `today`, not the server's."
  [^LocalDate day ^LocalDate today]
  (cond
    (= day today) "Today"
    (= day (.minusDays today 1)) "Yesterday"
    :else (.format day day-formatter)))

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
      (.format datetime-formatter zdt))))

(defn format-datetime-full
  "Format an Instant with full date, time, and timezone.
   Example: 'January 18, 2025 at 2:30 PM EST'"
  [^Instant instant timezone]
  (when instant
    (let [zdt (.atZone instant (->zone-id timezone))]
      (.format datetime-full-formatter zdt))))

(def ^:private time-formatter
  (-> (DateTimeFormatter/ofPattern "h:mm a")
      (.withLocale Locale/ENGLISH)))

(defn clock-time
  "Render a <time> element with the time of day, '2:45 PM', and the full
  datetime as a tooltip. For a row under a heading that already names the day."
  [^Instant instant timezone & {:keys [class]}]
  (when instant
    [:time (cond-> {:datetime (str instant)
                    :title (format-datetime-full instant timezone)}
             class (assoc :class class))
     (.format time-formatter (.atZone instant (->zone-id timezone)))]))

(defn format-relative
  "Format an Instant as a relative time string (e.g., '2 hours ago', 'yesterday')."
  [^Instant instant]
  (when instant
    (let [now (Instant/now)
          duration (Duration/between instant now)
          minutes (.toMinutes duration)
          hours (.toHours duration)
          days (.toDays duration)]
      (cond
        (< minutes 1) "just now"
        (< minutes 60) (str minutes (if (= minutes 1) " minute ago" " minutes ago"))
        (< hours 24) (str hours (if (= hours 1) " hour ago" " hours ago"))
        (< days 2) "yesterday"
        (< days 7) (str days " days ago")
        (< days 30) (str (quot days 7) (if (= (quot days 7) 1) " week ago" " weeks ago"))
        :else (str days " days ago")))))

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
     (format-relative instant)]))

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
  (when instant
    (let [zdt (.atZone instant (->zone-id timezone))]
      (.format datetime-full-formatter zdt))))
