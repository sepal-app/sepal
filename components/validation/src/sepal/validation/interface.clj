(ns sepal.validation.interface
  (:require [malli.core :as m]
            [malli.transform :as mt]
            [sepal.error.interface :as error.i]))

(def form-transformer
  "Transformer for decoding and validating form params.
   Converts string keys to keywords, coerces string values to proper types,
   applies default values, and strips extra keys."
  (mt/transformer
    (mt/key-transformer {:decode keyword})
    {:name :form}
    mt/strip-extra-keys-transformer
    mt/default-value-transformer
    mt/string-transformer))

(defn validate-form-values [spec values]
  (try
    (m/coerce spec values form-transformer)
    (catch Exception e
      (error.i/ex->error e))))

(def email-re
  "Must stay inside the HTML format for `<input type=email>`. The login field is
  `type=\"email\"`, so anything wider is an address Sepal stores and the browser
  then refuses to submit — an account that can never log in. The old
  `[a-zA-Z0-9.-]+` domain allowed `x@-bar.com` and `x@a..b.com`; spelling out the
  labels does not. `sepal.validation.interface-test` pins it both ways."
  #"^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*\.[a-zA-Z]{2,63}$")

(defn coerce-int [v]
  (try
    (cond
      (int? v) v
      (string? v) (Integer/parseInt v)
      (nil? v) v
      :else (int v))
    (catch Exception _
      nil)))

(defn empty->nil
  "Converts empty strings to nil. Useful as a form decoder for optional fields."
  [s]
  (when (seq s) s))

(defn parse-date
  "Parse and validate ISO-8601 date string (YYYY-MM-DD).
   Returns the date string if valid, nil if empty, or ::invalid-date if malformed.
   Used as a form decoder for date fields."
  [s]
  (if (or (nil? s) (= "" s))
    nil
    (try
      (java.time.LocalDate/parse s)
      s  ; Return original string if valid
      (catch Exception _
        ::invalid-date))))

(def date
  "Malli schema for ISO-8601 date strings (YYYY-MM-DD).
   Decodes form input with parse-date, rejects invalid dates.
   Accepts nil (for optional fields) or valid date strings."
  [:fn {:decode/form parse-date
        :error/message "must be a valid date (YYYY-MM-DD)"}
   #(or (nil? %)
        (and (string? %)
             (not= % ::invalid-date)
             (re-matches #"^\d{4}-\d{2}-\d{2}$" %)))])

(def future-date-message "Cannot be a future date")

(defn future-date?
  "Whether an ISO date string falls after `today`, also an ISO date string.
  Compared as strings, which orders YYYY-MM-DD correctly."
  [date today]
  (and (string? date) (pos? (compare date today))))

(defn future-date-errors
  "Field errors, keyed like `http/validation-errors` takes them, for each of
  `fields` in `values` dated after `today`. Nil when there are none.

  `today` is the garden's date, not the server's, so a garden ahead of the
  server can record its own today."
  [values fields today]
  (not-empty
    (into {}
          (for [field fields
                :when (future-date? (get values field) today)]
            [field [future-date-message]]))))
