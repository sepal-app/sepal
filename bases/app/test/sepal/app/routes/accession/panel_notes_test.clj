(ns sepal.app.routes.accession.panel-notes-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [integrant.core :as ig]
            [peridot.core :as peri]
            [sepal.accession.interface :as accession.i]
            [sepal.app.routes.accession.panel :as accession.panel]
            [sepal.app.test :as app.test]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.contact.interface :as contact.i]
            [sepal.database.interface :as db.i]
            [sepal.note.interface :as note.i]
            [sepal.taxon.interface :as taxon.i]
            [sepal.user.interface :as user.i])
  (:import [org.jsoup Jsoup]))

(use-fixtures :once default-system-fixture)

;; note.i's readers gate on the schema version, so a direct call needs a
;; context. Requests get theirs from ::z/context.
(def ^:private ctx {:schema-version (db.i/latest-version)})

(deftest test-fetch-panel-data-carries-notes
  (tf/testing "fetch-panel-data"
    {[::user.i/factory :key/user] {:db *db*}
     [::contact.i/factory :key/contact] {:db *db*}
     [::taxon.i/factory :key/taxon] {:db *db*}
     [::accession.i/factory :key/accession] {:db *db*
                                             :taxon (ig/ref :key/taxon)
                                             :contact (ig/ref :key/contact)}}
    (fn [{:keys [user accession]}]
      (let [made (mapv (fn [n]
                         (note.i/create! *db* {:body (str "note " n)
                                               :resource-type :accession
                                               :resource-id (:accession/id accession)
                                               :created-by (:user/id user)}))
                       (range 5))
            data (accession.panel/fetch-panel-data ctx *db* accession)]
        (is (= 5 (:note-count data)))
        (testing "the panel holds a preview, not the whole history"
          (is (= 3 (count (:notes data))))
          (is (= ["note 4" "note 3" "note 2"] (mapv :note/body (:notes data)))))
        (doseq [n made] (note.i/delete! *db* (:note/id n)))))))

(deftest test-a-reader-sees-notes-on-the-panel-page
  (tf/testing "GET /accession/:id/ as a reader"
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :reader}
     [::user.i/factory :key/author] {:db *db*}
     [::contact.i/factory :key/contact] {:db *db*}
     [::taxon.i/factory :key/taxon] {:db *db*}
     [::accession.i/factory :key/accession] {:db *db*
                                             :taxon (ig/ref :key/taxon)
                                             :contact (ig/ref :key/contact)}}
    (fn [{:keys [user author accession]}]
      ;; The tab is behind the edit permission, so this page is the only place a
      ;; reader can ever see a note. That is what makes this test the one that
      ;; matters for the 1,643 imported notes.
      (let [note (note.i/create! *db* {:body "imported from Bauble"
                                       :resource-type :accession
                                       :resource-id (:accession/id accession)
                                       :created-by (:user/id author)})
            sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response]} (peri/request sess (str "/accession/" (:accession/id accession) "/"))
            body (Jsoup/parse ^String (:body response))]
        (is (= 200 (:status response)))
        (is (re-find #"imported from Bauble" (.text body))
            "A reader's panel page shows the note")
        (note.i/delete! *db* (:note/id note))))))
