(ns sepal.app.routes.material.bulk-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [integrant.core :as ig]
            [next.jdbc.sql :as jdbc.sql]
            [peridot.core :as peri]
            [sepal.accession.interface :as accession.i]
            [sepal.activity.interface :as activity.i]
            [sepal.app.test :as app.test]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.location.interface :as location.i]
            [sepal.material.interface :as material.i]
            [sepal.material.interface.activity :as material.activity]
            [sepal.observation.interface :as observation.i]
            [sepal.taxon.interface :as taxon.i]
            [sepal.test.interface :as test.i]
            [sepal.user.interface :as user.i]))

(use-fixtures :once default-system-fixture)

(defn- post [user path params]
  (let [sess (app.test/login (:user/email user) "testpassword123")
        {:keys [response] :as sess} (peri/request sess "/settings/profile")
        token (test.i/response-anti-forgery-token response)]
    (:response (peri/request sess path
                             :request-method :post
                             :headers {"x-csrf-token" token "hx-request" "true"}
                             :params params))))

(defn- two-materials []
  {[::user.i/factory :key/user] {:db *db* :password "testpassword123" :role :editor}
   [::taxon.i/factory :key/taxon] {:db *db*}
   [::accession.i/factory :key/accession] {:db *db* :taxon (ig/ref :key/taxon)}
   [::location.i/factory :key/location] {:db *db*}
   [::material.i/factory :key/one] {:db *db* :accession (ig/ref :key/accession)
                                    :location (ig/ref :key/location)
                                    :data {:status :alive :quantity 1}}
   [::material.i/factory :key/four] {:db *db* :accession (ig/ref :key/accession)
                                     :location (ig/ref :key/location)
                                     :data {:status :alive :quantity 4}}})

(defn- cleanup! [user]
  (jdbc.sql/delete! *db* :activity {:created_by (:user/id user)}))

(deftest test-bulk-death
  (tf/testing "marking two materials dead zeroes them and records each death"
    (two-materials)
    (fn [{:keys [user one four]}]
      (let [ids [(:material/id one) (:material/id four)]
            response (post user "/material/bulk/status/"
                           {:ids (mapv str ids) :status "dead" :reason "dead"})]
        (is (= 200 (:status response)))
        (is (= "bulk-applied" (get-in response [:headers "HX-Trigger"])))
        (doseq [id ids]
          (is (= {:material/status :dead :material/quantity 0}
                 (select-keys (material.i/get-by-id *db* id) [:material/status :material/quantity]))))
        (is (= [[-1 "dead"]]
               (map (juxt :material-change/quantity :material-change/reason)
                    (material.i/list-by-material-id *db* (:material/id one)))))
        (is (= [[-4 "dead"]]
               (map (juxt :material-change/quantity :material-change/reason)
                    (material.i/list-by-material-id *db* (:material/id four)))))
        (is (= 2 (count (filter #(= material.activity/updated (:activity/type %))
                                (mapcat #(activity.i/get-by-resource *db* :resource-type :material :resource-id %)
                                        ids)))))
        (cleanup! user)))))

