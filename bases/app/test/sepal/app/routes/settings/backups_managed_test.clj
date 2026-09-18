(ns sepal.app.routes.settings.backups-managed-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [peridot.core :as peri]
            [sepal.app.backup.core :as backup]
            [sepal.app.test :as app.test]
            [sepal.app.test.system :refer [*backup-dir* *db* managed-backups-system-fixture]]
            [sepal.user.interface :as user.i])
  (:import [org.jsoup Jsoup]))

(use-fixtures :once managed-backups-system-fixture)

(defn- admin-session []
  (let [password "testpassword123"
        email (str "admin-" (random-uuid) "@test.com")]
    (user.i/create! *db* {:email email :password password :role :admin})
    (app.test/login email password)))

(deftest test-managed-page-offers-nothing-to-configure
  (testing "GET /settings/backups renders no schedule control and no media warning"
    (let [sess (admin-session)
          {:keys [response]} (peri/request sess "/settings/backups")
          body (Jsoup/parse ^String (:body response))]
      (is (= 200 (:status response)))
      (is (nil? (.selectFirst body "select[name=frequency]"))
          "a managed garden has no frequency to choose")
      (is (nil? (.selectFirst body "form[action*=/settings/backups]"))
          "and nothing to submit; scoped to this route because the layout has forms of its own")
      (is (not (.contains (:body response) "Media files are stored separately"))
          "the manual-media warning is false here: this garden's media is not on disk")
      (is (not (.contains (:body response) "Next backup"))))))

(deftest test-managed-page-still-lists-and-links-backups
  (testing "GET /settings/backups lists the zips that exist, with a download link"
    (let [result (backup/create-backup! *db* *backup-dir*)]
      (try
        (let [sess (admin-session)
              {:keys [response]} (peri/request sess "/settings/backups")
              body (Jsoup/parse ^String (:body response))]
          (is (.contains (:body response) (:filename result)))
          (is (some? (.selectFirst body (str "a[href*=" (:filename result) "]")))
              "the download link is the point of the page"))
        (finally
          (.delete (java.io.File. ^String *backup-dir* ^String (:filename result))))))))
