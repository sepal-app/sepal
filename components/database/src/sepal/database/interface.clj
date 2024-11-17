(ns sepal.database.interface
  (:refer-clojure :exclude [count])
  (:require [next.jdbc :as jdbc]
            [next.jdbc.date-time]
            [sepal.database.core :as core]
            [sepal.database.honeysql :as honeysql]
            [sepal.database.postgresql :as pg]
            [zodiac.ext.sql :as z.sql]))

(defn init []
  (pg/init)
  (honeysql/init))

(def jdbc-options core/jdbc-options)

(def execute! #'z.sql/execute!)
(def execute-one! #'z.sql/execute-one!)
(def count #'z.sql/count)
(def exists? #'z.sql/exists?)

(defmacro with-transaction [[sym transactable opts] & body]
  `(jdbc/with-transaction+options [~sym ~transactable ~opts]
     ~@body))

(defn ->jsonb [value]
  (pg/->jsonb value))

(defn ->pg-enum [value]
  (pg/->pg-enum value))
