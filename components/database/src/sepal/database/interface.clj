(ns sepal.database.interface
  (:require [clojure.data.json :as json]
            [camel-snake-kebab.core :as csk]
            [honey.sql]
            [integrant.core :as ig]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as jdbc.result-set]
            [next.jdbc.sql :as jdbc.sql]
            [next.jdbc.date-time]
            [next.jdbc.prepare :as prepare]
            [next.jdbc.result-set :as rs])
  (:import [java.sql PreparedStatement]
           ;; [org.postgis PGgeometry]
           [org.postgresql.util PGobject]))

;; read all db date times as java.time.Instant
(next.jdbc.date-time/read-as-instant)

(def jdbc-options
  (-> jdbc/snake-kebab-opts
      ;; override the column-fn b/c the default behavior of ->snake_case is to
      ;; turn :s3-bucket into s_3_bucket
      (assoc :column-fn (fn [v]
                          (csk/->snake_case v :separator \-)))
      ;; override the builder-fn b/c the default behavior of ->kebab-case is to
      ;; turn :s3_bucket into s-3-bucket
      (assoc :builder-fn (fn [rs opts]
                           (jdbc.result-set/as-modified-maps
                            rs
                            (assoc opts
                                   :label-fn
                                   (fn [v]
                                     (csk/->kebab-case v :separator \_))))))))

(defmethod ig/init-key ::db [_ cfg]
  (jdbc/with-options
    (jdbc/get-datasource cfg)
    jdbc-options))

(defmethod ig/halt-key! ::db [_ db]
  ;; TODO: If we add a connection pool then we'll need to close it here.
  )

(defn query
  "A helper to build execute queries against a table"
  [db table & {:keys [columns join-by offset limit order-by where]}]
  (let [params (cond-> {:select (or columns [:*])
                        :from [table]}
                 where
                 (assoc :where where)

                 join-by
                 (assoc :join-by join-by)

                 order-by
                 (assoc :order-by order-by)

                 offset
                 (assoc :offset offset)

                 limit
                 (assoc :limit limit)

                 :always
                 honey.sql/format)]
    (tap> params)
    (jdbc.sql/query db params)))

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
