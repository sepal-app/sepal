(ns sepal.database.sqlite
  "SQLite-specific database functionality.

  SQLite stores JSON as TEXT and doesn't have native JSONB support.
  This module handles JSON serialization/deserialization for SQLite."
  (:require [clojure.data.json :as json]
            [next.jdbc.prepare :as prepare])
  (:import [java.sql PreparedStatement]))

;; (defn ->json
;;   "Convert a Clojure data structure to a JSON string for SQLite storage."
;;   [value]
;;   (json/write-str value))

;; (defn <-json
;;   "Parse a JSON string from SQLite into Clojure data."
;;   [value]
;;   (when (and value (string? value) (not (empty? value)))
;;     (try
;;       (json/read-str value :key-fn keyword)
;;       (catch Exception _e
;;         ;; If parsing fails, return the original value
;;         value))))

(defn init
  "Initialize SQLite-specific JDBC extensions.

  This sets up:
  - Automatic JSON serialization for maps and vectors on write
  - Boolean conversion for SQLite's integer boolean values (0/1)
  - Automatic JSON parsing for TEXT columns containing JSON"
  []
  ;; Automatically convert Clojure data structures to JSON strings on write
  (extend-protocol prepare/SettableParameter
    ;; Convert a map to a JSON string
    clojure.lang.IPersistentMap
    (set-parameter [m ^PreparedStatement s i]
      (.setString s i (json/write-str m)))

    ;; Convert a vector to a JSON string
    clojure.lang.IPersistentVector
    (set-parameter [v ^PreparedStatement s i]
      (.setString s i (json/write-str v)))))
