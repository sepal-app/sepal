(ns sepal.user.interface
  (:require [next.jdbc :as jdbc]
            [honey.sql :as sql]
            [sepal.user.interface.spec :as spec]
            [sepal.validation.interface :refer [invalid? validation-error]]))

(defn create! [db data]
  (cond
    (invalid? spec/CreateUser data)
    (validation-error spec/CreateUser data)

    :else
    (let [data (assoc data :password [:crypt () (:password data) [:gen_salt "bf"]])
          stmt (-> {:insert-into [:public.user]
                    :values [data]
                    :returning [:*]}
                   (sql/format))]
      (try
        (jdbc/execute-one! db stmt)
        (catch Exception e
          [{:message (ex-message e)}])))))
