(ns sepal.accession.interface-test
  (:require [clojure.set :as set]
            [clojure.test :refer :all]
            [integrant.core :as ig]
            [malli.core :as m]
            [malli.generator :as mg]
            [matcher-combinators.test :refer [match?]]
            [next.jdbc :as jdbc]
            [next.jdbc.sql :as jdbc.sql]
            [sepal.accession.interface :as acc.i]
            [sepal.accession.interface.spec :as acc.spec]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db*
                                           default-system-fixture]]
            [sepal.contact.interface :as contact.i]
            [sepal.error.interface :as err.i]
            [sepal.location.interface :as loc.i]
            [sepal.material.interface :as mat.i]
            [sepal.taxon.interface :as taxon.i]))

(use-fixtures :once default-system-fixture)

(deftest test-create
  (let [db *db*]
    (tf/testing "accession.i/create!"
      {[::taxon.i/factory :key/taxon] {:db db}
       [::contact.i/factory :key/contact] {:db db}
       [::acc.i/factory :key/acc] {:db db
                                   :taxon (ig/ref :key/taxon)
                                   :contact (ig/ref :key/contact)}}
      (fn [{:keys [acc taxon contact]}]
        (is (not (err.i/error? acc)) (err.i/data acc))
        (is (m/validate acc.spec/Accession acc))
        (is (match? {:accession/taxon-id (:taxon/id taxon)
                     :accession/supplier-contact-id (:contact/id contact)}
                    acc))))))

(deftest test-update
  (let [db *db*]
    (tf/testing "update!"
      {[::taxon.i/factory :key/taxon] {:db db}
       [::contact.i/factory :key/contact] {:db db}
       [::acc.i/factory :key/acc] {:db db
                                   :taxon (ig/ref :key/taxon)
                                   :contact (ig/ref :key/contact)}}
      (fn [{:keys [acc taxon]}]
        (let [acc-code (mg/generate acc.spec/code)
              result (acc.i/update! db
                                    (:accession/id acc)
                                    {:code acc-code})]
          (is (not (err.i/error? result)) (err.i/data result))
          (is (m/validate acc.spec/Accession result))
          (is (match? {:accession/taxon-id (:taxon/id taxon)}
                      result)))))))

(deftest test-count-by-taxon-id
  (let [db *db*]
    (tf/testing "count-by-taxon-id returns 0 for taxon with no accessions"
      {[::taxon.i/factory :key/taxon] {:db db}}
      (fn [{:keys [taxon]}]
        (is (= 0 (acc.i/count-by-taxon-id db (:taxon/id taxon))))))

    (tf/testing "count-by-taxon-id returns correct count"
      {[::taxon.i/factory :key/taxon] {:db db}
       [::acc.i/factory :key/acc1] {:db db :taxon (ig/ref :key/taxon)}
       [::acc.i/factory :key/acc2] {:db db :taxon (ig/ref :key/taxon)}}
      (fn [{:keys [taxon]}]
        (is (= 2 (acc.i/count-by-taxon-id db (:taxon/id taxon))))))))

(deftest test-create-with-intended-location
  (let [db *db*]
    (tf/testing "an accession can name the location its material is meant for"
      {[::taxon.i/factory :key/taxon] {:db db}
       [::loc.i/factory :key/loc] {:db db}
       [::acc.i/factory :key/acc] {:db db
                                   :taxon (ig/ref :key/taxon)
                                   :intended-location (ig/ref :key/loc)}}
      (fn [{:keys [acc loc]}]
        (is (not (err.i/error? acc)) (err.i/data acc))
        (is (m/validate acc.spec/Accession acc))
        (is (= (:location/id loc) (:accession/intended-location-id acc)))))))

(deftest test-a-generated-accession-has-no-intended-location
  (let [db *db*]
    (tf/testing "the factory does not invent a location id"
      ;; `mg/generate` fills every key of the closed CreateAccession spec,
      ;; including optional ones, so an unset intended-location-id would arrive
      ;; as a random integer and fail the foreign key.
      {[::taxon.i/factory :key/taxon] {:db db}
       [::acc.i/factory :key/acc] {:db db :taxon (ig/ref :key/taxon)}}
      (fn [{:keys [acc]}]
        (is (nil? (:accession/intended-location-id acc)))))))

