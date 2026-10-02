(ns sepal.app.routes.panel-timezone-test
  "Every record page shows its panel's times in the garden's zone.

  A page that forgot to pass the timezone showed them in UTC, hours off for
  most gardens and labelled UTC. Each page here loads for a garden in Belize
  with an activity on the record, and its <time> tooltips must say CST."
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [integrant.core :as ig]
            [next.jdbc.sql :as jdbc.sql]
            [peridot.core :as peri]
            [sepal.accession.interface :as accession.i]
            [sepal.app.test :as app.test]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.contact.interface :as contact.i]
            [sepal.contact.interface.activity :as contact.activity]
            [sepal.location.interface :as location.i]
            [sepal.material.interface :as material.i]
            [sepal.material.interface.activity :as material.activity]
            [sepal.settings.interface :as settings.i]
            [sepal.taxon.interface :as taxon.i]
            [sepal.taxon.interface.activity :as taxon.activity]
            [sepal.user.interface :as user.i])
  (:import [org.jsoup Jsoup]))

(use-fixtures :once default-system-fixture)

(defn- time-titles
  "The tooltip of every <time> in the page's panel."
  [response]
  (let [body (Jsoup/parse ^String (:body response))]
    (mapv #(.attr % "title") (.select body "#detail-panel-content time[title], .spl-reader-page time[title]"))))

(defn- assert-garden-times [url response]
  (testing url
    (is (= 200 (:status response)))
    (let [titles (time-titles response)]
      (is (seq titles) "the panel shows the activity's time")
      (is (every? #(re-find #" CST$" %) titles)
          (str "every time is in the garden's zone: " titles)))))

(defn- in-belize [f]
  (settings.i/set-value! *db* "organization.timezone" "America/Belize")
  (try
    (f)
    (finally
      (settings.i/set-value! *db* "organization.timezone" "UTC"))))

(defn- fixtures []
  {[::user.i/factory :key/admin] {:db *db* :password "testpassword123" :role :admin}
   [::user.i/factory :key/reader] {:db *db* :password "testpassword123" :role :reader}
   [::taxon.i/factory :key/taxon] {:db *db*}
   [::contact.i/factory :key/contact] {:db *db*}
   [::location.i/factory :key/location] {:db *db*}
   [::accession.i/factory :key/accession] {:db *db*
                                           :taxon (ig/ref :key/taxon)
                                           :contact (ig/ref :key/contact)}
   [::material.i/factory :key/material] {:db *db*
                                         :accession (ig/ref :key/accession)
                                         :location (ig/ref :key/location)}})

(deftest test-panel-times-are-in-the-gardens-zone
  (tf/testing "record pages pass the garden's timezone to their panel"
    (fixtures)
    (fn [{:keys [admin reader taxon contact material]}]
      (taxon.activity/create! *db* taxon.activity/updated (:user/id admin) taxon)
      (contact.activity/create! *db* contact.activity/updated (:user/id admin) contact)
      (material.activity/create! *db* material.activity/updated (:user/id admin) material)
      (in-belize
        (fn []
          (let [editor (app.test/login (:user/email admin) "testpassword123")
                viewer (app.test/login (:user/email reader) "testpassword123")
                taxon-id (:taxon/id taxon)
                contact-id (:contact/id contact)]
            (doseq [[sess url] [[editor (format "/taxon/%s/name/" taxon-id)]
                                [editor (format "/taxon/%s/media/" taxon-id)]
                                [editor (format "/taxon/%s/synonyms/" taxon-id)]
                                [editor (format "/taxon/%s/tags/" taxon-id)]
                                [viewer (format "/taxon/%s/" taxon-id)]
                                [editor (format "/contact/%s/" contact-id)]
                                [viewer (format "/contact/%s/" contact-id)]
                                [editor (format "/material/%s/media/" (:material/id material))]]]
              (assert-garden-times url (:response (peri/request sess url)))))))
      (jdbc.sql/delete! *db* :activity {:created_by (:user/id admin)}))))
