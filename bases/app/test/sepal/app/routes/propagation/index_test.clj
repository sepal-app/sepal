(ns sepal.app.routes.propagation.index-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [integrant.core :as ig]
            [next.jdbc.sql :as jdbc.sql]
            [peridot.core :as peri]
            [sepal.accession.interface :as accession.i]
            [sepal.app.test :as app.test]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.location.interface :as location.i]
            [sepal.propagation.interface :as propagation.i]
            [sepal.taxon.interface :as taxon.i]
            [sepal.user.interface :as user.i])
  (:import [java.net URLEncoder]
           [java.nio.charset StandardCharsets]
           [org.jsoup Jsoup]))

(use-fixtures :once default-system-fixture)

(defn- fixtures
  "A function, not a def: *db* is bound by the fixture at run time, so reading
  it at namespace load captures nil."
  []
  {[::user.i/factory :key/user] {:db *db*
                                 :password "testpassword123"
                                 :role :editor}
   [::taxon.i/factory :key/taxon] {:db *db*}
   [::accession.i/factory :key/acc-a] {:db *db*
                                       :taxon (ig/ref :key/taxon)}
   [::accession.i/factory :key/acc-b] {:db *db*
                                       :taxon (ig/ref :key/taxon)}
   [::location.i/factory :key/loc-a] {:db *db*}
   [::location.i/factory :key/loc-b] {:db *db*}})

(defn- list-page [user q]
  (let [sess (app.test/login (:user/email user) "testpassword123")
        path (cond-> "/propagation/"
               (seq q) (str "?q=" (URLEncoder/encode q StandardCharsets/UTF_8)))]
    (Jsoup/parse ^String (:body (:response (-> sess (peri/request path)))))))

(defn- parent-cells
  "The parent link of each data row: the wide form, which is the accessible
  one. The collapsed form below 640px repeats it as plain text."
  [body]
  (mapv #(.text %) (.select body "tbody td.spl-col--identifier a")))

(deftest test-list-renders-and-filters
  (tf/testing "the list is the nursery list: active by default, and every
               filter narrows it"
    (fixtures)
    (fn [{:keys [user acc-a acc-b loc-a]}]
      (let [db *db*
            p1 (propagation.i/create!
                 db {:type :cutting
                     :parent-accession-id (:accession/id acc-a)
                     :location-id (:location/id loc-a)
                     :propagated-on "2026-03-01"})
            p2 (propagation.i/create!
                 db {:type :seed
                     :parent-accession-id (:accession/id acc-b)
                     :status :complete
                     :propagated-on "2026-05-01"})
            p3 (propagation.i/create!
                 db {:type :division
                     :parent-accession-id (:accession/id acc-b)
                     :location-id (:location/id loc-a)
                     :propagated-on "2026-04-15"})]
        (try
          (let [code-a (:accession/code acc-a)
                code-b (:accession/code acc-b)]
            (testing "renders the list"
              (let [body (list-page user nil)]
                (is (some? (.selectFirst body "table.spl-table")))
                (is (some? (.selectFirst body ".spl-table-card")))))

            (testing "the default query is the nursery list"
              (let [rows (parent-cells (list-page user nil))]
                (is (= 2 (count rows)) "the completed batch is out of the way")
                (is (some #(= code-a %) rows))
                (is (some #(= code-b %) rows))))

            (testing "a status filter overrides the default"
              (is (= 1 (count (parent-cells (list-page user "status:complete"))))))

            (testing "status:* clears the default"
              (is (= 3 (count (parent-cells (list-page user "status:*"))))))

            (testing "a type filter narrows inside the default"
              (is (= 1 (count (parent-cells (list-page user "type:cutting"))))))

            (testing "a location filter narrows to one bench"
              (is (= 2 (count (parent-cells
                                (list-page user (str "location.id:"
                                                     (:location/id loc-a))))))))

            (testing "a date range narrows"
              (is (= 1 (count (parent-cells (list-page user "propagated:>2026-04-01"))))
                  "only the complete batch is after the cutoff and it is not active")
              (is (= 2 (count (parent-cells
                                (list-page user "status:* propagated:>2026-04-01")))))))
          (finally
            (doseq [p [p1 p2 p3]]
              (jdbc.sql/delete! db :propagation {:id (:propagation/id p)}))))))))
