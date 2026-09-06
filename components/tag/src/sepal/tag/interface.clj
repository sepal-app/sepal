(ns sepal.tag.interface
  (:require [integrant.core :as ig]
            [sepal.tag.core :as core]))

(defn available?
  "Whether this database has `tag` and `tag_link` at all.

  The reads below gate on this themselves and return nothing, so no call site
  can 500 by forgetting. A caller that offers a *write* -- an Add form, a
  rename, a section in the nav -- has to ask, because there is nowhere to put
  the row and a control that fails is worse than one that is absent."
  [ctx]
  (core/available? ctx))

;; The reads take `ctx` first so the gate lives in one place; the writes do not,
;; because they are only reachable from a handler that has already asked
;; `available?` and refused the request.
(defn get-by-id [ctx db id] (core/get-by-id ctx db id))
(defn get-by-name [ctx db name] (core/get-by-name ctx db name))
(defn list-all [ctx db] (core/list-all ctx db))
(defn create! [db data] (core/create! db data))
(defn update! [db id data] (core/update! db id data))
(defn delete! [db id] (core/delete! db id))
(defn tag! [db tag-id resource-id resource-type] (core/tag! db tag-id resource-id resource-type))
(defn untag! [db tag-id resource-id resource-type] (core/untag! db tag-id resource-id resource-type))
(defn get-for-resource [ctx db resource-type resource-id] (core/get-for-resource ctx db resource-type resource-id))
(defn get-tagged [ctx db tag-id] (core/get-tagged ctx db tag-id))

(defmethod ig/init-key ::factory [_ args]
  (core/factory args))
