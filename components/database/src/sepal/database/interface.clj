(ns sepal.database.interface
  (:require [integrant.core :as ig]
            [next.jdbc :as jdbc]))

(defmethod ig/init-key ::db [_ cfg]
  (jdbc/get-datasource cfg))

(defmethod ig/halt-key! ::db [_ db]
  ;; TODO: If we add a connection pool then we'll need to close it here.
  )
