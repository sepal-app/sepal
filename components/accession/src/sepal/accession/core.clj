(ns sepal.accession.core
  (:require [sepal.accession.core :as core]
            [sepal.accession.interface.spec :as spec]
            [sepal.store.interface :as store.i]))

(defn get-by-id [db id]
  (store.i/get-by-id db :accession id spec/Accession))

(defn create! [db data]
  (store.i/create! db :accession data spec/CreateAccession))

(defn update! [db id data]
  (store.i/update! db :accession id data spec/UpdateAccession))
