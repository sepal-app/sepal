(ns sepal.collection.interface)

(defn get-by-id [db id]
  (core/get-by-id db id))

(defn create! [db data]
  (core/create! db data))

(defn update! [db id data]
  (core/update! db id data))

(defmethod ig/init-key ::factory [_ args]
  (core/factory args))
