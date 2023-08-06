(ns sepal.database.postgresql
  (:require [clojure.core.protocols :as p]
            [clojure.data.json :as json]
            [clojure.datafy :as d]
            [next.jdbc.prepare :as prepare]
            [next.jdbc.result-set :as rs])
  (:import [java.sql PreparedStatement]
           [org.postgresql.util PGobject]))

(defn ->pgobject
  "Transforms Clojure data to a PGobject that contains the data as
  JSON. PGObject type defaults to `jsonb` but can be changed via
  metadata key `:pgtype`"
  [x]
  (let [pgtype (or (:pgtype (meta x)) "jsonb")]
    (doto (PGobject.)
      (.setType pgtype)
      (.setValue (json/write-str x)))))

(extend-protocol p/Datafiable
  org.postgresql.util.PGobject
  (datafy [^org.postgresql.util.PGobject v]
    (let [type  (.getType v)
          value (.getValue v)]
      (if (#{"jsonb" "json"} type)
        (when value
          (with-meta (json/read-str value) {:pgtype type}))
        v))))

(defn init []
  (extend-protocol prepare/SettableParameter
    ;; Convert a map to a json pgobject
    clojure.lang.IPersistentMap
    (set-parameter [m ^PreparedStatement s i]
      (.setObject s i (->pgobject m)))

    ;; Convert a vector to a json pgobject
    clojure.lang.IPersistentVector
    (set-parameter [v ^PreparedStatement s i]
      (.setObject s i (->pgobject v))))

  ;; Convert a PGobject to Clojure data on read
  (extend-protocol rs/ReadableColumn
    org.postgresql.util.PGobject
    (read-column-by-label [^org.postgresql.util.PGobject v _]
      (d/datafy v))
    (read-column-by-index [^org.postgresql.util.PGobject v _2 _3]
      (d/datafy v))))
