(ns sepal.app.cli-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [sepal.activity.interface :as activity.i]
            [sepal.app.cli :as cli]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.user.interface :as user.i]))

(use-fixtures :once default-system-fixture)

;; =============================================================================
;; CLI load test - verifies the CLI namespace loads and its commands are wired
;; =============================================================================

(deftest cli-loads-without-error-test
  ;; This used to shell out to `clojure -M:dev:cli list-users --help`, which cost
  ;; 24 seconds: the child JVM loaded the whole application again. It bought
  ;; nothing. The child ran -M:dev:cli, so development/src was on its classpath
  ;; and Clojure auto-loaded user.clj — the same malli.i/init this JVM already
  ;; ran. Both processes met the CLI under identical preconditions, and
  ;; -M:dev:cli is the only way anyone invokes it: the CLI ships in no project
  ;; and no image.
  (testing "every subcommand resolves to a function"
    (is (= #{"create-user" "list-users" "routes"} (set (keys cli/subcommands))))
    (doseq [[name {:keys [fn description]}] cli/subcommands]
      (is (ifn? fn) (format "%s has no function" name))
      (is (seq description) (format "%s has no description" name))))

  (testing "list-users --help prints its usage and succeeds, without a database"
    (let [out (java.io.StringWriter.)
          exit (binding [*out* out]
                 ((:fn (get cli/subcommands "list-users")) ["--help"]))]
      (is (= 0 exit))
      (is (str/includes? (str out) "list-users")))))

;; =============================================================================
;; User interface tests (using test system)
;; =============================================================================

(deftest create-user-test
  (testing "creates user with valid data"
    (let [email "cli-test@example.com"
          _ (user.i/create! *db* {:email email
                                  :password "password123"
                                  :role :editor})
          user (user.i/get-by-email *db* email)]
      (is (some? user))
      (is (= email (:user/email user)))
      (is (= :editor (:user/role user)))))

  (testing "role is required and must be valid"
    (is (thrown? Exception
                 (user.i/create! *db* {:email "no-role@example.com"
                                       :password "password123"}))))

  ;; A user/created event is written by the three route handlers that create
  ;; accounts, never by the component. This path and instance.clj's two
  ;; provisioning paths run with no session, so there is no actor to put in
  ;; activity.created_by, which is not null. The gap is deliberate; this pins
  ;; it so nobody closes it by quietly inventing a system user.
  (testing "creating a user outside a route writes no activity"
    (let [email "cli-no-activity@example.com"
          user (user.i/create! *db* {:email email
                                     :password "password123"
                                     :role :reader})]
      (is (empty? (activity.i/get-by-resource *db*
                                              :resource-type :user
                                              :resource-id (:user/id user)))))))

(deftest get-all-users-test
  (testing "returns all users"
    (let [_ (user.i/create! *db* {:email "user1@example.com"
                                  :password "password123"
                                  :role :admin})
          _ (user.i/create! *db* {:email "user2@example.com"
                                  :password "password123"
                                  :role :reader})
          users (user.i/get-all *db*)]
      (is (>= (count users) 2))
      (is (every? :user/role users)))))
