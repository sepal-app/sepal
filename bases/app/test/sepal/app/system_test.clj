(ns sepal.app.system-test
  (:require [integrant.core :as ig]
            [sepal.config.interface :as config.i]
            [sepal.test.interface :as test.i]
            [zodiac.core :as z]
            [zodiac.ext.sql :as z.sql]))

(def ^:dynamic *app* nil)
(def ^:dynamic *db* nil)
(def ^:dynamic *system* nil)
(def ^:dynamic *cookie-store* nil)

(defn load-config [config]
  (config.i/read-config config {:profile :test}))

(defn default-system-config []
  {:sepal.app.server/zodiac-sql {:spec (load-config "database/config.edn")
                                 :context-key :db}
   :sepal.app.server/zodiac-assets {:build? false
                                    :manifest-path "app/build/.vite/manifest.json"
                                    :asset-resource-path "app/build/assets"
                                    :package-json-dir "bases/app"}
   :sepal.app.server/zodiac {:extensions [(ig/ref :sepal.app.server/zodiac-sql)
                                          (ig/ref :sepal.app.server/zodiac-assets)]
                             :request-context {:forgot-password-email-from "support@sepal.app"
                                               :forgot-password-email-subject "Sepal - Reset Password"
                                               :reset-password-secret "1234"
                                               :app-domain "test.sepal.app"}
                             :cookie-secret "1234567890123456"
                             :start-server? false}
   :sepal.database.interface/schema {:zodiac (ig/ref :sepal.app.server/zodiac)}
   :sepal.malli.interface/init {}})

(def default-system-fixture
  (let [system-config (default-system-config)]
    (test.i/create-system-fixture system-config
                                  (fn [system f]
                                    (let [db (-> system :sepal.app.server/zodiac ::z.sql/db)]
                                      ;; Load schema for in-memory test database
                                      (binding [*system* system
                                                *db* db
                                                *app* (-> system :sepal.app.server/zodiac ::z/app)
                                                *cookie-store* (-> system :sepal.app.server/zodiac ::z/cookie-store)]
                                        (f))))
                                  (keys system-config))))
