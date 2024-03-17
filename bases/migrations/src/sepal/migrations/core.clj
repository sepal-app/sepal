(ns sepal.migrations.core
  (:require [clojure.string :as str]
            [clojure.tools.cli :refer [parse-opts]]
            [migratus.core :as migratus]
            [sepal.config.interface :as config.i]))

(defn get-config
  ([]
   (get-config :local))
  ([profile]
   (config.i/read-config  "migrations/config.edn" {:profile profile})))

(defn usage [options-summary]
  (->> ["Usage: migratus action [options]"
        ""
        "Actions:"
        "  create"
        "  migrate"
        "  pending"
        "  completed"
        ""
        "options:"
        options-summary]
       (str/join \newline)))

(def global-cli-options
  [["-h" "--help"]
   ["-p" "--profile PROFILE" ""
    :default "local"
    :validate [#{"local" "development" "staging" "production" "test"}
               "Must be one of local, development, staging, production or test"]]])

(defn -main [& args]
  (let [{:keys [options arguments _errors summary]} (parse-opts args
                                                                global-cli-options
                                                                :in-order true)
        action (first arguments)]

    (cond
      (:help options)
      (do (println (usage summary))
          (System/exit 1))

      :else
      (let [config (get-config (-> options :profile keyword))]
        (case action
          "pending" (migratus/pending-list config)
          "completed" (->> (migratus/completed-list config)
                           (sort #(compare (first %1) (first %2))))
          "create" (migratus/create config (->> arguments rest (str/join "-")))
          "migrate" (migratus/migrate config)
          (do
            (println "Unknown command:" action)
            (System/exit 1)))))))

(comment
  ;; Create a new migrations
  (migratus/create (get-config :local)
                   "intial migrations")

  ;; List pending migrations
  (migratus/pending-list (get-config :local))

  ;; Apply pending migrations
  (migratus/migrate (get-config :local))

  (->> (migratus/completed-list (get-config :local))
       (sort #(compare (first %1) (first %2))))
  ())
