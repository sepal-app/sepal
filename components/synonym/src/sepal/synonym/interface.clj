(ns sepal.synonym.interface
  (:require [integrant.core :as ig]
            [sepal.synonym.core :as core]))

(defn add-synonym!
  "Assert that `synonym-name` is a synonym of a taxon in this garden.

  Writes no activity event. Activity is written by the route, inside the same
  transaction, exactly as every other component's create does — which is what
  lets an import call this directly without flooding the feed."
  [db data]
  (core/add-synonym! db data))

(defn remove-synonym! [db id]
  (core/remove-synonym! db id))

(defn list-for-taxon [ctx db taxon-id]
  (core/list-for-taxon ctx db taxon-id))

(defmethod ig/init-key ::factory [_ args]
  (core/factory args))
