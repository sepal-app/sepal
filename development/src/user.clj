(ns user
  (:require [sepal.app.system :as system]
            [zodiac.ext.sql :as z.sql]))

(add-tap println)

(defonce ^:dynamic *system* nil)
(defonce ^:dynamic *db* nil)

(defn go
  ([]
   (go :local))
  ([profile]
   (let [sys (system/start! profile)]
     (alter-var-root #'*system* (constantly sys))
     (alter-var-root #'*db* (constantly (get-in *system* [:sepal.app.server/zodiac ::z.sql/db]))))))

(defn stop []
  (when *system*
    (system/stop! *system*))
  (alter-var-root #'*system* (constantly nil))
  (alter-var-root #'*db* (constantly nil)))

(defn restart []
  (when *system*
    (stop))
  (go))
