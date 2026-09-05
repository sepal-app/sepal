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
  "Formats SQLite's FTS5 MATCH: `[:match :taxon_fts pattern]` -> `taxon_fts match ?`.

  The pattern is returned as a parameter, never interpolated. Building the SQL
  by concatenation instead -- `\" match '\" right \"'\"` -- put an unescaped,
  user-controlled string inside a SQL literal: a search for `Rosa 'Peace'`
  closed the string early and reached SQLite as a syntax error, which is the
  benign end of what an injected quote can do. Every FTS pattern in the app
  comes from a search box.

  The formatter contract is a vector of the SQL fragment followed by its
  params, so adding `right` here is what turns it into a real bind variable."
  [_ args & _]
  (let [[table pattern] args]
    [(str (name table) " match ?") pattern]))

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
