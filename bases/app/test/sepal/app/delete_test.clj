(ns sepal.app.delete-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [integrant.core :as ig]
            [next.jdbc.sql :as jdbc.sql]
            [sepal.accession.interface :as accession.i]
            [sepal.activity.interface :as activity.i]
            [sepal.app.delete :as app.delete]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.collection.interface :as coll.i]
            [sepal.contact.interface :as contact.i]
            [sepal.error.interface :as error.i]
            [sepal.location.interface :as location.i]
            [sepal.material.interface :as material.i]
            [sepal.media.interface :as media.i]
            [sepal.note.interface :as note.i]
            [sepal.observation.interface :as observation.i]
            [sepal.propagation.interface :as propagation.i]
            [sepal.synonym.interface :as synonym.i]
            [sepal.tag.interface :as tag.i]
            [sepal.taxon.interface :as taxon.i]
            [sepal.user.interface :as user.i]))

(use-fixtures :once default-system-fixture)

(defn- clear-activity!
  "activity.created_by is a real foreign key, so every event this suite writes
  has to go before the user fixture can delete its user."
  [user]
  (jdbc.sql/delete! *db* :activity {:created_by (:user/id user)}))

(defn- accession-fixtures
  "A function, not a def: *db* is bound by the fixture at run time, and a
  top-level map would capture the nil it holds at load time."
  []
  {[::user.i/factory :key/user] {:db *db*}
   [::location.i/factory :key/location] {:db *db*}
   [::contact.i/factory :key/contact] {:db *db*}
   [::taxon.i/factory :key/taxon] {:db *db*}
   [::accession.i/factory :key/accession] {:db *db*
                                           :taxon (ig/ref :key/taxon)
                                           :contact (ig/ref :key/contact)}})

;;; ---------------------------------------------------------------------------
;;; accession

(deftest test-accession-with-material-is-blocked
  (tf/testing "blockers :accession"
    (assoc (accession-fixtures)
           [::material.i/factory :key/material] {:db *db*
                                                 :accession (ig/ref :key/accession)
                                                 :location (ig/ref :key/location)})
    (fn [{:keys [user accession]}]
      (try
        (is (= [{:reason :material :count 1}]
               (app.delete/blockers :accession *db* accession)))
        (let [result (app.delete/delete! :accession *db* accession (:user/id user))]
          (is (error.i/error? result))
          (is (some? (accession.i/get-by-id *db* (:accession/id accession)))
              "A blocked delete changes nothing"))
        (finally
          (clear-activity! user))))))

(deftest test-accession-deletes-what-it-owns
  (tf/testing "delete! :accession"
    (accession-fixtures)
    (fn [{:keys [user accession]}]
      (let [id (:accession/id accession)
            collection (coll.i/create! *db* {:accession-id id
                                             :collector "A. Collector"})
            note (note.i/create! *db* {:body "a note"
                                       :resource-type :accession
                                       :resource-id id
                                       :created-by (:user/id user)})
            tag (tag.i/create! *db* {:name "delete-test accession tag"})
            media (media.i/create! *db* {:s3-bucket "b" :s3-key "k.jpg"
                                         :size-in-bytes 1 :media-type "image/jpeg"
                                         :created-by (:user/id user)})]
        (try
          (tag.i/tag! *db* (:tag/id tag) id :accession)
          (media.i/link! *db* (:media/id media) id :accession)
          (is (empty? (app.delete/blockers :accession *db* accession)))
          (is (nil? (app.delete/delete! :accession *db* accession (:user/id user))))
          (is (nil? (accession.i/get-by-id *db* id)))
          (is (nil? (coll.i/get-by-id *db* (:collection/id collection)))
              "the collection goes with it, by foreign key")
          (is (nil? (note.i/get-by-id *db* (:note/id note)))
              "and its notes, in code")
          (is (empty? (tag.i/get-for-resource *db* :accession id))
              "and its tag links")
          (is (nil? (media.i/get-link *db* (:media/id media)))
              "and its media links")
          (is (some? (tag.i/get-by-id *db* (:tag/id tag)))
              "but not the tag itself")
          (is (some? (media.i/get-by-id *db* (:media/id media)))
              "nor the media itself")
          (finally
            (tag.i/delete! *db* (:tag/id tag))
            (media.i/delete! *db* (:media/id media))
            (clear-activity! user)))))))

