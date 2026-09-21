(ns sepal.app.routes.propagation.product-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [integrant.core :as ig]
            [next.jdbc.sql :as jdbc.sql]
            [peridot.core :as peri]
            [sepal.accession.interface :as accession.i]
            [sepal.app.routes.propagation.product :as product]
            [sepal.app.test :as app.test]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.location.interface :as location.i]
            [sepal.material.interface :as material.i]
            [sepal.propagation.interface :as propagation.i]
            [sepal.taxon.interface :as taxon.i]
            [sepal.test.interface :as test.i]
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
   [::location.i/factory :key/loc] {:db *db*}})

(defn- clean-up!
  "Products, then the propagation, then the parent material. Each points at
  the next, and a foreign key refuses the wrong order."
  [user {:keys [materials parent-materials accessions propagations]}]
  (doseq [id materials] (material.i/delete! *db* id))
  (doseq [id accessions] (accession.i/delete! *db* id))
  (doseq [id propagations] (jdbc.sql/delete! *db* :propagation {:id id}))
  (doseq [id parent-materials] (material.i/delete! *db* id))
  (jdbc.sql/delete! *db* :activity {:created_by (:user/id user)}))

(defn- session-and-token [user propagation-id]
  (let [sess (app.test/login (:user/email user) "testpassword123")
        {:keys [response] :as sess} (-> sess
                                        (peri/request (str "/propagation/"
                                                           propagation-id
                                                           "/")))]
    [sess (test.i/response-anti-forgery-token response)]))

(defn- post-product
  "POST the product action. A nil kind posts no kind at all, which is how the
  primary button works."
  [sess token propagation-id kind]
  (:response (peri/request sess
                           (str "/propagation/" propagation-id "/product/")
                           :request-method :post
                           :params (cond-> {:__anti-forgery-token token}
                                     kind (assoc :kind (name kind))))))

