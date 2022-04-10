(ns sepal.database.interface
  (:require [clojure.data.json :as json]
            [integrant.core :as ig]
            [next.jdbc :as jdbc]
            [next.jdbc.date-time]
            [next.jdbc.prepare :as prepare]
            [next.jdbc.result-set :as rs])
  (:import [java.sql PreparedStatement]
           ;; [org.postgis PGgeometry]
           [org.postgresql.util PGobject]))

;; read all db date times as java.time.Instant
(next.jdbc.date-time/read-as-instant)

(defmethod ig/init-key ::db [_ cfg]
  (jdbc/get-datasource cfg))

(defmethod ig/halt-key! ::db [_ db]
  ;; TODO: If we add a connection pool then we'll need to close it here.
  )
(defn ->pgobject
  "Transforms Clojure data to a PGobject that contains the data as
  JSON. PGObject type defaults to `jsonb` but can be changed via
  metadata key `:pgtype`"
  [x]
  (let [pgtype (or (:pgtype (meta x)) "jsonb")]
    (doto (PGobject.)
      (.setType pgtype)
      (.setValue (json/write-str x)))))

(defn <-pgobject
  "Transform PGobject containing `json` or `jsonb` value to Clojure
  data."
  [^org.postgresql.util.PGobject v]
  (let [type  (.getType v)
        value (.getValue v)]
    (if (#{"jsonb" "json"} type)
      (when value
        (with-meta (json/read-str value) {:pgtype type}))
      v)))

;; if a SQL parameter is a Clojure hash map, it'll be transformed
;; to a PGobject for JSON/JSONB:
(extend-protocol prepare/SettableParameter
  clojure.lang.IPersistentMap
  (set-parameter [m ^PreparedStatement s i]
    (.setObject s i (->pgobject m)))

  ;; org.postgis.Point
  ;; (set-parameter [p ^PreparedStatement s i]
  ;;   (.setObject s i (PGgeometry. p)))
  )
;; if a row contains a PGobject then we'll convert them to Clojure data
;; while reading (if column is either "json" or "jsonb" type):
(extend-protocol rs/ReadableColumn
  org.postgresql.util.PGobject
  (read-column-by-label [^org.postgresql.util.PGobject v _]
    (<-pgobject v))
  (read-column-by-index [^org.postgresql.util.PGobject v _2 _3]
    (<-pgobject v)))