(deftest test-an-unknown-intended-location-is-refused
  (let [db *db*]
    (tf/testing "the foreign key holds"
      {[::taxon.i/factory :key/taxon] {:db db}}
      (fn [{:keys [taxon]}]
        (is (thrown? org.sqlite.SQLiteException
                     (acc.i/create! db {:code "ACC-INT-1"
                                        :taxon-id (:taxon/id taxon)
                                        :intended-location-id 999999999}))
            "a location id no location has must be refused")))))

(deftest test-a-location-an-accession-intends-cannot-be-deleted
  (let [db *db*]
    (tf/testing "the reference restricts, and clearing it releases the location"
      {[::taxon.i/factory :key/taxon] {:db db}
       [::loc.i/factory :key/loc] {:db db}
       [::acc.i/factory :key/acc] {:db db
                                   :taxon (ig/ref :key/taxon)
                                   :intended-location (ig/ref :key/loc)}}
      (fn [{:keys [acc loc]}]
        (is (thrown? org.sqlite.SQLiteException
                     (jdbc.sql/delete! db :location {:id (:location/id loc)}))
            "a location an accession intends must not be deletable")
        (acc.i/update! db (:accession/id acc) {:intended-location-id nil})
        (is (nil? (:accession/intended-location-id
                    (acc.i/get-by-id db (:accession/id acc)))))
        (is (some? (jdbc.sql/delete! db :location {:id (:location/id loc)}))
            "once nothing intends it the location can go")))))

(deftest test-awaiting-planting-by-location-id
  (let [db *db*]
    (tf/testing "an accession intended for a location with nothing planted"
      {[::taxon.i/factory :key/taxon] {:db db}
       [::loc.i/factory :key/loc] {:db db}
       [::acc.i/factory :key/acc] {:db db
                                   :taxon (ig/ref :key/taxon)
                                   :intended-location (ig/ref :key/loc)}}
      (fn [{:keys [acc loc taxon]}]
        (is (match? [{:accession/id (:accession/id acc)
                      :accession/code (:accession/code acc)
                      :taxon/name (:taxon/name taxon)}]
                    (acc.i/awaiting-planting-by-location-id
                      db (:location/id loc))))))

    (tf/testing "material in the intended location takes it off the list"
      {[::taxon.i/factory :key/taxon] {:db db}
       [::loc.i/factory :key/loc] {:db db}
       [::acc.i/factory :key/acc] {:db db
                                   :taxon (ig/ref :key/taxon)
                                   :intended-location (ig/ref :key/loc)}
       [::mat.i/factory :key/mat] {:db db
                                   :accession (ig/ref :key/acc)
                                   :location (ig/ref :key/loc)}}
      (fn [{:keys [loc mat]}]
        (is (some? mat))
        (is (= [] (acc.i/awaiting-planting-by-location-id
                    db (:location/id loc))))))

    (tf/testing "material somewhere else leaves it on the list"
      {[::taxon.i/factory :key/taxon] {:db db}
       [::loc.i/factory :key/intended] {:db db}
       [::loc.i/factory :key/elsewhere] {:db db}
       [::acc.i/factory :key/acc] {:db db
                                   :taxon (ig/ref :key/taxon)
                                   :intended-location (ig/ref :key/intended)}
       [::mat.i/factory :key/mat] {:db db
                                   :accession (ig/ref :key/acc)
                                   :location (ig/ref :key/elsewhere)}}
      (fn [{:keys [acc intended mat]}]
        (is (some? mat))
        (is (match? [{:accession/id (:accession/id acc)}]
                    (acc.i/awaiting-planting-by-location-id
                      db (:location/id intended))))))

    (tf/testing "partly planted counts as planted"
      ;; Material in two locations, one of them the intended one. The bed has
      ;; what it was promised, so the accession is not waiting on it.
      {[::taxon.i/factory :key/taxon] {:db db}
       [::loc.i/factory :key/intended] {:db db}
       [::loc.i/factory :key/elsewhere] {:db db}
       [::acc.i/factory :key/acc] {:db db
                                   :taxon (ig/ref :key/taxon)
                                   :intended-location (ig/ref :key/intended)}
       [::mat.i/factory :key/mat1] {:db db
                                    :accession (ig/ref :key/acc)
                                    :location (ig/ref :key/intended)}
       [::mat.i/factory :key/mat2] {:db db
                                    :accession (ig/ref :key/acc)
                                    :location (ig/ref :key/elsewhere)}}
      (fn [{:keys [intended mat1 mat2]}]
        (is (some? mat1))
        (is (some? mat2))
        (is (= [] (acc.i/awaiting-planting-by-location-id
                    db (:location/id intended))))))

    (tf/testing "an accession with no intended location is never listed"
      {[::taxon.i/factory :key/taxon] {:db db}
       [::loc.i/factory :key/loc] {:db db}
       [::acc.i/factory :key/acc] {:db db :taxon (ig/ref :key/taxon)}}
      (fn [{:keys [acc loc]}]
        (is (some? acc))
        (is (= [] (acc.i/awaiting-planting-by-location-id
                    db (:location/id loc))))))))