(deftest test-a-deleted-accession-leaves-its-activity
  (tf/testing "activity survives"
    (accession-fixtures)
    (fn [{:keys [user accession]}]
      (let [id (:accession/id accession)]
        (try
          (app.delete/delete! :accession *db* accession (:user/id user))
          (let [events (activity.i/get-by-resource
                         *db* :resource-type :accession :resource-id id)]
            (is (seq events) "the history outlives the record")
            (is (some #(= :accession/deleted (:activity/type %)) events)
                "including the deletion itself"))
          (finally
            (clear-activity! user)))))))

;;; ---------------------------------------------------------------------------
;;; material

(deftest test-material-owns-its-history
  (tf/testing "delete! :material"
    (assoc (accession-fixtures)
           [::material.i/factory :key/material] {:db *db*
                                                 :accession (ig/ref :key/accession)
                                                 :location (ig/ref :key/location)})
    (fn [{:keys [user material]}]
      (let [id (:material/id material)
            observation (observation.i/create! *db* {:resource-type :material
                                                     :resource-id id
                                                     :type "general"
                                                     :observed-on "2026-03-14"
                                                     :note "a material observation"
                                                     :created-by (:user/id user)})]
        (try
          (is (empty? (app.delete/blockers :material *db* material))
              "nothing names this plant, so material_change is all it owns")
          (is (nil? (app.delete/delete! :material *db* material (:user/id user))))
          (is (nil? (material.i/get-by-id *db* id)))
          (is (nil? (observation.i/get-by-id *db* (:observation/id observation))))
          (finally
            (clear-activity! user)))))))

;;; ---------------------------------------------------------------------------
;;; propagation

(deftest test-a-propagation-blocks-its-parent-and-bench
  ;; A propagation is history: the record of a genotype the garden grew, like a
  ;; material_change. A parent, bench or rootstock it names cannot be deleted
  ;; while it exists, the way a location material moved through cannot.
  (tf/testing "blockers :propagation"
    (assoc (accession-fixtures)
           [::material.i/factory :key/material] {:db *db*
                                                 :accession (ig/ref :key/accession)
                                                 :location (ig/ref :key/location)})
    (fn [{:keys [user accession material location taxon]}]
      (let [propagation (propagation.i/create! *db* {:type :cutting
                                                     :parent-accession-id (:accession/id accession)
                                                     :parent-material-id (:material/id material)
                                                     :location-id (:location/id location)
                                                     :rootstock-taxon-id (:taxon/id taxon)})]
        (try
          (is (= [{:reason :propagation-parent :count 1}]
                 (app.delete/blockers :material *db* material))
              "the plant the cuttings came off")
          (is (some #(= :propagation-parent (:reason %))
                    (app.delete/blockers :accession *db* accession))
              "the accession the batch came off")
          (is (some #(= :propagation-location (:reason %))
                    (app.delete/blockers :location *db* location))
              "the bench the batch is on")
          (is (some #(= :propagation-rootstock (:reason %))
                    (app.delete/blockers :taxon *db* taxon))
              "the taxon a graft sits on")
          (is (error.i/error?
                (app.delete/delete! :material *db* material (:user/id user))))
          (is (some? (material.i/get-by-id *db* (:material/id material)))
              "a refused delete writes nothing")
          (finally
            (jdbc.sql/delete! *db* :propagation {:id (:propagation/id propagation)})))))))

(deftest test-a-product-does-not-block-its-own-deletion
  ;; The product carries the link, so removing it breaks no reference. Only the
  ;; parent side is history that has to outlive the record it names.
  (tf/testing "blockers :material with a product link"
    (assoc (accession-fixtures)
           [::material.i/factory :key/material] {:db *db*
                                                 :accession (ig/ref :key/accession)
                                                 :location (ig/ref :key/location)})
    (fn [{:keys [user accession material]}]
      (let [propagation (propagation.i/create! *db* {:type :cutting
                                                     :parent-accession-id (:accession/id accession)})]
        (try
          (material.i/update! *db* (:material/id material)
                              {:propagation-id (:propagation/id propagation)})
          (is (empty? (app.delete/blockers :material *db* material)))
          (is (nil? (app.delete/delete! :material *db* material (:user/id user))))
          (finally
            (clear-activity! user)
            (jdbc.sql/delete! *db* :propagation {:id (:propagation/id propagation)})))))))

;;; ---------------------------------------------------------------------------
;;; taxon

(deftest test-a-wfo-taxon-cannot-be-deleted
  (tf/testing "blockers :taxon"
    {[::user.i/factory :key/user] {:db *db*}}
    (fn [{:keys [user]}]
      (let [wfo (taxon.i/create! *db* {:name "Wfo test name"
                                       :rank :species
                                       :wfo-taxon-id "wfo-0000000001-2025-06"})
            local (taxon.i/create! *db* {:name "Local test name" :rank :species})]
        (try
          (is (= [{:reason :wfo :count 1}]
                 (app.delete/blockers :taxon *db* wfo)))
          (is (empty? (app.delete/blockers :taxon *db* local)))
          (is (nil? (app.delete/delete! :taxon *db* local (:user/id user))))
          (is (nil? (taxon.i/get-by-id *db* (:taxon/id local))))
          (finally
            (taxon.i/delete! *db* (:taxon/id wfo))
            (clear-activity! user)))))))

(deftest test-a-taxon-with-accessions-or-children-is-blocked
  (tf/testing "blockers :taxon, by reference"
    (accession-fixtures)
    (fn [{:keys [taxon]}]
      ;; The factory generates wfo-taxon-id or not at random, and a WFO hit is
      ;; a second blocker. Clear it so this test sees only the reference it is
      ;; about.
      (taxon.i/update! *db* (:taxon/id taxon) {:wfo-taxon-id nil})
      (is (= [{:reason :accession :count 1}]
             (app.delete/blockers :taxon *db* (taxon.i/get-by-id *db* (:taxon/id taxon))))
          "an accession names it"))))

(deftest test-deleting-a-taxon-takes-its-synonyms
  (tf/testing "delete! :taxon"
    {[::user.i/factory :key/user] {:db *db*}
     [::taxon.i/factory :key/taxon] {:db *db*}}
    (fn [{:keys [user taxon]}]
      (let [id (:taxon/id taxon)]
        (try
          (synonym.i/add-synonym! *db* {:taxon-id id
                                        :synonym-name "Encyclia cochleata"})
          ;; A generated taxon may carry a wfo-taxon-id, which would block the
          ;; delete for a reason this test is not about.
          (taxon.i/update! *db* id {:wfo-taxon-id nil})
          (let [taxon (taxon.i/get-by-id *db* id)]
            (is (empty? (app.delete/blockers :taxon *db* taxon)))
            (is (nil? (app.delete/delete! :taxon *db* taxon (:user/id user))))
            (is (nil? (taxon.i/get-by-id *db* id)))
            (is (empty? (synonym.i/list-for-taxon {:synonym-reference nil} *db* id))
                "a synonym is the taxon's other name and has no meaning without it"))
          (finally
            (clear-activity! user)))))))

;;; ---------------------------------------------------------------------------
;;; location

(deftest test-a-location-with-move-history-is-blocked
  (tf/testing "blockers :location"
    (assoc (accession-fixtures)
           [::location.i/factory :key/destination] {:db *db*}
           [::material.i/factory :key/material] {:db *db*
                                                 :accession (ig/ref :key/accession)
                                                 :location (ig/ref :key/location)})
    (fn [{:keys [location destination material]}]
      (material.i/update! *db* (:material/id material)
                          {:location-id (:location/id destination)})
      (is (= [{:reason :material-change :count 1}]
             (app.delete/blockers :location *db* location))
          "empty of plants, and still held by the history of what left it")
      ;; The material's change rows cascade with it, which frees both locations
      ;; for the fixture teardown.
      (material.i/delete! *db* (:material/id material)))))

(deftest test-a-clean-location-deletes
  (tf/testing "delete! :location"
    {[::user.i/factory :key/user] {:db *db*}
     [::location.i/factory :key/location] {:db *db*}}
    (fn [{:keys [user location]}]
      (try
        (is (empty? (app.delete/blockers :location *db* location)))
        (is (nil? (app.delete/delete! :location *db* location (:user/id user))))
        (is (nil? (location.i/get-by-id *db* (:location/id location))))
        (finally
          (clear-activity! user))))))

(deftest test-deleting-a-location-takes-its-observations
  (tf/testing "delete! :location clears observations"
    {[::user.i/factory :key/user] {:db *db*}
     [::location.i/factory :key/location] {:db *db*}}
    (fn [{:keys [user location]}]
      (let [id (:location/id location)
            observation (observation.i/create! *db* {:resource-type :location
                                                     :resource-id id
                                                     :type "general"
                                                     :observed-on "2026-03-14"
                                                     :note "a location observation"
                                                     :created-by (:user/id user)})]
        (try
          (is (empty? (app.delete/blockers :location *db* location)))
          (is (nil? (app.delete/delete! :location *db* location (:user/id user))))
          (is (nil? (location.i/get-by-id *db* id)))
          (is (nil? (observation.i/get-by-id *db* (:observation/id observation))))
          (finally
            (clear-activity! user)))))))

;;; ---------------------------------------------------------------------------
;;; contact

(deftest test-a-contact-with-accessions-is-blocked
  (tf/testing "blockers :contact"
    (accession-fixtures)
    (fn [{:keys [contact]}]
      (is (= [{:reason :accession :count 1}]
             (app.delete/blockers :contact *db* contact))
          "an accession names it as supplier"))))

(deftest test-a-clean-contact-deletes
  (tf/testing "delete! :contact"
    {[::user.i/factory :key/user] {:db *db*}
     [::contact.i/factory :key/contact] {:db *db*}}
    (fn [{:keys [user contact]}]
      (try
        (is (empty? (app.delete/blockers :contact *db* contact)))
        (is (nil? (app.delete/delete! :contact *db* contact (:user/id user))))
        (is (nil? (contact.i/get-by-id *db* (:contact/id contact))))
        (finally
          (clear-activity! user))))))

;;; ---------------------------------------------------------------------------
;;; collection

(deftest test-a-collection-deletes-on-its-own
  (tf/testing "delete! :collection"
    (accession-fixtures)
    (fn [{:keys [user accession]}]
      (let [collection (coll.i/create! *db* {:accession-id (:accession/id accession)
                                             :collector "A. Collector"})]
        (try
          (is (empty? (app.delete/blockers :collection *db* collection)))
          (is (nil? (app.delete/delete! :collection *db* collection (:user/id user))))
          (is (nil? (coll.i/get-by-id *db* (:collection/id collection)))
              "\"this accession is not wild-collected after all\" has an answer")
          (is (some? (accession.i/get-by-id *db* (:accession/id accession)))
              "and the accession it belonged to is untouched")
          (finally
            (clear-activity! user)))))))

;;; ---------------------------------------------------------------------------
;;; labels

(deftest test-every-polymorphic-resource-cascades-its-links
  ;; A note, an observation, a tag link and a media link all hang off a
  ;; polymorphic resource_id with no foreign key behind it, so a delete path
  ;; that forgets one strands rows that leak into the next record to reuse the
  ;; id. The behavioural tests above prove it for the paths they exercise;
  ;; this proves no method was added without it.
  (let [source (slurp "bases/app/src/sepal/app/delete.clj")]
    (doseq [[resource cascades]
            [["accession" [["note" "note.i/delete-for-resource!"]
                           ["tag" "tag.i/delete-for-resource!"]
                           ["media" "media.i/unlink-resource!"]]]
             ["material" [["observation" "observation.i/delete-for-resource!"]
                          ["tag" "tag.i/delete-for-resource!"]
                          ["media" "media.i/unlink-resource!"]]]
             ["taxon" [["note" "note.i/delete-for-resource!"]
                       ["tag" "tag.i/delete-for-resource!"]
                       ["media" "media.i/unlink-resource!"]]]
             ["location" [["observation" "observation.i/delete-for-resource!"]]]]
            [cascade f] cascades]
      (is (re-find (re-pattern (str (java.util.regex.Pattern/quote f)
                                    " tx :" resource))
                   source)
          (format "delete!* :%s must call %s, or its %s links outlive it"
                  resource f cascade)))))

(defn- fts-rows [table id]
  (jdbc.sql/query *db* [(str "select rowid from " table " where rowid = ?") id]))

(deftest test-a-deleted-record-leaves-no-search-hit
  ;; The FTS triggers are not this plan's code and nothing here touches them,
  ;; which is exactly why they are worth pinning once: a stale row shows up as
  ;; a search hit for a record that no longer exists, and nothing else fails.
  ;; taxon's trigger is trigger_taxon_after_delete, not *_fts_delete like the
  ;; other three, so a grep for the naming convention misses it.
  (tf/testing "accession, location and contact FTS rows"
    (accession-fixtures)
    (fn [{:keys [user accession location contact]}]
      (try
        (doseq [[record resource-type table id-key]
                [[accession :accession "accession_fts" :accession/id]
                 [location :location "location_fts" :location/id]
                 [contact :contact "contact_fts" :contact/id]]]
          (let [id (id-key record)]
            (is (seq (fts-rows table id))
                (format "%s starts in %s" resource-type table))
            (app.delete/delete! resource-type *db* record (:user/id user))
            (is (empty? (fts-rows table id))
                (format "a deleted %s must not stay searchable" resource-type))))
        (finally
          (clear-activity! user))))))

(deftest test-a-deleted-taxon-leaves-no-search-hit
  (tf/testing "taxon_fts"
    {[::user.i/factory :key/user] {:db *db*}
     [::taxon.i/factory :key/taxon] {:db *db*}}
    (fn [{:keys [user taxon]}]
      (let [id (:taxon/id taxon)]
        (try
          (taxon.i/update! *db* id {:wfo-taxon-id nil})
          (is (seq (fts-rows "taxon_fts" id)))
          (app.delete/delete! :taxon *db* (taxon.i/get-by-id *db* id) (:user/id user))
          (is (empty? (fts-rows "taxon_fts" id))
              "a deleted taxon must not stay searchable")
          (finally
            (clear-activity! user)))))))

(deftest test-blocker-labels-read-as-sentences
  (is (= "12 material record(s) reference this"
         (app.delete/blocker-label {:reason :material :count 12})))
  (is (= "2 propagation(s) name this as their parent"
         (app.delete/blocker-label {:reason :propagation-parent :count 2})))
  (is (= "1 propagation(s) are running at this location"
         (app.delete/blocker-label {:reason :propagation-location :count 1})))
  (is (= "This name comes from the World Flora Online list"
         (app.delete/blocker-label {:reason :wfo :count 1}))
      "a reason with nothing to count renders without a number"))

(deftest test-deleting-a-hybrid-takes-its-parentage-with-it
  ;; A hybrid's own taxon_parentage rows reference it, so without clearing them
  ;; the foreign key refuses and a hybrid cannot be deleted at all. Synonyms
  ;; are already handled this way.
  (tf/testing "a cross and the rows recording it"
    {[::user.i/factory :key/user] {:db *db* :role :admin}}
    (fn [{:keys [user]}]
      (let [a (taxon.i/create! *db* {:name "Acer rubrum" :rank :species})
            hybrid (taxon.i/create! *db* {:name "Acer × freemanii" :rank :species})]
        (taxon.i/set-parentage! *db* (:taxon/id hybrid)
                                [{:parent-taxon-id (:taxon/id a)}])
        (is (nil? (app.delete/delete! :taxon *db* hybrid (:user/id user)))
            "deleting the hybrid should succeed")
        (is (empty? (taxon.i/list-parentage *db* (:taxon/id hybrid)))
            "and leave no parentage row pointing at a taxon that is gone")
        (jdbc.sql/delete! *db* :activity {:created_by (:user/id user)})))))

(deftest test-a-taxon-named-in-a-cross-says-so-rather-than-failing
  ;; The foreign key would refuse anyway; the point is that the dialog names
  ;; the reason instead of surfacing a constraint error.
  (tf/testing "a parent of a recorded cross"
    {[::user.i/factory :key/user] {:db *db* :role :admin}}
    (fn [{:keys [user]}]
      (let [parent (taxon.i/create! *db* {:name "Cattleya" :rank :genus})
            hybrid (taxon.i/create! *db* {:name "Laeliocattleya" :rank :genus})]
        (taxon.i/set-parentage! *db* (:taxon/id hybrid)
                                [{:parent-taxon-id (:taxon/id parent)}])
        (let [found (app.delete/blockers :taxon *db* parent)]
          (is (= [:parentage] (mapv :reason found)))
          (is (= [1] (mapv :count found)))
          (is (re-find #"cross"
                       (app.delete/blocker-label (first found)))
              "the label should say what a parentage row is"))
        (jdbc.sql/delete! *db* :taxon_parentage {:taxon_id (:taxon/id hybrid)})
        (jdbc.sql/delete! *db* :activity {:created_by (:user/id user)})))))
