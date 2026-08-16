(ns sepal.app.cli.system-test
  (:require [babashka.fs :as fs]
            [clojure.test :refer [deftest is testing]]
            [next.jdbc :as jdbc]
            [sepal.app.cli.system :as cli.system]
            [sepal.app.instance :as instance]))

(deftest test-cli-pool-loads-spatialite
  (testing "the CLI opens a database the same way the server does, so a command
            that touches a geometry column cannot fail where a request would not"
    (let [dir (fs/create-temp-dir {:prefix "sepal-cli-system"})
          db-path (str (fs/path dir "sepal.db"))]
      (try
        (instance/provision! {:db-path db-path})
        (let [system (cli.system/start-system
                       {:db-path db-path
                        :extension-library-path (System/getenv "EXTENSIONS_LIBRARY_PATH")})]
          (try
            (let [db (cli.system/get-db system)
                  row (jdbc/execute-one! db ["select spatialite_version() as version"])]
              (is (some? (:version row))
                  "spatialite_version() must resolve, which it only can if the
                   extension was loaded into this connection"))
            (finally
              (cli.system/stop-system system))))
        (finally
          (fs/delete-tree dir))))))

(deftest test-cli-pool-sets-busy-timeout
  (testing "busy_timeout reaches the CLI connection"
    (let [dir (fs/create-temp-dir {:prefix "sepal-cli-busy"})
          db-path (str (fs/path dir "sepal.db"))]
      (try
        (instance/provision! {:db-path db-path})
        (let [system (cli.system/start-system
                       {:db-path db-path
                        :extension-library-path (System/getenv "EXTENSIONS_LIBRARY_PATH")})]
          (try
            (let [db (cli.system/get-db system)
                  row (jdbc/execute-one! db ["pragma busy_timeout"])]
              (is (= 5000 (first (vals row)))))
            (finally
              (cli.system/stop-system system))))
        (finally
          (fs/delete-tree dir))))))
