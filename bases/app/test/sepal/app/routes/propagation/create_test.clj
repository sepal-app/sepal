(ns sepal.app.routes.propagation.create-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [integrant.core :as ig]
            [next.jdbc.sql :as jdbc.sql]
            [peridot.core :as peri]
            [sepal.accession.interface :as accession.i]
            [sepal.app.test :as app.test]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.database.interface :as db.i]
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
   [::accession.i/factory :key/acc-a] {:db *db*
                                       :taxon (ig/ref :key/taxon)}
   [::accession.i/factory :key/acc-b] {:db *db*
                                       :taxon (ig/ref :key/taxon)}
   [::location.i/factory :key/loc] {:db *db*}
   [::material.i/factory :key/mat] {:db *db*
                                    :accession (ig/ref :key/acc-a)
                                    :location (ig/ref :key/loc)
                                    :data {:quantity 3 :status :alive}}
   [::material.i/factory :key/mat-b] {:db *db*
                                      :accession (ig/ref :key/acc-b)
                                      :location (ig/ref :key/loc)}})

(defn- new-form
  "The create page and its token, in a session that keeps the cookies."
  [user]
  (let [sess (app.test/login (:user/email user) "testpassword123")
        {:keys [response] :as sess} (-> sess (peri/request "/propagation/new/"))]
    [sess (test.i/response-anti-forgery-token response)]))

(defn- post!
  "POST the create form. Returns the response."
  [sess token params]
  (:response (peri/request sess "/propagation/new/"
                           :request-method :post
                           :params (merge {:__anti-forgery-token token} params))))

