(ns sepal.user.core
  (:require [sepal.database.interface :as db.i]))

(defn exists? [db id-or-email]
  (let [where (if (int? id-or-email)
                [:= :id id-or-email]
                [:= :email id-or-email])]
    (db.i/exists? db {:select 1
                      :from :public.user
                      :where where})))
