(ns sepal.app.ui.propagations-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is use-fixtures]]
            [integrant.core :as ig]
            [next.jdbc.sql :as jdbc.sql]
            [peridot.core :as peri]
            [sepal.accession.interface :as accession.i]
            [sepal.app.test :as app.test]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.location.interface :as location.i]
            [sepal.material.interface :as material.i]
            [sepal.propagation.interface :as propagation.i]
            [sepal.taxon.interface :as taxon.i]
            [sepal.user.interface :as user.i])
  (:import [org.jsoup Jsoup]))

(use-fixtures :once default-system-fixture)

(defn- fixtures []
  {[::user.i/factory :key/user] {:db *db*
                                 :password "testpassword123"
                                 :role :editor}
   [::taxon.i/factory :key/taxon] {:db *db*}
   [::accession.i/factory :key/acc] {:db *db*
                                     :taxon (ig/ref :key/taxon)}
   [::location.i/factory :key/loc] {:db *db*}
   [::material.i/factory :key/mat] {:db *db*
                                    :accession (ig/ref :key/acc)
                                    :location (ig/ref :key/loc)}})

(defn- page [user path]
  (let [sess (app.test/login (:user/email user) "testpassword123")
        {:keys [response]} (-> sess (peri/request path))]
    (is (= 200 (:status response)))
    (Jsoup/parse ^String (:body response))))

(defn- clean-up! [user ids]
  (doseq [id ids] (jdbc.sql/delete! *db* :propagation {:id id}))
  (jdbc.sql/delete! *db* :activity {:created_by (:user/id user)}))

(deftest test-material-panel-shows-what-was-grown
  (tf/testing "a plant's panel lists the propagations taken from it and where
               the plant itself came from"
    (fixtures)
    (fn [{:keys [user acc mat]}]
      (let [prop (propagation.i/create! *db* {:type :cutting
                                              :parent-accession-id (:accession/id acc)
                                              :parent-material-id (:material/id mat)})
            origin (propagation.i/create! *db* {:type :seed
                                                :parent-accession-id (:accession/id acc)})]
        (material.i/update! *db* (:material/id mat)
                            {:propagation-id (:propagation/id origin)})
        (try
          (let [body (page user (str "/material/" (:material/id mat) "/general/"))]
            (is (str/includes? (.text body) "Cutting")
                "the propagation taken from this plant is listed")
            (is (str/includes? (.text body) "Grown from")
                "and the propagation that produced the plant is named"))
          (finally
            (material.i/update! *db* (:material/id mat) {:propagation-id nil})
            (clean-up! user [(:propagation/id prop) (:propagation/id origin)])))))))

(deftest test-accession-panel-shows-its-propagations
  (tf/testing "an accession's panel lists the batches taken from it without a
               named plant"
    (fixtures)
    (fn [{:keys [user acc]}]
      (let [prop (propagation.i/create! *db* {:type :division
                                              :parent-accession-id (:accession/id acc)})]
        (try
          (let [body (page user (str "/accession/" (:accession/id acc) "/general/"))]
            (is (str/includes? (.text body) "Division"))
            (is (str/includes? (.text body) "In progress")))
          (finally
            (clean-up! user [(:propagation/id prop)])))))))

(deftest test-location-panel-shows-what-is-running
  (tf/testing "a bench's panel lists the active batches on it"
    (fixtures)
    (fn [{:keys [user acc loc]}]
      (let [prop (propagation.i/create! *db* {:type :cutting
                                              :parent-accession-id (:accession/id acc)
                                              :location-id (:location/id loc)})
            done (propagation.i/create! *db* {:type :seed
                                              :parent-accession-id (:accession/id acc)
                                              :location-id (:location/id loc)
                                              :status :complete})]
        (try
          (let [body (page user (str "/location/" (:location/id loc) "/"))]
            (is (str/includes? (.text body) "Cutting")
                "the running batch is on the bench")
            (is (not (str/includes? (.text body) "Seed · Complete"))
                "a closed batch is not"))
          (finally
            (clean-up! user [(:propagation/id prop) (:propagation/id done)])))))))

(deftest test-a-deleted-parent-plant-keeps-the-lineage
  (tf/testing "the propagation survives its parent plant, and the accession
               still names the genotype"
    (fixtures)
    (fn [{:keys [user acc mat]}]
      (let [prop (propagation.i/create! *db* {:type :cutting
                                              :parent-accession-id (:accession/id acc)
                                              :parent-material-id (:material/id mat)})]
        (try
          (is (thrown? Exception
                       (material.i/delete! *db* (:material/id mat)))
              "a plant a propagation came off cannot be deleted")
          (is (= (:propagation/id prop)
                 (:propagation/id (propagation.i/get-by-id *db* (:propagation/id prop))))
              "the lineage is still walkable through the parent accession")
          (finally
            (clean-up! user [(:propagation/id prop)])))))))
