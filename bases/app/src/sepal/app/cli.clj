(ns sepal.app.cli
  "CLI for administrative tasks like creating users."
  (:require [clojure.string :as str]
            [clojure.tools.cli :refer [parse-opts]]
            [sepal.app.cli.routes :as cli.routes]
            [sepal.app.cli.system :as cli.sys]
            [sepal.user.interface :as user.i])
  (:gen-class))

;; =============================================================================
;; create-user command
;; =============================================================================

(def create-user-options
  [["-e" "--email EMAIL" "User email address (required)"]
   ["-p" "--password PASSWORD" "User password (required)"]
   ["-r" "--role ROLE" "User role: admin, editor, reader (required)"
    :validate [#{"admin" "editor" "reader"} "Must be: admin, editor, or reader"]]
   ["-h" "--help" "Show help for this command"]])

(defn- create-user-cmd [args]
  (let [{:keys [options errors summary]} (parse-opts args create-user-options)]
    (cond
      (:help options)
      (do (println "Usage: clojure -M:dev:cli create-user [options]")
          (println)
          (println "Options:")
          (println summary)
          0)

      errors
      (do (println (str "Errors:\n" (str/join "\n" errors)))
          1)

      (not (:email options))
      (do (println "Error: --email is required") 1)

      (not (:password options))
      (do (println "Error: --password is required") 1)

      (not (:role options))
      (do (println "Error: --role is required") 1)

      :else
      (cli.sys/with-system [sys]
        (let [db (cli.sys/get-db sys)]
          (if (user.i/exists? db (:email options))
            (do (println (format "Error: User with email '%s' already exists" (:email options)))
                1)
            (do (user.i/create! db {:email (:email options)
                                    :password (:password options)
                                    :role (keyword (:role options))})
                (println (format "Created user: %s (role: %s)" (:email options) (:role options)))
                0)))))))

;; =============================================================================
;; list-users command
;; =============================================================================

(def list-users-options
  [["-h" "--help" "Show help for this command"]])

(defn- list-users-cmd [args]
  (let [{:keys [options errors summary]} (parse-opts args list-users-options)]
    (cond
      (:help options)
      (do (println "Usage: clojure -M:dev:cli list-users")
          (println)
          (println "Options:")
          (println summary)
          0)

      errors
      (do (println (str "Errors:\n" (str/join "\n" errors)))
          1)

      :else
      (cli.sys/with-system [sys]
        (let [db (cli.sys/get-db sys)
              users (user.i/get-all db)]
          (if (empty? users)
            (println "No users found.")
            (doseq [user users]
              (println (format "  %d: %s (%s)"
                               (:user/id user)
                               (:user/email user)
                               (name (:user/role user))))))
          0)))))

;; =============================================================================
;; routes command
;; =============================================================================

(def routes-options
  [["-t" "--tree" "Print as hierarchical tree"]
   ["-h" "--help" "Show help for this command"]])

(defn- routes-cmd [args]
  (let [{:keys [options errors summary]} (parse-opts args routes-options)]
    (cond
      (:help options)
      (do (println "Usage: clojure -M:dev:cli routes [options]")
          (println)
          (println "Print the application route tree.")
          (println)
          (println "Options:")
          (println summary)
          0)

      errors
      (do (println (str "Errors:\n" (str/join "\n" errors)))
          1)

      :else
      (do (cli.routes/print-route-tree {:format (if (:tree options) :tree :table)})
          0))))

;; =============================================================================
;; Main entry point
;; =============================================================================

(def subcommands
  {"create-user" {:description "Create a new user"
                  :fn create-user-cmd}
   "list-users"  {:description "List all users"
                  :fn list-users-cmd}
   "routes"      {:description "Print the route tree"
                  :fn routes-cmd}})

(defn- print-usage []
  (println "Usage: clojure -M:dev:cli <command> [options]")
  (println)
  (println "Commands:")
  (doseq [[name {:keys [description]}] subcommands]
    (println (format "  %-15s %s" name description)))
  (println)
  (println "Use 'clojure -M:dev:cli <command> --help' for more information about a command."))

(defn -main [& args]
  (let [[cmd & cmd-args] args]
    (if-let [{:keys [fn]} (get subcommands cmd)]
      (System/exit (fn cmd-args))
      (do (when (and cmd (not= cmd "--help") (not= cmd "-h"))
            (println (format "Unknown command: %s" cmd))
            (println))
          (print-usage)
          (System/exit (if cmd 1 0))))))
