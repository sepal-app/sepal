(ns sepal.media.core
  (:require [next.jdbc.sql :as jdbc.sql]
            [sepal.database.interface :as db.i]
            [sepal.media.interface.spec :as spec]))

(defn get-by-id [db id]
  (let [result (jdbc.sql/get-by-id db :media id)]
    (tap> (str "result: " result))
    (when (some? result)
      (db.i/coerce spec/Media result))))
