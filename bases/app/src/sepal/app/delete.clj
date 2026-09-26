(ns sepal.app.delete
  "What deleting a record means, for every resource, in one place.

  Two multimethods on the resource type. `blockers` answers what still depends
  on the record; `delete!` removes the record and everything it owns, in one
  transaction.

  A hard delete. Soft delete would need a `where deleted_at is null` filter at
  some 30 query sites and in four FTS tables, and every site that forgot would
  show a deleted record while nothing failed loudly.

  The policy lives here rather than in the components because components do not
  know about each other -- an accession component that counted materials would
  be the first cross-component dependency in the codebase."
  (:require [sepal.accession.interface :as accession.i]
            [sepal.accession.interface.activity :as accession.activity]
            [sepal.collection.interface :as coll.i]
            [sepal.contact.interface :as contact.i]
            [sepal.contact.interface.activity :as contact.activity]
            [sepal.database.interface :as db.i]
            [sepal.error.interface :as error.i]
            [sepal.location.interface :as location.i]
            [sepal.location.interface.activity :as location.activity]
            [sepal.material.interface :as material.i]
            [sepal.material.interface.activity :as material.activity]
            [sepal.media.interface :as media.i]
            [sepal.note.interface :as note.i]
            [sepal.observation.interface :as observation.i]
            [sepal.propagation.interface :as propagation.i]
            [sepal.propagation.interface.activity :as propagation.activity]
            [sepal.synonym.interface :as synonym.i]
            [sepal.tag.interface :as tag.i]
            [sepal.tag.interface.activity :as tag.activity]
            [sepal.taxon.interface :as taxon.i]
            [sepal.taxon.interface.activity :as taxon.activity]))

(defmulti blockers
  "Reasons this record cannot be deleted, as [{:reason kw :count int}].
   Empty means nothing stops it."
  (fn [resource-type _db _record] resource-type))

(defmulti delete!*
  "Delete the record and everything it owns. Called inside a transaction by
   `delete!`, never directly."
  (fn [resource-type _tx _record _deleted-by] resource-type))

(defn- counted [reason n]
  (when (pos? n) {:reason reason :count n}))

;;; ---------------------------------------------------------------------------
;;; accession

(defmethod blockers :accession [_ db accession]
  (->> [(counted :material (material.i/count-by-accession-id db (:accession/id accession)))
        ;; A propagation is history: the record of a genotype the garden grew.
        ;; Deleting the accession it came off would leave that record naming
        ;; nothing.
        (counted :propagation-parent
                 (propagation.i/count-by-parent-accession-id db (:accession/id accession)))]
       (filterv some?)))

(defmethod delete!* :accession [_ tx accession deleted-by]
  (let [id (:accession/id accession)]
    ;; The activity is written first, while the record is still readable: its
    ;; payload names the code and the taxon, which are gone a line later.
    (accession.activity/create! tx accession.activity/deleted deleted-by accession)
    (note.i/delete-for-resource! tx :accession id)
    (tag.i/delete-for-resource! tx :accession id)
    (media.i/unlink-resource! tx :accession id)
    ;; collection cascades by foreign key
    (accession.i/delete! tx id)))

;;; ---------------------------------------------------------------------------
;;; material

(defmethod blockers :material [_ db material]
  ;; material_change cascades. A propagation naming this plant does not: it is
  ;; the record of what was grown from it, and removing the plant would leave
  ;; the record naming nothing.
  (->> [(counted :propagation-parent
                 (propagation.i/count-by-parent-material-id db (:material/id material)))]
       (filterv some?)))

(defmethod delete!* :material [_ tx material deleted-by]
  (let [id (:material/id material)]
    (material.activity/create! tx material.activity/deleted deleted-by material)
    (observation.i/delete-for-resource! tx :material id)
    (tag.i/delete-for-resource! tx :material id)
    (media.i/unlink-resource! tx :material id)
    ;; material_change cascades by foreign key
    (material.i/delete! tx id)))

;;; ---------------------------------------------------------------------------
;;; taxon

(defmethod blockers :taxon [_ db taxon]
  (let [id (:taxon/id taxon)]
    (->> [(counted :accession (accession.i/count-by-taxon-id db id))
          (counted :child-taxon (taxon.i/count-children db id))
          ;; A cross naming this taxon as a parent. Not cleared with the
          ;; delete: removing it would quietly rewrite another taxon's
          ;; ancestry, so this refuses and says so.
          (counted :parentage (taxon.i/count-parentage-children db id))
          ;; A graft's rootstock. The taxon is a service the scion sits on, and
          ;; a propagation record that names it cannot outlive it.
          (counted :propagation-rootstock
                   (propagation.i/count-by-rootstock-taxon-id db id))
          ;; Reference data this garden received, not a record it authored.
          ;; Pressing delete on one is a mis-click.
          (when (:taxon/wfo-taxon-id taxon) {:reason :wfo :count 1})]
         (filterv some?))))

