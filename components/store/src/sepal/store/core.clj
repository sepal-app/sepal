(ns sepal.store.core
  (:require [malli.core :as m]
            [malli.transform :as mt]
            [next.jdbc.sql :as jdbc.sql]
            [sepal.database.interface :as db.i]))

(def transformer
  (mt/transformer
    mt/default-value-transformer
    mt/strip-extra-keys-transformer
    {:name :store}))

(defn coerce [spec data]
  (m/coerce spec data transformer))

(defn encode [spec data]
  (m/encode spec data transformer))

(defn get-by-id
  [db table id spec]
  (let [spec-props (m/properties spec)
        opts (some-> {}
                     (:store/columns spec-props)
                     (assoc :columns (:store/columns spec-props)))]
    (when-let [result (jdbc.sql/get-by-id db table id opts)]
      (coerce spec result))))

(defn update! [db table id data spec result-spec]
  ;; First we db/coerce the data into the spec to make sure it validates and
  ;; then we encode the data so its in the form expected by the database
  (let [data (->> data
                  (coerce spec)
                  (encode spec))]
    (when-let [_result (db.i/execute-one! db
                                          {:update table
                                           :set data
                                           :where [:= :id id]}

                                         ;; TODO: use the store/columns keys from the properties
                                          {:returning-keys 1})]
      ;; If we have a result-spec then coerce the result
      (get-by-id db table id result-spec))))

(defn create! [db table data spec result-spec]
  (let [data (->> data
                  (coerce spec)
                  (encode spec))]
    (when-let [result (db.i/execute-one! db {:insert-into [table]
                                             :values [data]
                                             ;; TODO: use the store/columns keys from the properties
                                             :returning [:*]})]
      ;; If we have a result-spec then coerce the result
      (cond->> result
        (some? result-spec)
        (coerce result-spec)))))
