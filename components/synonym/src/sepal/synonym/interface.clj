(ns sepal.synonym.interface
  (:require [integrant.core :as ig]
            [sepal.synonym.core :as core]
            [sepal.synonym.reference :as reference]
            [taoensso.telemere :as tel]))

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

(defn list-for-accepted-core
  "Every WFO synonym of the taxon with this 14-character id core, read from the
  reference pool. A nil pool (no reference file) yields []."
  [pool accepted-core]
  (reference/list-for-accepted-core pool accepted-core))

(defn search
  "WFO synonyms whose name matches the query, prefix-extended, read from the
  reference pool. A nil pool or an empty query yields []."
  [pool query]
  (reference/search pool query))

(defn version
  "The WFO release the reference pool was built from, for logging. nil when
  there is no pool."
  [pool]
  (reference/version pool))

(defmethod ig/init-key ::factory [_ args]
  (core/factory args))

(defmethod ig/init-key ::reference-pool [_ {:keys [path]}]
  (let [pool (reference/open path)]
    (if pool
      (tel/log! {:level :info :data {:path path :wfo-version (reference/version pool)}}
                "Opened the WFO synonym reference")
      (tel/log! {:level :info :data {:path path}}
                "No WFO synonym reference; synonym search covers local rows only"))
    pool))

(defmethod ig/halt-key! ::reference-pool [_ pool]
  (reference/close! pool))
