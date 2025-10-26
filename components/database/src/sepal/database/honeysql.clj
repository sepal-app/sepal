(ns sepal.database.honeysql
  (:require [honey.sql]))

(defn- sqlite-json-extract
  "Formats SQLite JSON extract operation.

  "
  [_ column & _path]
  (let [[left right] column]
    ;; (tap> (str "column: " column))
    ;; (tap> [(str (name left) " -> '"  right "'")])
    [(str (name left) " -> '"  right "'")]))

(defn- sqlite-jsonb-extract
  "Formats SQLite JSON extract operation.

  SQLite uses json_extract(column, '$.path') instead of PostgreSQL's -> and ->> operators.
  "
  [_ column & _path]
  (let [[left right] column]
    [(str (name left) " ->> '"  right "'")]))

(defn- match-op
  "Formats SQLite JSON extract operation.

  SQLite uses json_extract(column, '$.path') instead of PostgreSQL's -> and ->> operators.
  "
  [_ column & _path]
  (let [[left right] column]
    [(str (name left) " match '"  right "'")]))

(defn init
  "Initialize HoneySQL formatters for SQLite.

  Note: PostgreSQL-specific operators like @@, <%, <<%, %>, %>> are not registered
  for SQLite as they don't have direct equivalents.

  For JSON operations in SQLite, use :json_extract instead of :-> or :->>:
    ;; PostgreSQL: [:-> :data :type]
    ;; SQLite:     [:json_extract :data :type]
  "
  []
  ;; Register SQLite JSON functions
  (honey.sql/register-fn! :json_extract sqlite-json-extract)

  ;; For backward compatibility with PostgreSQL code, register -> and ->>
  ;; to use SQLite's json_extract
  (honey.sql/register-fn! :-> sqlite-json-extract)
  (honey.sql/register-fn! :->> sqlite-jsonb-extract)
  (honey.sql/register-fn! :match match-op))
