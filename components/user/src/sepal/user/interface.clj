(ns sepal.user.interface
  (:require [honey.sql :as sql]
            [next.jdbc :as jdbc]
            [next.jdbc.sql :as jdbc.sql]
            [sepal.user.core :as core]
            [sepal.user.interface.spec :as spec]
            [sepal.validation.interface :refer [invalid? validate]]))

(defn parse-int [v]
  (if-not (int? v)
    (Integer/parseInt v)
    v))

(defn get-by-id [db id]
  (jdbc.sql/get-by-id db :public.user (parse-int id)))

(defn exists? [db id-or-email]
  (core/exists? db id-or-email))

(defn create! [db data]
  (cond
    (invalid? spec/CreateUser data)
    (validate spec/CreateUser data)

    :else
    (let [data (assoc data :password [:crypt (:password data) [:gen_salt "bf"]])
          stmt (-> {:insert-into [:public.user]
                    :values [data]
                    :returning [:*]}
                   (sql/format))]
      (jdbc/execute-one! db stmt))))
