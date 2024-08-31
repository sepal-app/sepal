(ns sepal.store.core
  (:require [malli.core :as m]
            [malli.transform :as mt]
            [next.jdbc.sql :as jdbc.sql]
            [sepal.error.interface :as error.i]))

(def transformer
  (mt/transformer
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

(defn update! [db table id data spec]
  (try
    ;; First we db/coerce the data into the spec to make sure it validates and
    ;; then we encode the data so its in the form expected by the database
    (let [data (->> data
                    (coerce spec)
                    (encode spec))
          result-spec (-> spec m/properties :store/result)]
      (when-let [result (jdbc.sql/update! db
                                          table
                                          data
                                          {:id id}
                                          {:return-keys 1})]
        ;; If the spec has a :store/result property then coerce the result
        (cond->> result
          (some? result-spec)
          (coerce result-spec))))
    (catch Exception ex
      (error.i/ex->error ex))))

(defn create! [db table data spec]
  (try
    (let [data (->> data
                    (coerce spec)
                    (encode spec))
          result-spec (-> spec m/properties :store/result)]
      (when-let [result (jdbc.sql/insert! db
                                          table
                                          data
                                          {:return-keys true})]
        (cond->> result
          (some? result-spec)
          (coerce result-spec))))
    (catch Exception ex
      (tap> (str "ex: " ex))
      (error.i/ex->error ex))))
