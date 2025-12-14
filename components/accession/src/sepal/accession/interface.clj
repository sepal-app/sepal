(ns sepal.accession.interface
  (:require [integrant.core :as ig]
            [sepal.accession.core :as core]))

(defn get-by-id [db id]
  (core/get-by-id db id))

(defn create! [db data]
  (core/create! db data))

(defn update! [db id data]
  (core/update! db id data))

(defn count-by-taxon-id
  "Count accessions for a given taxon."
  [db taxon-id]
  (core/count-by-taxon-id db taxon-id))

(defn count-by-supplier-contact-id
  "Count accessions for a given supplier contact."
  [db contact-id]
  (core/count-by-supplier-contact-id db contact-id))

(defmethod ig/init-key ::factory [_ args]
  (core/factory args))
