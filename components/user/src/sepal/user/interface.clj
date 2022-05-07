(ns sepal.user.interface
  (:require [next.jdbc :as jdbc]
            [next.jdbc.sql :as jdbc.sql]
            [honey.sql :as sql]
            [sepal.user.interface.spec :as spec]
            [sepal.validation.interface :refer [invalid? validation-error]]))

(defn parse-int [v]
  (if-not (int? v)
    (Integer/parseInt v)
    v))

(defn get-by-id [db id]
  (jdbc.sql/get-by-id db :public.user (parse-int id)))

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