(defn- created-id [response kind]
  (let [re (case kind
             :material #"/material/(\d+)/"
             :accession #"/accession/(\d+)/")]
    (some-> (re-find re (get-in response [:headers "HX-Redirect"]))
            second
            parse-long)))

(deftest test-default-follows-the-parent-and-the-method
  (testing "the three-branch rule"
    (doseq [[type clonal expected]
            [[:seed false :material]
             [:vegetative false :material]
             [:tissue false :material]
             [:plant true :material]
             [:plant false :accession]
             [:other true :material]
             [:other nil :accession]]]
      (is (= expected (product/default-kind {:material/type type} clonal))
          (str "parent " type ", clonal " clonal)))))

(deftest test-a-clone-produces-material
  (tf/testing "a cutting's product is new material under the parent accession"
    (fixtures)
    (fn [{:keys [user acc loc]}]
      (let [prop (propagation.i/create! *db* {:type :cutting
                                              :parent-accession-id (:accession/id acc)
                                              :location-id (:location/id loc)})
            [sess token] (session-and-token user (:propagation/id prop))
            response (post-product sess token (:propagation/id prop) nil)
            material-id (created-id response :material)]
        (try
          (is (some? material-id) "the redirect names the new material")
          (let [material (material.i/get-by-id *db* material-id)]
            (is (= (:propagation/id prop) (:material/propagation-id material)))
            (is (= (:accession/id acc) (:material/accession-id material))))
          (finally
            (clean-up! user {:materials [material-id]
                             :propagations [(:propagation/id prop)]})))))))

(deftest test-seed-off-a-plant-produces-an-accession
  (tf/testing "seed taken from a plant is a new genotype"
    (fixtures)
    (fn [{:keys [user acc loc taxon]}]
      (let [parent-material (material.i/create! *db* {:code "1"
                                                      :accession-id (:accession/id acc)
                                                      :location-id (:location/id loc)
                                                      :type :plant
                                                      :status :alive
                                                      :quantity 1})
            prop (propagation.i/create! *db* {:type :seed
                                              :parent-accession-id (:accession/id acc)
                                              :parent-material-id (:material/id parent-material)})
            [sess token] (session-and-token user (:propagation/id prop))
            response (post-product sess token (:propagation/id prop) nil)
            accession-id (created-id response :accession)]
        (try
          (is (some? accession-id) "the default is a new accession")
          (let [product (accession.i/get-by-id *db* accession-id)]
            (is (= (:propagation/id prop) (:accession/propagation-id product)))
            (is (= (:accession/id acc) (:propagation/parent-accession-id prop))
                "the lineage still resolves through the parent accession")
            (is (= (:taxon/id taxon) (:accession/taxon-id product))))
          (finally
            (clean-up! user {:accessions [accession-id]
                             :propagations [(:propagation/id prop)]
                             :parent-materials [(:material/id parent-material)]})))))))

(deftest test-seed-sown-from-a-seed-lot-produces-material
  (tf/testing "the accession was the seed, so growing it on is not a new genotype"
    (fixtures)
    (fn [{:keys [user acc loc]}]
      (let [seed-lot (material.i/create! *db* {:code "1"
                                               :accession-id (:accession/id acc)
                                               :location-id (:location/id loc)
                                               :type :seed
                                               :status :alive
                                               :quantity 40})
            prop (propagation.i/create! *db* {:type :seed
                                              :parent-accession-id (:accession/id acc)
                                              :parent-material-id (:material/id seed-lot)
                                              :location-id (:location/id loc)})
            [sess token] (session-and-token user (:propagation/id prop))
            response (post-product sess token (:propagation/id prop) nil)
            material-id (created-id response :material)]
        (try
          (is (some? material-id) "the default is material, not a new accession")
          (is (= (:accession/id acc)
                 (:material/accession-id (material.i/get-by-id *db* material-id))))
          (finally
            (clean-up! user {:materials [material-id]
                             :propagations [(:propagation/id prop)]
                             :parent-materials [(:material/id seed-lot)]})))))))

(deftest test-reaccessioning-a-clone
  (tf/testing "the secondary action stores and the lineage resolves"
    (fixtures)
    (fn [{:keys [user acc]}]
      (let [prop (propagation.i/create! *db* {:type :cutting
                                              :parent-accession-id (:accession/id acc)})
            [sess token] (session-and-token user (:propagation/id prop))
            response (post-product sess token (:propagation/id prop) :accession)
            accession-id (created-id response :accession)]
        (try
          (is (some? accession-id))
          (is (= (:propagation/id prop)
                 (:accession/propagation-id (accession.i/get-by-id *db* accession-id))))
          (finally
            (clean-up! user {:accessions [accession-id]
                             :propagations [(:propagation/id prop)]})))))))

(deftest test-a-mixed-batch
  (tf/testing "one propagation can produce both kinds"
    (fixtures)
    (fn [{:keys [user acc loc]}]
      (let [prop (propagation.i/create! *db* {:type :cutting
                                              :parent-accession-id (:accession/id acc)
                                              :location-id (:location/id loc)})
            [sess token] (session-and-token user (:propagation/id prop))
            material-id (created-id (post-product sess token (:propagation/id prop) :material)
                                    :material)
            accession-id (created-id (post-product sess token (:propagation/id prop) :accession)
                                     :accession)]
        (try
          (is (= 1 (count (material.i/list-by-propagation-id *db* (:propagation/id prop)))))
          (is (= 1 (count (accession.i/list-by-propagation-id *db* (:propagation/id prop)))))
          (finally
            (clean-up! user {:materials [material-id]
                             :accessions [accession-id]
                             :propagations [(:propagation/id prop)]})))))))

(deftest test-a-barren-propagation-renders
  (tf/testing "a batch that produced nothing is still a record"
    (fixtures)
    (fn [{:keys [user acc]}]
      (let [prop (propagation.i/create! *db* {:type :cutting
                                              :parent-accession-id (:accession/id acc)})
            sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response]} (-> sess
                                   (peri/request (str "/propagation/"
                                                      (:propagation/id prop)
                                                      "/")))
            body (Jsoup/parse ^String (:body response))]
        (try
          (is (= 200 (:status response)))
          (is (str/includes? (.text body) "nothing recorded"))
          (finally
            (clean-up! user {:propagations [(:propagation/id prop)]})))))))
