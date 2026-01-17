(ns sepal.app.csv
  "CSV export utilities."
  (:require [clojure.data.csv :as csv])
  (:import [java.time LocalDate]
           [java.time.format DateTimeFormatter DateTimeParseException]))

(def ^:private date-formatter (DateTimeFormatter/ofPattern "yyyy-MM-dd"))

(defn- format-date
  "Ensure date string is ISO-8601 formatted (YYYY-MM-DD).
   Parses and reformats to handle any legacy data that might not be strictly formatted."
  [s]
  (when s
    (try
      (.format (LocalDate/parse s) date-formatter)
      (catch DateTimeParseException _
        s))))  ; Return original if can't parse

(defn- format-value
  "Format a value for CSV output.
   - nil -> empty string
   - booleans -> \"true\"/\"false\"
   - dates -> ISO-8601 (YYYY-MM-DD)
   - other -> string"
  [v]
  (cond
    (nil? v) ""
    (boolean? v) (if v "true" "false")
    (and (string? v) (re-matches #"^\d{4}-\d{2}-\d{2}$" v)) (format-date v)
    :else (str v)))

(defn rows->csv
  "Convert database rows to CSV string.
   
   Arguments:
     columns - Vector of column definition maps:
               - :key - Key to look up in row maps
               - :header - (optional) CSV column header, defaults to (name key)
     rows    - Sequence of row maps from database query.
   
   Returns CSV string with headers."
  [columns rows]
  (let [headers (mapv (fn [{:keys [key header]}]
                        (or header (name key)))
                      columns)
        extract-row (fn [row]
                      (mapv (fn [{:keys [key]}]
                              (format-value (get row key)))
                            columns))]
    (with-open [writer (java.io.StringWriter.)]
      (csv/write-csv writer (cons headers (map extract-row rows)))
      (str writer))))