(deftest test-receipt-fields-round-trip
  (let [db *db*]
    (tf/testing "received-type and quantity-received survive create and update"
      {[::taxon.i/factory :key/taxon] {:db db}
       [::acc.i/factory :key/acc] {:db db
                                   :taxon (ig/ref :key/taxon)
                                   :data {:received-type :unrooted_cutting
                                          :quantity-received 3}}}
      (fn [{:keys [acc]}]
        (is (not (err.i/error? acc)) (err.i/data acc))
        (is (= :unrooted_cutting (:accession/received-type acc)))
        (is (= 3 (:accession/quantity-received acc)))
        (is (m/validate acc.spec/Accession acc))
        (let [updated (acc.i/update! db (:accession/id acc)
                                     {:received-type :scion
                                      :quantity-received 1})]
          (is (not (err.i/error? updated)) (err.i/data updated))
          (is (= :scion (:accession/received-type updated)))
          (is (= 1 (:accession/quantity-received updated))))))))

(deftest test-every-received-type-value-validates
  ;; `rest` on a Malli :enum also yields the schema's properties map, which is
  ;; not a member -- the same trap `create_test/test-create-accepts-every-field`
  ;; documents and `ui.form/enum-select` filters with its `keyword?` default.
  (let [values (filter keyword? (rest acc.spec/received-type))]
    (doseq [v values]
      (is (m/validate acc.spec/received-type v)
          (str v " should be a member of the vocabulary")))
    (is (= 28 (count values))
        "Bauble's recvd_type_values has 28 members and all 28 are carried")
    (is (not (m/validate acc.spec/received-type :not_a_propagule))
        "a value outside the vocabulary is refused")))

(deftest test-received-type-table-matches-the-spec
  ;; The same guard used elsewhere for the same reason: the lookup table and
  ;; the enum are two copies of one vocabulary, and a table row the enum lacks
  ;; makes every record using it unreadable -- `m/coerce` throws and the
  ;; accession cannot be loaded at all. The INSERT and the enum edit ship in
  ;; the same commit, and this fails if they ever drift in either direction.
  (let [db *db*
        in-table (set (map :accession-received-type/name
                           (jdbc/execute! db ["select name from accession_received_type"])))
        in-spec (set (map name (filter keyword? (rest acc.spec/received-type))))]
    (is (= 28 (count in-table)) "28 rows seeded")
    (is (= in-table in-spec)
        (str "table and spec disagree. Only in the table: "
             (set/difference in-table in-spec)
             ". Only in the spec: "
             (set/difference in-spec in-table)))))

