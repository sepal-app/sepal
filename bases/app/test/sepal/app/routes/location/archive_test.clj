(ns sepal.app.routes.location.archive-test
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
            [sepal.taxon.interface :as taxon.i]
            [sepal.test.interface :as test.i]
            [sepal.user.interface :as user.i])
  (:import [org.jsoup Jsoup]))

(use-fixtures :once default-system-fixture)

(def ^:private password "testpassword123")

(defn- post-archive [sess id path]
  (let [{:keys [response] :as sess} (peri/request sess (str "/location/" id "/"))
        token (test.i/response-anti-forgery-token response)]
    (-> sess
        (peri/request (str "/location/" id path)
                      :request-method :post
                      :params {:__anti-forgery-token token})
        :response)))

(deftest test-archiving-takes-a-location-out-of-the-picker-but-not-the-list
  (tf/testing "the picker files new material, so it offers only active
               locations; the list keeps showing archived ones, which is how
               one is found again to restore"
    {[::user.i/factory :key/user] {:db *db* :password password :role :editor}}
    (fn [{:keys [user]}]
      (let [loc (location.i/create! *db* {:code "ARCH1" :name "Retired bed"})
            id (:location/id loc)
            sess (app.test/login (:user/email user) password)
            ;; The rows come back as markup; the id is on each one.
            picker (fn [] (->> (-> sess
                                   (peri/request "/location/"
                                                 :params {"q" "Retired" "options" "1"})
                                   :response :body
                                   (as-> ^String b (Jsoup/parse b))
                                   (.select "[role=option]"))
                               (map #(parse-long (.attr % "data-value")))))]
        (try
          (is (some #(= id %) (picker))
              "offered while active")
          (is (= 303 (:status (post-archive sess id "/archive/"))))
          (is (= :archived (:location/status (location.i/get-by-id *db* id))))
          (is (not (some #(= id %) (picker)))
              "an archived location takes no new material, so it is not offered")
          (is (not (re-find #"Retired bed"
                            (-> sess (peri/request "/location/") :response :body)))
              "and the list leaves it out of the way")
          (is (re-find #"Retired bed"
                       (-> sess
                           (peri/request "/location/" :params {"q" "archived:true"})
                           :response :body))
              "until asked for, which is how one is found again to restore")
          (is (= 303 (:status (post-archive sess id "/unarchive/"))))
          (is (= :active (:location/status (location.i/get-by-id *db* id))))
          (is (some #(= id %) (picker))
              "and restoring puts it back")
          (finally
            ;; Archiving records activity against the user the fixture is
            ;; about to delete, and the foreign key catches that first.
            (jdbc.sql/delete! *db* :activity {:resource_type "location"
                                              :resource_id id})
            (location.i/delete! *db* id)))))))

(deftest test-a-location-holding-material-cannot-be-archived
  (tf/testing "archiving hides where the material is, so it has to be emptied
               first — history is the reason to archive, not a reason not to"
    {[::user.i/factory :key/user] {:db *db* :password password :role :editor}
     [::taxon.i/factory :key/taxon] {:db *db*}
     [::accession.i/factory :key/accession] {:db *db* :taxon (ig/ref :key/taxon)}
     [::location.i/factory :key/location] {:db *db*}
     [::material.i/factory :key/material] {:db *db*
                                           :accession (ig/ref :key/accession)
                                           :location (ig/ref :key/location)}}
    (fn [{:keys [user location material]}]
      (let [id (:location/id location)
            sess (app.test/login (:user/email user) password)]
        (try
          (let [response (post-archive sess id "/archive/")]
            (is (= 422 (:status response)))
            (is (re-find #"cannot be archived yet" (:body response))))
          (is (= :active (:location/status (location.i/get-by-id *db* id))))
          (finally
            (jdbc.sql/delete! *db* :material {:id (:material/id material)})))))))

(deftest test-the-delete-dialog-offers-archive-when-history-is-the-only-blocker
  (tf/testing "a location the move log names can never be deleted, so a dialog
               that only says no leaves the reader nowhere to go"
    {[::user.i/factory :key/user] {:db *db* :password password :role :editor}
     [::taxon.i/factory :key/taxon] {:db *db*}
     [::accession.i/factory :key/accession] {:db *db* :taxon (ig/ref :key/taxon)}
     [::location.i/factory :key/from] {:db *db*}
     [::location.i/factory :key/to] {:db *db*}
     [::material.i/factory :key/material] {:db *db*
                                           :accession (ig/ref :key/accession)
                                           :location (ig/ref :key/from)}}
    (fn [{:keys [user material from to]}]
      ;; Move it away: `from` now holds nothing but is named by the move log.
      (material.i/update! *db* (:material/id material)
                          {:location-id (:location/id to) :reason "transferred"})
      (let [sess (app.test/login (:user/email user) password)
            body (-> sess
                     (peri/request (str "/location/" (:location/id from) "/delete/"))
                     :response :body
                     (as-> ^String s (Jsoup/parse s)))]
        (try
          (is (some? (.selectFirst body (str "a[href=\"/location/"
                                             (:location/id from) "/archive/\"]")))
              "the dialog offers archiving instead")
          (finally
            (jdbc.sql/delete! *db* :material {:id (:material/id material)})))))))