(defn- created-id
  "The id the redirect names, or nil when the response is not a redirect."
  [response]
  (some-> (get-in response [:headers "HX-Redirect"])
          (str/replace #"^.*/propagation/(\d+)/.*$" "$1")
          parse-long))

(defn- clean-up!
  "The propagation rows this test made, and the activity they wrote.

  The activity rows name the user, and the user fixture cannot delete a user
  anything still points at."
  [user ids]
  (when (seq ids)
    (db.i/execute! *db* {:delete-from :propagation
                         :where [:in :id ids]}))
  (jdbc.sql/delete! *db* :activity {:created_by (:user/id user)}))

(deftest test-create-a-propagation
  (tf/testing "a propagation is created from the parent accession"
    (fixtures)
    (fn [{:keys [user acc-a]}]
      (let [[sess token] (new-form user)
            response (post! sess token {:type "cutting"
                                        :parent-accession-id (str (:accession/id acc-a))})
            id (created-id response)]
        (try
          (is (= 200 (:status response)))
          (is (some? id) "the redirect names the new record")
          (is (= :cutting (:propagation/type (propagation.i/get-by-id *db* id))))
          (finally (clean-up! user [id])))))))

(deftest test-prefill-from-the-parent
  (tf/testing "opening the form from a plant fills the parent in and locks it"
    (fixtures)
    (fn [{:keys [user acc-a mat]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            new-page (fn [query]
                       (let [{:keys [response]} (-> sess
                                                    (peri/request (str "/propagation/new/" query)))]
                         (is (= 200 (:status response)))
                         (Jsoup/parse ^String (:body response))))]
        (testing "from a plant"
          (let [body (new-page (str "?parent-material-id=" (:material/id mat)))]
            (is (= (str (:accession/id acc-a))
                   (.attr (.selectFirst body "input[name=parent-accession-id]") "value")))
            (is (str/includes? (.text body) (:accession/code acc-a)))
            (is (= (str (:material/id mat))
                   (.attr (.selectFirst body "sepal-combobox[name=parent-material-id]")
                          "data-value")))
            (is (nil? (.selectFirst body "sepal-combobox[name=parent-accession-id]"))
                "the accession is locked rather than offered")))

        (testing "from the accession"
          (let [body (new-page (str "?parent-accession-id=" (:accession/id acc-a)))]
            (is (nil? (.selectFirst body "sepal-combobox[name=parent-accession-id]")))
            (is (some? (.selectFirst body "sepal-combobox[name=parent-material-id]"))
                "the plant picker offers this accession's material")))))))

(deftest test-parent-material-must-belong-to-the-accession
  (tf/testing "a plant from another accession is refused with a flash, not a 500"
    (fixtures)
    (fn [{:keys [user acc-a mat-b]}]
      (let [[sess token] (new-form user)
            response (post! sess token {:type "cutting"
                                        :parent-accession-id (str (:accession/id acc-a))
                                        :parent-material-id (str (:material/id mat-b))})]
        (is (= 200 (:status response)) "a redirect carrying a flash, not a 500")
        (is (some? (get-in response [:headers "HX-Redirect"])))))))

(deftest test-counts-error-is-a-field-error
  (tf/testing "more succeeded than started is answered on the field"
    (fixtures)
    (fn [{:keys [user acc-a]}]
      (let [[sess token] (new-form user)
            response (post! sess token {:type "seed"
                                        :parent-accession-id (str (:accession/id acc-a))
                                        :quantity-started "40"
                                        :quantity-succeeded "45"})
            body (Jsoup/parse ^String (:body response))]
        (is (= 422 (:status response)))
        (is (re-find #"More succeeded than were started"
                     (.text (.selectFirst body "#quantity-succeeded-errors")))
            "the message names the resolution")))))

(deftest test-parent-quantity-writes-one-change
  (tf/testing "a filled parent quantity appends one change; a blank one none"
    (fixtures)
    (fn [{:keys [user acc-a mat]}]
      (let [[sess token] (new-form user)
            base {:type "division"
                  :parent-accession-id (str (:accession/id acc-a))
                  :parent-material-id (str (:material/id mat))}
            with-quantity (post! sess token (assoc base :parent-quantity "1"))
            without-quantity (post! sess token base)
            ids (keep created-id [with-quantity without-quantity])]
        (try
          (is (= 2 (count ids)) "both posts created a propagation")
          (let [changes (material.i/list-by-material-id *db* (:material/id mat))]
            (is (= 1 (count changes)) "exactly one change row")
            (is (= -2 (:material-change/quantity (first changes)))
                "the signed delta from 3 to 1")
            (is (= "divided" (:material-change/reason (first changes))))
            (is (= 1 (:material/quantity (material.i/get-by-id *db* (:material/id mat))))))
          (finally
            (clean-up! user ids)
            (db.i/execute! *db* {:delete-from :material-change
                                 :where [:= :material_id (:material/id mat)]})))))))

(deftest test-rootstock-is-not-constrained-to-grafts
  (tf/testing "a rootstock on a cutting stores"
    (fixtures)
    (fn [{:keys [user acc-a taxon]}]
      (let [[sess token] (new-form user)
            response (post! sess token {:type "cutting"
                                        :parent-accession-id (str (:accession/id acc-a))
                                        :rootstock-taxon-id (str (:taxon/id taxon))})
            id (created-id response)]
        (try
          (is (some? id))
          (is (= (:taxon/id taxon)
                 (:propagation/rootstock-taxon-id (propagation.i/get-by-id *db* id))))
          (finally (clean-up! user [id])))))))

(deftest test-the-parent-plant-picker-follows-the-accession
  (tf/testing "choosing an accession offers its plants"
    (fixtures)
    (fn [{:keys [user acc-a mat mat-b]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            fragment (fn [q]
                       (Jsoup/parse ^String (:body (:response (peri/request sess (str "/propagation/parent-plant/" q))))))
            body (fragment (str "?parent-accession-id=" (:accession/id acc-a)))]
        (is (some? (.selectFirst body (str "[data-value='" (:material/id mat) "']")))
            "the accession's plant is offered")
        (is (nil? (.selectFirst body (str "[data-value='" (:material/id mat-b) "']")))
            "another accession's is not")
        (is (some? (.selectFirst (fragment "?parent-accession-id=") "#parent-material-field"))
            "a cleared accession still answers with the empty field")))))

(deftest test-the-bare-form-reloads-the-parent-plant
  (tf/testing "the list's New form wires the accession to the plant picker"
    (fixtures)
    (fn [{:keys [user]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            body (Jsoup/parse ^String (:body (:response (peri/request sess "/propagation/new/"))))]
        (is (some? (.selectFirst body "[hx-get='/propagation/parent-plant/'][hx-include='#parent-accession-id']")))
        (is (some? (.selectFirst body "#parent-material-field")))))))
