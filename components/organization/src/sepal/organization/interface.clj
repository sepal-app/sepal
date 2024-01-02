(ns sepal.organization.interface
  (:require [next.jdbc :as jdbc]
            [next.jdbc.sql :as jdbc.sql]
            [honey.sql :as sql]
            [sepal.organization.interface.spec :as spec]
            [sepal.validation.interface :refer [invalid? validate]]))

(defn parse-int [v]
  (if-not (int? v)
    (Integer/parseInt v)
    v))

(defn get-by-id [db id]
  (jdbc.sql/get-by-id db :organization (parse-int id)))

(defn create! [db data]
  (cond
    (invalid? spec/CreateOrganization data)
    (validate spec/CreateOrganization data)

    :else
    (let [stmt (-> {:insert-into :organization
                    :values [data]
                    :returning :*}
                   (sql/format))]
      (jdbc/execute-one! db stmt))))

(defn assign-role [db data]
  (let [stmt (-> {:insert-into :organization-user
                  :values [data]
                  :returning :*}
                 (sql/format))]
    (try
      (jdbc/execute-one! db stmt)
      (catch Exception e
        {:error {:message (ex-message e)}}))))
