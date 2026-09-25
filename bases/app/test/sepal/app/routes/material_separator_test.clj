(ns sepal.app.routes.material-separator-test
  "The garden's material separator, on each screen that shows a full code."
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [integrant.core :as ig]
            [next.jdbc.sql :as jdbc.sql]
            [peridot.core :as peri]
            [sepal.accession.interface :as accession.i]
            [sepal.app.test :as app.test]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.location.interface :as location.i]
            [sepal.material.interface :as material.i]
            [sepal.material.interface.activity :as material.activity]
            [sepal.observation.interface :as observation.i]
            [sepal.propagation.interface :as propagation.i]
            [sepal.settings.interface :as settings.i]
            [sepal.taxon.interface :as taxon.i]
            [sepal.user.interface :as user.i])
  (:import [org.jsoup Jsoup]))

(use-fixtures :once default-system-fixture)

(use-fixtures :each (fn [t] (try (t) (finally (app.test/reset-codes! *db*)))))

(def ^:private password "testpassword123")

(defn- fixtures []
  {[::user.i/factory :key/user] {:db *db* :password password :role :editor}
   [::taxon.i/factory :key/taxon] {:db *db*}
   [::location.i/factory :key/location] {:db *db*}
   [::accession.i/factory :key/acc] {:db *db* :taxon (ig/ref :key/taxon)}
   [::material.i/factory :key/material] {:db *db*
                                         :accession (ig/ref :key/acc)
                                         :location (ig/ref :key/location)
                                         :data {:code "A"}}})

(defn- separator! [separator]
  (settings.i/set-value! *db* "codes.material_separator" separator))

(defn- page-text [sess path]
  (-> (peri/request sess path) :response :body (Jsoup/parse) (.text)))

(deftest test-the-material-screens-show-the-full-code
  (tf/testing "the list, the detail page and the delete dialog"
    (fixtures)
    (fn [{:keys [user acc material]}]
      (let [sess (app.test/login (:user/email user) password)
            acc-code (:accession/code acc)
            id (:material/id material)
            paths [(str "/material/?q=" acc-code)
                   (str "/material/" id "/general/")
                   (str "/material/" id "/delete/")]]
        (testing "with the seeded ."
          (doseq [path paths]
            (is (re-find (re-pattern (str "\\Q" acc-code ".A\\E")) (page-text sess path))
                path)))

        (testing "with no separator"
          (separator! "")
          (doseq [path paths]
            (let [text (page-text sess path)]
              (is (re-find (re-pattern (str "\\Q" acc-code "A\\E")) text) path)
              (is (not (re-find (re-pattern (str "\\Q" acc-code ".A\\E")) text)) path))))))))

(deftest test-observation-search-uses-the-separator
  (tf/testing "a bare word matches the start of the full code as the garden writes it"
    (fixtures)
    (fn [{:keys [user acc material]}]
      (let [observation (observation.i/create! *db* {:resource-type :material
                                                     :resource-id (:material/id material)
                                                     :type "general"
                                                     :observed-on "2026-01-01"
                                                     :created-by (:user/id user)})
            sess (app.test/login (:user/email user) password)
            found? (fn [q]
                     (-> (peri/request sess "/observation/" :params {"q" q})
                         :response :body (Jsoup/parse)
                         (.selectFirst (str "[data-observation-id=" (:observation/id observation) "]"))
                         (some?)))
            acc-code (:accession/code acc)]
        (try
          (separator! "")
          (is (found? (str acc-code "A")))
          (is (not (found? (str acc-code ".A"))))
          (separator! ".")
          (is (found? (str acc-code ".A")))
          (finally
            (observation.i/delete! *db* (:observation/id observation))))))))

(deftest test-the-activity-feed-uses-the-separator
  (tf/testing "a material created event names the full code"
    (fixtures)
    (fn [{:keys [user acc material]}]
      (try
        (material.activity/create! *db* material.activity/created (:user/id user) material)
        (separator! "")
        (let [sess (app.test/login (:user/email user) password)]
          (is (re-find (re-pattern (str "\\Q" (:accession/code acc) "A\\E"))
                       (page-text sess "/activity"))))
        (finally
          (jdbc.sql/delete! *db* :activity {:created_by (:user/id user)}))))))

(deftest test-the-propagation-panel-uses-the-separator
  (tf/testing "a propagation from a named plant is titled with its full code"
    (fixtures)
    (fn [{:keys [user acc material]}]
      (let [prop (propagation.i/create! *db* {:type :cutting
                                              :parent-accession-id (:accession/id acc)
                                              :parent-material-id (:material/id material)})]
        (try
          (separator! "-")
          (let [sess (app.test/login (:user/email user) password)]
            (is (re-find (re-pattern (str "\\Q" (:accession/code acc) "-A\\E"))
                         (page-text sess (str "/propagation/" (:propagation/id prop) "/panel/")))))
          (finally
            (jdbc.sql/delete! *db* :propagation {:id (:propagation/id prop)})))))))

(deftest test-a-letter-template-counts-within-the-accession
  (tf/testing "GET /material/next-code under {letter}"
    (fixtures)
    (fn [{:keys [user acc]}]
      (settings.i/set-value! *db* "codes.material_template" "{letter}")
      (let [sess (app.test/login (:user/email user) password)
            {:keys [response]} (peri/request sess (str "/material/next-code/?accession-id="
                                                       (:accession/id acc)))
            body (Jsoup/parse ^String (:body response))]
        (testing "the accession already holds A"
          (is (= "B" (some-> (.selectFirst body "#code") (.attr "value")))))))))
