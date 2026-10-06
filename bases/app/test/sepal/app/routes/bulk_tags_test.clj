(ns sepal.app.routes.bulk-tags-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [integrant.core :as ig]
            [next.jdbc.sql :as jdbc.sql]
            [peridot.core :as peri]
            [sepal.accession.interface :as accession.i]
            [sepal.activity.interface :as activity.i]
            [sepal.app.test :as app.test]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.tag.interface :as tag.i]
            [sepal.tag.interface.activity :as tag.activity]
            [sepal.taxon.interface :as taxon.i]
            [sepal.test.interface :as test.i]
            [sepal.user.interface :as user.i]))

(use-fixtures :once default-system-fixture)

(defn- session [user]
  (let [sess (app.test/login (:user/email user) "testpassword123")
        {:keys [response] :as sess} (peri/request sess "/settings/profile")]
    [sess (test.i/response-anti-forgery-token response)]))

(defn- send! [[sess token] method path params]
  (:response (peri/request sess path :request-method method
                           :headers {"x-csrf-token" token "hx-request" "true"}
                           :params params)))

(defn- three-accessions []
  {[::user.i/factory :key/user] {:db *db* :password "testpassword123" :role :editor}
   [::taxon.i/factory :key/taxon] {:db *db*}
   [::accession.i/factory :key/a] {:db *db* :taxon (ig/ref :key/taxon)}
   [::accession.i/factory :key/b] {:db *db* :taxon (ig/ref :key/taxon)}
   [::accession.i/factory :key/c] {:db *db* :taxon (ig/ref :key/taxon)}})

(defn- linked-events [ids]
  (filter #(= tag.activity/linked (:activity/type %))
          (mapcat #(activity.i/get-by-resource *db* :resource-type :accession :resource-id %) ids)))

(deftest test-add-creates-one-tag-and-links-each
  (tf/testing "a new name creates one tag; an already-linked row is skipped"
    (three-accessions)
    (fn [{:keys [user a b c]}]
      (let [s (session user)
            ids (map :accession/id [a b c])
            _ (send! s :post "/accession/bulk/tags/" {:ids [(str (:accession/id a))] :tag-name "Label run"})
            response (send! s :post "/accession/bulk/tags/" {:ids (map str ids) :tag-name "label run"})
            tag (tag.i/get-by-name *db* "Label run")]
        (is (= 200 (:status response)))
        (is (re-find #"1 already had it" (:body response)))
        (is (= 1 (count (filter #(= "Label run" (:tag/name %)) (tag.i/list-all *db*)))))
        (is (= 3 (count (linked-events ids))) "one event per link, none for the repeat")
        (doseq [id ids] (tag.i/untag! *db* (:tag/id tag) id :accession))
        (jdbc.sql/delete! *db* :activity {:created_by (:user/id user)})
        (tag.i/delete! *db* (:tag/id tag))))))

(deftest test-remove-form-offers-only-carried-tags
  (tf/testing "the Remove dialog lists the union of the selected rows' tags"
    (three-accessions)
    (fn [{:keys [user a b c]}]
      (let [t1 (tag.i/create! *db* {:name "On A"})
            t2 (tag.i/create! *db* {:name "On B"})
            t3 (tag.i/create! *db* {:name "On C"})]
        (tag.i/tag! *db* (:tag/id t1) (:accession/id a) :accession)
        (tag.i/tag! *db* (:tag/id t2) (:accession/id b) :accession)
        (tag.i/tag! *db* (:tag/id t3) (:accession/id c) :accession)
        (let [[sess] (session user)
              {:keys [response]} (peri/request sess "/accession/bulk/tags/remove/"
                                               :params {:ids [(str (:accession/id a)) (str (:accession/id b))]})]
          (is (= 200 (:status response)))
          (is (re-find #"On A" (:body response)))
          (is (re-find #"On B" (:body response)))
          (is (not (re-find #"On C" (:body response)))))
        (doseq [t [t1 t2 t3]] (tag.i/delete! *db* (:tag/id t)))))))

(deftest test-remove-unlinks
  (tf/testing "removing unlinks each row that has it and counts the rest"
    (three-accessions)
    (fn [{:keys [user a b]}]
      (let [t (tag.i/create! *db* {:name "Printed"})
            s (session user)]
        (tag.i/tag! *db* (:tag/id t) (:accession/id a) :accession)
        (let [response (send! s :post "/accession/bulk/tags/remove/"
                              {:ids [(str (:accession/id a)) (str (:accession/id b))]
                               :tag-id (str (:tag/id t))})]
          (is (= 200 (:status response)))
          (is (re-find #"1 didn't have it" (:body response)))
          (is (empty? (tag.i/get-for-resources *db* :accession [(:accession/id a)]))))
        (jdbc.sql/delete! *db* :activity {:created_by (:user/id user)})
        (tag.i/delete! *db* (:tag/id t))))))

(deftest test-material-and-taxon-routes-exist
  (tf/testing "the routes answer on the other two lists"
    (three-accessions)
    (fn [{:keys [user taxon]}]
      (let [s (session user)
            response (send! s :post "/taxon/bulk/tags/" {:ids [(str (:taxon/id taxon))] :tag-name "Taxon run"})
            tag (tag.i/get-by-name *db* "Taxon run")]
        (is (= 200 (:status response)))
        (is (= 422 (:status (send! s :post "/material/bulk/tags/" {:ids ["999999"] :tag-name "x"})))
            "a material id that doesn't exist")
        (tag.i/untag! *db* (:tag/id tag) (:taxon/id taxon) :taxon)
        (jdbc.sql/delete! *db* :activity {:created_by (:user/id user)})
        (tag.i/delete! *db* (:tag/id tag))))))

(deftest test-remove-form-refuses-no-selection
  (tf/testing "the Remove dialog's body with no ids is a 422 with a sentence"
    (three-accessions)
    (fn [{:keys [user]}]
      (let [response (send! (session user) :get "/accession/bulk/tags/remove/" {})]
        (is (= 422 (:status response)))
        (is (re-find #"Select at least one row" (:body response)))))))
