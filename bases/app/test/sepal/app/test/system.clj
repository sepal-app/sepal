(ns sepal.app.test.system
  (:require [integrant.core :as ig]
            [sepal.config.interface :as config.i]
            [sepal.test.interface :as test.i]))

(def ^:dynamic *app* nil)
(def ^:dynamic *db* nil)
(def ^:dynamic *system* nil)

(defn load-config [config]
  (config.i/read-config config {:profile :test}))

(defn default-system-config []
  {:sepal.database.interface/pool {:db-spec (load-config "database/config.edn")}
   :sepal.database.interface/db {:connectable (ig/ref :sepal.database.interface/pool)}
   :sepal.app.middleware/cookie-store {:secret "1234567890123456"}
   :sepal.app.middleware/middleware {:context {:db (ig/ref :sepal.database.interface/db)
                                               :forgot-password-email-from "support@sepal.app"
                                               :forgot-password-email-subject "Sepal - Reset Password"
                                               :reset-password-secret "1234"
                                               :app-domain "test.sepal.app"}
                                     :session-store (ig/ref :sepal.app.middleware/cookie-store)}
   :sepal.app.ring/app {:middleware (ig/ref :sepal.app.middleware/middleware)}})

(def default-system-fixture
  (let [system-config (default-system-config)]
    (test.i/create-system-fixture system-config
                                  (fn [system f]
                                    (binding [*system* system
                                              *db* (:sepal.database.interface/db system)
                                              *app* (:sepal.app.ring/app system)]
                                      (f)))
                                  (keys system-config))))