(defmethod delete!* :taxon [_ tx taxon deleted-by]
  (let [id (:taxon/id taxon)]
    (taxon.activity/create! tx taxon.activity/deleted deleted-by taxon)
    (synonym.i/delete-for-taxon! tx id)
    ;; This taxon's own parentage, which references it. Without this the
    ;; foreign key refuses and a hybrid cannot be deleted at all.
    (taxon.i/delete-parentage! tx id)
    (note.i/delete-for-resource! tx :taxon id)
    (tag.i/delete-for-resource! tx :taxon id)
    (media.i/unlink-resource! tx :taxon id)
    (taxon.i/delete! tx id)))

;;; ---------------------------------------------------------------------------
;;; location

(defmethod blockers :location [_ db location]
  (let [id (:location/id location)]
    (->> [(counted :material (material.i/count-by-location-id db id))
          (counted :material-change (material.i/count-changes-by-location-id db id))
          ;; A batch is history, so a finished one holds the bench as firmly
          ;; as a running one.
          (counted :propagation-location
                   (propagation.i/count-by-location-id db id))]
         (filterv some?))))

(defmethod delete!* :location [_ tx location deleted-by]
  (let [id (:location/id location)]
    (location.activity/create! tx location.activity/deleted deleted-by location)
    (observation.i/delete-for-resource! tx :location id)
    (location.i/delete! tx id)))

;;; ---------------------------------------------------------------------------
;;; propagation

(defmethod blockers :propagation [_ db propagation]
  ;; A product is a plant or accession that says it came from here. Deleting
  ;; the propagation would erase where it came from, so the products block it.
  (let [id (:propagation/id propagation)]
    (->> [(counted :propagation-product
                   (+ (count (material.i/list-by-propagation-id db id))
                      (count (accession.i/list-by-propagation-id db id))))]
         (filterv some?))))

(defmethod delete!* :propagation [_ tx propagation deleted-by]
  (propagation.activity/create! tx propagation.activity/deleted deleted-by propagation)
  (propagation.i/delete! tx (:propagation/id propagation)))

;;; ---------------------------------------------------------------------------
;;; contact

(defmethod blockers :contact [_ db contact]
  (->> [(counted :accession
                 (accession.i/count-by-supplier-contact-id db (:contact/id contact)))]
       (filterv some?)))

(defmethod delete!* :contact [_ tx contact deleted-by]
  (contact.activity/create! tx contact.activity/deleted deleted-by contact)
  (contact.i/delete! tx (:contact/id contact)))

;;; ---------------------------------------------------------------------------
;;; collection

(defmethod blockers :collection [_ _db _collection]
  [])

(defmethod delete!* :collection [_ tx collection _deleted-by]
  ;; A collection has no activity type of its own; it is part of its accession,
  ;; and the accession's own updated event is what the changelog shows.
  (coll.i/delete! tx (:collection/id collection)))

;;; ---------------------------------------------------------------------------
;;; tag

(defmethod blockers :tag [_ _db _tag]
  ;; A tag's links belong to it and go with it; nothing else names a tag.
  [])

(defmethod delete!* :tag [_ tx tag deleted-by]
  (tag.activity/create! tx tag.activity/deleted deleted-by tag)
  (tag.i/delete! tx (:tag/id tag)))

;;; ---------------------------------------------------------------------------

(def ^:private blocker-labels
  {:material "%d material record(s) reference this"
   :material-change "%d move(s) in the history reference this location"
   :accession "%d accession(s) reference this"
   :child-taxon "%d taxa name this one as their parent"
   :parentage "%d cross(es) name this taxon as a parent"
   :propagation-parent "%d propagation(s) name this as their parent"
   :propagation-rootstock "%d propagation(s) use this taxon as a rootstock"
   :propagation-location "%d propagation(s) name this location"
   :propagation-product "%d record(s) came from this propagation"
   :wfo "This name comes from the World Flora Online list"})

(defn blocker-label [{:keys [reason count]}]
  (let [fmt (get blocker-labels reason "%d record(s) reference this")]
    (if (re-find #"%d" fmt)
      (format fmt count)
      fmt)))

(defn delete!
  "Delete the record and everything it owns, in one transaction.

   Returns nil, or an error map when a blocker or a constraint stops it."
  [resource-type db record deleted-by]
  (let [found (blockers resource-type db record)]
    (if (seq found)
      (error.i/error ::blocked "Cannot delete this record" {:blockers found})
      (try
        (db.i/with-transaction [tx db]
          (delete!* resource-type tx record deleted-by))
        nil
        (catch Exception ex
          ;; The backstop. Between the check and the write another session can
          ;; insert a child, and the foreign key is what catches that. A check
          ;; that replaces the constraint is a race; one that explains it is a
          ;; message.
          (error.i/ex->error ex))))))
