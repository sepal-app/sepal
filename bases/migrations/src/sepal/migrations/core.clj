(ns sepal.migrations.core
  (:gen-class)
  (:require [clojure.string :as str]
            [clojure.tools.cli :refer [parse-opts]]
            [taoensso.timbre :as log]
            [migratus.core :as migratus]
            [sepal.config.interface :as config.i]))

(log/set-min-level! (-> "LOG_LEVEL"
                        System/getenv
                        (or "info")
                        (str/lower-case)
                        (keyword)))

(defn get-config
  ([]
   (get-config :local))
  ([profile]
   (config.i/read-config  "migrations/config.edn" {:profile profile})))

(defn usage [options-summary]
  (->> ["Usage: migratus [action] [options]"
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

(def cli-options
  [["-h" "--help"]
   ["-p" "--profile PROFILE" ""
    :default "local"
    :update-fn keyword
    :validate [#{"local" "development" "staging" "production" "test"}
               "Must be one of local, development, staging, production or test"]]])

(defn -main [& args]
  (let [{:keys [options arguments _errors summary] :as args} (parse-opts args
                                                                         cli-options
                                                                         :in-order true)
        action (first arguments)]

    (log/debug args)

    (cond
      (:help options)
      (do (println (usage summary))
          (System/exit 1))

      :else
      (let [config (get-config (-> options :profile keyword))]
        (case action
          "pending" (println (migratus/pending-list config))
          "completed" (->> (migratus/completed-list config)
                           (sort #(compare (first %1) (first %2)))
                           (println))
          "create" (migratus/create config (->> arguments rest (str/join "-")))
          "migrate" (migratus/migrate config)
          nil (do
                (println "ERROR: You must provide an action: create, completed, migrate, pending")
                (println (usage summary))
                (System/exit 1))
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
