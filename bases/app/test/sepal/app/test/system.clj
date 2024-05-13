(ns sepal.app.test.system
  (:require [integrant.core :as ig]
            [sepal.config.interface :as config.i]
            [sepal.test.interface :as test.i]))

(def ^:dynamic *app* nil)
(def ^:dynamic *db* nil)
(def ^:dynamic *system* nil)

(defn load-config [config]
  (config.i/read-config config {:profile test}))

(def default-system-config
  {:sepal.database.interface/pool
   {:db-spec (load-config "database/config.edn")}

   :sepal.database.interface/db
   {:connectable (ig/ref :sepal.database.interface/pool)}

   :sepal.app.ring/app
   {:cookie-secret "1234567890123456"
    :context {:db (ig/ref :sepal.database.interface/db)}}})

(def default-system-fixture
  (test.i/create-system-fixture default-system-config
                                (fn [system f]
                                  (binding [*system* system
                                            *db* (:sepal.database.interface/db system)
                                            *app* (:sepal.app.ring/app system)]
                                    (f)))
                                (keys default-system-config)))