(deftest test-already-in-status-is-skipped
  (tf/testing "a material already at the status gets no write and no event"
    (assoc-in (two-materials) [[::material.i/factory :key/one] :data] {:status :dead :quantity 0})
    (fn [{:keys [user one four]}]
      (let [response (post user "/material/bulk/status/"
                           {:ids [(str (:material/id one)) (str (:material/id four))]
                            :status "dead" :reason "dead"})]
        (is (= 200 (:status response)))
        (is (re-find #"1 already had that status" (:body response)))
        (is (empty? (activity.i/get-by-resource *db* :resource-type :material
                                                :resource-id (:material/id one))))
        (cleanup! user)))))

(deftest test-living-status-writes-no-history
  (tf/testing "moving to dormant changes only the status"
    (two-materials)
    (fn [{:keys [user four]}]
      (post user "/material/bulk/status/" {:ids [(str (:material/id four))] :status "dormant"})
      (is (= {:material/status :dormant :material/quantity 4}
             (select-keys (material.i/get-by-id *db* (:material/id four))
                          [:material/status :material/quantity])))
      (is (empty? (material.i/list-by-material-id *db* (:material/id four))))
      (cleanup! user))))

(deftest test-non-living-needs-a-reason
  (tf/testing "dead with no reason is refused and nothing changes"
    (two-materials)
    (fn [{:keys [user four]}]
      (let [response (post user "/material/bulk/status/"
                           {:ids [(str (:material/id four))] :status "dead" :reason ""})]
        (is (= 422 (:status response)))
        (is (re-find #"reason-bulk-status-errors" (:body response)))
        (is (= :alive (:material/status (material.i/get-by-id *db* (:material/id four)))))
        (cleanup! user)))))

(deftest test-a-missing-id-writes-nothing
  (tf/testing "one deleted id fails the whole action"
    (two-materials)
    (fn [{:keys [user four]}]
      (let [response (post user "/material/bulk/status/"
                           {:ids [(str (:material/id four)) "999999"] :status "dormant"})]
        (is (= 422 (:status response)))
        (is (re-find #"no longer exist" (:body response)))
        (is (= :alive (:material/status (material.i/get-by-id *db* (:material/id four)))))
        (cleanup! user)))))

(deftest test-empty-selection-is-refused
  (tf/testing "no ids is a 422 with a sentence"
    (two-materials)
    (fn [{:keys [user]}]
      (let [response (post user "/material/bulk/status/" {:status "dormant"})]
        (is (= 422 (:status response)))
        (is (re-find #"Select at least one row" (:body response)))))))

(deftest test-bulk-move
  (tf/testing "moving writes one change per moved row and skips rows already there"
    (assoc (two-materials) [::location.i/factory :key/bed] {:db *db*})
    (fn [{:keys [user one four bed location]}]
      (material.i/update! *db* (:material/id one) {:location-id (:location/id bed)})
      (let [before (count (material.i/list-by-material-id *db* (:material/id one)))
            response (post user "/material/bulk/move/"
                           {:ids [(str (:material/id one)) (str (:material/id four))]
                            :location-id (str (:location/id bed))})]
        (is (= 200 (:status response)))
        (is (re-find #"1 was already there" (:body response)))
        (is (= before (count (material.i/list-by-material-id *db* (:material/id one))))
            "no new change for the row already in the bed")
        (is (= [[(:location/id location) (:location/id bed) 0]]
               (map (juxt :material-change/from-location-id :material-change/to-location-id
                          :material-change/quantity)
                    (material.i/list-by-material-id *db* (:material/id four)))))
        ;; The change rows hold both locations; delete the materials first.
        (material.i/delete! *db* (:material/id one))
        (material.i/delete! *db* (:material/id four))
        (cleanup! user)))))

(deftest test-move-to-a-missing-location
  (tf/testing "an unknown location is refused and nothing moves"
    (two-materials)
    (fn [{:keys [user four location]}]
      (let [response (post user "/material/bulk/move/"
                           {:ids [(str (:material/id four))] :location-id "999999"})]
        (is (= 422 (:status response)))
        (is (re-find #"Choose a location" (:body response)))
        (is (= (:location/id location)
               (:material/location-id (material.i/get-by-id *db* (:material/id four)))))))))

(def ^:private observation-form
  "What the dialog submits: every field, the optional ones blank."
  {:type "general" :value "" :observed_on "2026-10-01"
   :observed_by "" :next_check_on "" :note ""})

(deftest test-bulk-observation
  (tf/testing "one observation per material"
    (two-materials)
    (fn [{:keys [user one four]}]
      (let [ids [(:material/id one) (:material/id four)]
            response (post user "/material/bulk/observation/"
                           (assoc observation-form :ids (mapv str ids)))]
        (is (= 200 (:status response)))
        (doseq [id ids]
          (is (= 1 (count (observation.i/get-for-resource *db* :material id))))
          (observation.i/delete-for-resource! *db* :material id))
        (cleanup! user)))))

(deftest test-bulk-observation-in-the-future
  (tf/testing "a future date is refused for the whole action"
    (two-materials)
    (fn [{:keys [user four]}]
      (let [response (post user "/material/bulk/observation/"
                           (assoc observation-form :ids [(str (:material/id four))]
                                  :observed_on "2999-01-01"))]
        (is (= 422 (:status response)))
        (is (re-find #"observed_on-bulk-observation-errors" (:body response)))
        (is (empty? (observation.i/get-for-resource *db* :material (:material/id four))))))))

(deftest test-status-is-all-or-nothing
  (tf/testing "an event write that fails on the second material changes neither"
    (two-materials)
    (fn [{:keys [user one four]}]
      (let [ids [(:material/id one) (:material/id four)]
            calls (atom 0)
            create! material.activity/create!
            response (with-redefs [material.activity/create!
                                   (fn [& args]
                                     (if (= 2 (swap! calls inc))
                                       (throw (ex-info "write failed" {}))
                                       (apply create! args)))]
                       (post user "/material/bulk/status/"
                             {:ids (mapv str ids) :status "dead" :reason "dead"}))]
        (is (= 422 (:status response)))
        (is (re-find #"innerHTML:\.spl-bulk-error[^>]*>Nothing was changed" (:body response))
            "the message shows in the dialog")
        (doseq [id ids]
          (is (= {:material/status :alive}
                 (select-keys (material.i/get-by-id *db* id) [:material/status])))
          (is (empty? (material.i/list-by-material-id *db* id)))
          (is (empty? (activity.i/get-by-resource *db* :resource-type :material :resource-id id))))
        (cleanup! user)))))
