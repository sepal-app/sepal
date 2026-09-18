(ns sepal.app.routes.settings.backups-managed-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [peridot.core :as peri]
            [sepal.app.backup.core :as backup]
            [sepal.app.test :as app.test]
            [sepal.app.test.system :refer [*backup-dir* *db* managed-backups-system-fixture]]
            [sepal.test.interface :as test.i]
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

(deftest test-managed-page-refuses-a-hand-rolled-write
  (testing "POST /settings/backups is not a route on a managed garden"
    ;; The form is gone from the page, so there is no anti-forgery token to lift
    ;; from it — sepal.test.core/response-anti-forgery-token reads
    ;; input[name=__anti-forgery-token] out of the body and would throw on nil.
    ;; A caller forging this request would have to get a token from another page,
    ;; which is what this does: the token is per session, not per page.
    (let [sess (admin-session)
          {:keys [response]} (peri/request sess "/settings/profile")
          token (test.i/response-anti-forgery-token response)
          {:keys [response]} (peri/request sess "/settings/backups"
                                           :request-method :post
                                           :params {:__anti-forgery-token token
                                                    :frequency "disabled"})]
      (is (= 404 (:status response)))
      (is (nil? (:frequency (backup/get-config *db* *backup-dir*)))
          "the schedule the sweep depends on is untouched"))))
