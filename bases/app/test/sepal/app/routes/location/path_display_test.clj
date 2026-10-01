(ns sepal.app.routes.location.path-display-test
  "Wherever another record shows a location's name, a nested location reads as
  its path and a top-level one as its name alone."
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [integrant.core :as ig]
            [next.jdbc.sql :as jdbc.sql]
            [peridot.core :as peri]
            [sepal.accession.interface :as accession.i]
            [sepal.app.test :as app.test]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.location.interface :as location.i]
            [sepal.material.interface :as material.i]
            [sepal.media.interface :as media.i]
            [sepal.observation.interface :as observation.i]
            [sepal.propagation.interface :as propagation.i]
            [sepal.taxon.interface :as taxon.i]
            [sepal.user.interface :as user.i])
  (:import [org.jsoup Jsoup]))

(use-fixtures :once default-system-fixture)

(def ^:private password "testpassword123")

(def ^:private row-path "PD orchard › PD row")

(defn- world
  "PD orchard › PD row, and a top-level PD nursery. An accession intended for
  the row, with material that moved from the nursery into it, a propagation
  on the row, and media linked to the row. A function, because *db* is bound
  by the fixture, after this namespace loads."
  []
  {[::user.i/factory :key/user] {:db *db* :password password :role :editor}
   [::taxon.i/factory :key/taxon] {:db *db*}
   [::location.i/factory :key/orchard] {:db *db* :data {:name "PD orchard"}}
   [::location.i/factory :key/row] {:db *db* :parent (ig/ref :key/orchard)
                                    :data {:name "PD row"}}
   [::location.i/factory :key/nursery] {:db *db* :data {:name "PD nursery"}}
   [::accession.i/factory :key/accession] {:db *db*
                                           :taxon (ig/ref :key/taxon)
                                           :intended-location (ig/ref :key/row)}
   [::material.i/factory :key/material] {:db *db*
                                         :accession (ig/ref :key/accession)
                                         :location (ig/ref :key/nursery)
                                         :data {:status :alive :quantity 1}}
   [::propagation.i/factory :key/propagation] {:db *db*
                                               :accession (ig/ref :key/accession)}
   [::media.i/factory :key/media] {:db *db* :user (ig/ref :key/user)}})

(defn- with-world
  "Builds the world, moves the material into the row, puts the propagation on
  it, links the media and records an observation there, then calls `f` with
  the fixtures and a logged-in session."
  [f]
  (tf/testing "a nested location is shown as its path"
    (world)
    (fn [{:keys [user row material propagation media] :as fx}]
      (material.i/update! *db* (:material/id material)
                          {:location-id (:location/id row) :reason "transferred"})
      (propagation.i/update! *db* (:propagation/id propagation)
                             {:location-id (:location/id row)})
      (media.i/link! *db* (:media/id media) (:location/id row) "location")
      (let [observation (observation.i/create! *db* {:resource-type "location"
                                                     :resource-id (:location/id row)
                                                     :type "general"
                                                     :observed-on "2026-01-01"
                                                     :note "PD observation"})]
        (try
          (f fx (app.test/login (:user/email user) password))
          (finally
            (observation.i/delete! *db* (:observation/id observation))
            (media.i/unlink! *db* (:media/id media))
            (jdbc.sql/delete! *db* :activity {:created_by (:user/id user)})))))))

(defn- text-of [sess path]
  (-> sess (peri/request path) :response :body (as-> ^String b (Jsoup/parse b)) (.text)))

(deftest test-material-panel-location
  (with-world
    (fn [{:keys [material]} sess]
      (is (re-find #"PD orchard › PD row"
                   (text-of sess (str "/material/" (:material/id material) "/panel/")))))))

(deftest test-material-history
  (with-world
    (fn [{:keys [material]} sess]
      (is (re-find #"PD nursery → PD orchard › PD row"
                   (text-of sess (str "/material/" (:material/id material) "/panel/")))
          "the top-level one by name, the nested one by path"))))

(deftest test-accession-intended-location
  (with-world
    (fn [{:keys [accession]} sess]
      (is (re-find #"PD orchard › PD row"
                   (text-of sess (str "/accession/" (:accession/id accession) "/panel/")))))))

(deftest test-propagation-panel
  (with-world
    (fn [{:keys [propagation]} sess]
      (is (re-find #"PD orchard › PD row"
                   (text-of sess (str "/propagation/" (:propagation/id propagation) "/panel/")))))))

(deftest test-propagation-list
  (with-world
    (fn [_ sess]
      (is (re-find #"PD orchard › PD row" (text-of sess "/propagation/"))))))

(deftest test-observation-list
  (with-world
    (fn [{:keys [row]} sess]
      (is (re-find (re-pattern (str row-path " \\(" (:location/code row) "\\)"))
                   (text-of sess "/observation/"))))))

(deftest test-media-link-info
  (with-world
    (fn [{:keys [media row]} sess]
      (is (re-find (re-pattern (str row-path " \\(" (:location/code row) "\\)"))
                   (text-of sess (str "/media/" (:media/id media) "/panel/")))))))

(deftest test-location-moved-section
  (with-world
    (fn [{:keys [nursery]} sess]
      (is (re-find #"to PD orchard › PD row"
                   (text-of sess (str "/location/" (:location/id nursery) "/panel/")))))))
