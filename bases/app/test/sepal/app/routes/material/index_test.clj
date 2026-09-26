(ns sepal.app.routes.material.index-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [integrant.core :as ig]
            [peridot.core :as peri]
            [sepal.accession.interface :as accession.i]
            [sepal.app.test :as app.test]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.location.interface :as location.i]
            [sepal.material.interface :as material.i]
            [sepal.taxon.interface :as taxon.i]
            [sepal.user.interface :as user.i])
  (:import [org.jsoup Jsoup]))

(use-fixtures :once default-system-fixture)

(def ^:private password "testpassword123")

;; A function, not a top-level def: *db* is bound by the fixture at run time.
(defn- fixtures []
  {[::user.i/factory :key/user] {:db *db* :password password :role :editor}
   [::taxon.i/factory :key/taxon] {:db *db*}
   [::location.i/factory :key/location] {:db *db*}
   [::accession.i/factory :key/accession] {:db *db* :taxon (ig/ref :key/taxon)}
   [::material.i/factory :key/material] {:db *db*
                                         :accession (ig/ref :key/accession)
                                         :location (ig/ref :key/location)
                                         :data {:status :dormant :quantity 1}}})

(defn- fetch [sess url & {:as params}]
  (let [{:keys [response]} (if (seq params)
                             (peri/request sess url :params params)
                             (peri/request sess url))]
    (Jsoup/parse ^String (:body response))))

(defn- living-checkbox [body]
  (.selectFirst body "label[x-data^=termFilter]"))

(deftest test-the-living-checkbox-carries-the-alive-term
  (tf/testing "a living-only checkbox on the page, checked when the query has it"
    (fixtures)
    (fn [{:keys [user]}]
      (let [sess (app.test/login (:user/email user) password)
            unchecked (living-checkbox (fetch sess "/material/"))
            checked (living-checkbox (fetch sess "/material/" "q" "status:alive"))]
        (is (.contains (.attr unchecked "x-data") "'status:alive', false)"))
        (is (.contains (.attr checked "x-data") "'status:alive', true)"))
        (is (.contains (.text unchecked) "Only living material"))))))

(deftest test-the-list-shows-and-filters-by-status
  (tf/testing "a dormant material shows its status, and status:dormant finds it"
    (fixtures)
    (fn [{:keys [user material]}]
      (let [sess (app.test/login (:user/email user) password)
            row (str "tr:has(a[href*=/material/" (:material/id material) "/])")]
        (is (.contains (.text (.selectFirst (fetch sess "/material/" "q" "status:dormant") row))
                       "Dormant"))
        (is (nil? (.selectFirst (fetch sess "/material/" "q" "status:alive") row)))))))
