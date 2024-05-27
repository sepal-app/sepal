(ns user
  (:require [sepal.app.system :as system]))

(add-tap println)

(def ^:dynamic *system* nil)
(def ^:dynamic *db* nil)

(defn go
  ([]
   (go :local))
  ([profile]
   (let [sys (system/start! profile)]
     (alter-var-root #'*system* (constantly sys))
     (alter-var-root #'*db* (constantly (:sepal.database.interface/db *system*))))))

(defn halt []
  (system/stop! *system*)
  (alter-var-root #'*system* (constantly nil))
  (alter-var-root #'*db* (constantly nil)))
