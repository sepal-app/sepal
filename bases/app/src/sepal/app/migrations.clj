(ns sepal.app.migrations
  (:require [integrant.core :as ig]
            [migratus.core :as migratus]))

(defmethod ig/init-key ::migratus [_ cfg]
  cfg)

(comment
  (do
    (require '[integrant.repl.state :refer [system]])
    (def cfg (::migratus system))
    cfg)

  ;; Create new migration file
  (migratus/create cfg "initial migration")

  ;; Apply pending migrations
  (migratus/migrate cfg)
  ())
