(ns sepal.accession.core
  (:require [integrant.core :as ig]
            [malli.generator :as mg]
            [next.jdbc.sql :as jdbc.sql]
            [sepal.accession.interface.spec :as spec]
            [sepal.code-template.interface :as ct.i]
            [sepal.database.interface :as db.i]
            [sepal.store.interface :as store.i])
  (:import [java.time LocalDate]))

(defn get-by-id [db id]
  (store.i/get-by-id db :accession id spec/Accession))

(defn create! [db data]
  (store.i/create! db :accession data spec/CreateAccession spec/Accession))

(defn update! [db id data]
  (store.i/update! db :accession id data spec/UpdateAccession spec/Accession))

(defn delete! [db id]
  (jdbc.sql/delete! db :accession {:id id})
  nil)

(defn count-by-taxon-id
  "Count accessions for a given taxon."
  [db taxon-id]
  (db.i/count db {:select [:id]
                  :from [:accession]
                  :where [:= :taxon_id taxon-id]}))

(defn count-by-supplier-contact-id
  "Count accessions for a given supplier contact."
  [db contact-id]
  (db.i/count db {:select [:id]
                  :from [:accession]
                  :where [:= :supplier_contact_id contact-id]}))

(defn awaiting-planting-by-location-id
  "Accessions intended for this location with no material in it yet.

  The `not exists` clause is what makes this a work queue rather than a
  history: without it an accession stays listed against the bed forever, and
  75 of the 91 rows Belize Botanic Gardens carries are already planted where
  they were meant to go."
  [db location-id]
  (db.i/execute! db {:select [:a.id :a.code :a.date_received :t.name]
                     :from [[:accession :a]]
                     :join [[:taxon :t] [:= :a.taxon_id :t.id]]
                     :where [:and
                             [:= :a.intended_location_id location-id]
                             [:not [:exists {:select [[[:inline 1]]]
                                             :from [[:material :m]]
                                             :where [:and
                                                     [:= :m.accession_id :a.id]
                                                     [:= :m.location_id :a.intended_location_id]]}]]]
                     :order-by [[:a.date_received :asc] [:a.code :asc]]}))

(defn count-all
  "Count every accession in the garden."
  [db]
  (db.i/count db {:select [:id]
                  :from [:accession]}))

(defn next-code
  "The next accession code for `template`, derived from the codes already
  stored rather than from a counter. nil when the template is blank or
  unparseable, so a broken setting leaves the field empty instead of 500ing.

  The `like` narrows the scan in SQL and the shape match runs in Clojure,
  because SQLite's glob cannot express a bounded digit run. A prefix holding
  a LIKE wildcard only widens the scan; the shape match still decides."
  ([db template]
   (next-code db template (LocalDate/now)))
  ([db template ^LocalDate date]
   (when-let [prefix (ct.i/scan-prefix template date)]
     (->> (db.i/execute! db {:select [:code]
                             :from [:accession]
                             :where [:like :code (str prefix "%")]})
          (map :accession/code)
          (#(ct.i/next-code template % date))))))

(create-ns 'sepal.accession.interface)
(alias 'acc.i 'sepal.accession.interface)

(defn factory
  "Build an accession for tests. Fields are generated from the spec unless
  `:data` overrides them — which a test needs when behaviour depends on a
  particular value, since a generated one differs run to run."
  [{:keys [db taxon contact intended-location data] :as args}]
  (let [generated (-> (mg/generate spec/CreateAccession)
                      (assoc :taxon-id (:taxon/id taxon))
                      (assoc :supplier-contact-id (when contact (:contact/id contact)))
                      ;; Explicit, because a generated value would be a random
                      ;; integer and the foreign key would refuse it.
                      (assoc :intended-location-id (when intended-location
                                                     (:location/id intended-location))))
        result (create! db (merge generated data))]
    (vary-meta result assoc :db db)))

(defmethod ig/halt-key! ::acc.i/factory [_ data]
  (when data
    (let [{:keys [db]} (meta data)]
      (jdbc.sql/delete! db :accession {:id (:accession/id data)}))))
