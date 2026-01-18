(ns sepal.app.datetime
  "Server-side datetime formatting utilities.

   All timestamps are formatted on the server using the organization's timezone."
  (:require [sepal.settings.interface :as settings.i])
  (:import [java.time Duration Instant ZoneId]
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
