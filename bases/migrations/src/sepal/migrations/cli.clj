(ns sepal.migrations.cli
  (:require [aero.core :as aero]
            [clojure.java.io :as io]
            ;; [clojure.tools.cli :refer [parse-opts]]
            [migratus.core :as migratus]
            [next.jdbc :as jdbc]))

(defn get-config [profile]
  (let [db-config (-> "database/config.edn" ;; from the database component
                      (io/resource)
                      (aero/read-config {:profile profile}))
        conn (jdbc/get-connection db-config)]
    {:store :database
     :migration-dir "migrations/versions/"
     :db {:connection conn}}))


(defn migrate [{:keys [profile]
                :or {profile :local}}]
  (migratus/migrate (get-config profile)))

(defn cli [args]
  ;; TODO: Use tools.cli to valid args
  (println (str "args: " args))
  (migrate args))