(deftest test-quantity-received-zero-round-trips
  (let [db *db*]
    (tf/testing "zero propagules received is a legitimate state, not a missing value"
      {[::taxon.i/factory :key/taxon] {:db db}
       [::acc.i/factory :key/acc] {:db db
                                   :taxon (ig/ref :key/taxon)
                                   :data {:quantity-received 0}}}
      (fn [{:keys [acc]}]
        (is (not (err.i/error? acc)) (err.i/data acc))
        (is (= 0 (:accession/quantity-received acc)))))))

(deftest test-quantity-received-rejects-negative
  (let [db *db*]
    (tf/testing "the spec refuses a negative quantity before the CHECK sees it"
      {[::taxon.i/factory :key/taxon] {:db db}}
      (fn [{:keys [taxon]}]
        ;; `acc.i/create!` throws rather than returning an error map when the
        ;; Malli schema itself refuses the data -- `store.core/create!` calls
        ;; `m/coerce`, which throws on a coercion failure instead of catching
        ;; it. Only the form-submission path (`validate.i/validate-form-values`)
        ;; converts that throw into an error map; a direct interface call does
        ;; not. So the conversion happens here, the same way
        ;; `taxon.rank-test/test-...` converts a thrown validation failure.
        (let [result (try
                       (acc.i/create! db {:code "NEG-1"
                                          :taxon-id (:taxon/id taxon)
                                          :quantity-received -1})
                       (catch Exception ex
                         (err.i/ex->error ex)))]
          (is (err.i/error? result)
              "a negative quantity received is an error, not a row"))))))

(deftest test-receipt-fields-absent-when-not-set
  (let [db *db*]
    (tf/testing "an accession with neither field set is unaffected -- every
                 existing row in every existing garden"
      {[::taxon.i/factory :key/taxon] {:db db}}
      (fn [{:keys [taxon]}]
        (let [created (acc.i/create! db {:code "PLAIN-1"
                                         :taxon-id (:taxon/id taxon)})]
          (is (not (err.i/error? created)) (err.i/data created))
          (is (nil? (:accession/received-type created)))
          (is (nil? (:accession/quantity-received created)))
          (is (m/validate acc.spec/Accession created))
          ;; Created directly rather than through `::acc.i/factory`, because
          ;; the factory's `mg/generate` would fill received-type and
          ;; quantity-received with random valid values instead of leaving
          ;; them genuinely absent. Not tracked by integrant, so it must be
          ;; deleted here -- otherwise it outlives the taxon factory's :key/taxon
          ;; and the halt-key delete on that taxon hits its foreign key.
          (jdbc.sql/delete! db :accession {:id (:accession/id created)}))))))

(deftest test-accession-fts-still-syncs
  ;; The migration adds two columns to accession, which has an external-content
  ;; FTS table and three triggers hanging off it. The triggers name their
  ;; columns explicitly and the update trigger fires only AFTER UPDATE OF code,
  ;; so they should be indifferent to the new columns -- asserted rather than
  ;; assumed.
  (let [db *db*]
    (tf/testing "accession_fts"
      {[::taxon.i/factory :key/taxon] {:db db}}
      (fn [{:keys [taxon]}]
        (let [created (acc.i/create! db {:code "FTS-ONE"
                                         :taxon-id (:taxon/id taxon)
                                         :received-type :seed
                                         :quantity-received 4})
              id (:accession/id created)]
          (is (not (err.i/error? created)) (err.i/data created))
          (is (= "FTS-ONE"
                 (:accession-fts/code
                   (jdbc/execute-one! db ["select code from accession_fts where rowid = ?" id])))
              "the insert trigger still populates accession_fts")
          (acc.i/update! db id {:code "FTS-TWO"})
          (is (= "FTS-TWO"
                 (:accession-fts/code
                   (jdbc/execute-one! db ["select code from accession_fts where rowid = ?" id])))
              "the update trigger still syncs accession_fts")
          (jdbc.sql/delete! db :accession {:id id})
          (is (nil? (jdbc/execute-one! db ["select rowid from accession_fts where rowid = ?" id]))
              "the delete trigger still removes from accession_fts"))))))
