(ns sepal.app.routes.settings.observation-digest-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [peridot.core :as peri]
            [sepal.app.observation.digest :as digest]
            [sepal.app.test :as app.test]
            [sepal.app.test.system :refer [*db* *mail-client* *system* default-system-fixture]]
            [sepal.scheduler.interface :as scheduler.i]
            [sepal.test.interface :as test.i]
            [sepal.user.interface :as user.i])
  (:import [org.jsoup Jsoup]))

(use-fixtures :once default-system-fixture)

(defn- flash-banner-text [body]
  (some-> (.selectFirst body ".banner-text") (.text)))

(defn- admin-session []
  (let [password "testpassword123"
        email (str "admin-" (random-uuid) "@test.com")]
    (user.i/create! *db* {:email email :password password :role :admin})
    (app.test/login email password)))

(defn- post-digest
  "GET the page for a token, then POST `params` on top of the anti-forgery
  field."
  [params]
  (let [sess (admin-session)
        {:keys [response] :as sess} (peri/request sess "/settings/observation-digest")
        token (test.i/response-anti-forgery-token response)]
    (peri/request sess "/settings/observation-digest"
                  :request-method :post
                  :params (merge {:__anti-forgery-token token} params))))

(defn- clear-config! []
  ;; Tests share one running scheduler across the file, so clearing the
  ;; setting alone leaves a job from an earlier test still registered.
  ;; Rescheduling against the cleared config cancels it too.
  (digest/set-config! *db* {:enabled false :send-at nil :recipient nil})
  (digest/schedule-digest! {:db *db*
                            :mail *mail-client*
                            :scheduler (:sepal.scheduler.interface/scheduler *system*)}))

(defn- scheduled-job-ids []
  (->> (scheduler.i/list-jobs (:sepal.scheduler.interface/scheduler *system*))
       (map :id)
       set))

(deftest test-the-page-renders-with-the-current-values
  (testing "GET /settings/observation-digest shows the saved configuration"
    (clear-config!)
    (digest/set-config! *db* {:enabled true :send-at "06:30" :recipient "curator@test.com"})
    (let [sess (admin-session)
          {:keys [response]} (peri/request sess "/settings/observation-digest")]
      (is (= 200 (:status response)))
      (let [body (Jsoup/parse ^String (:body response))]
        (is (.hasAttr (.selectFirst body "#enabled") "checked")
            "the checkbox reflects enabled")
        (is (= "06:30" (.attr (.selectFirst body "#send_at") "value")))
        (is (= "curator@test.com" (.attr (.selectFirst body "#recipient") "value")))))
    (clear-config!)))

(deftest test-the-page-renders-disabled-by-default
  (testing "with nothing configured, the checkbox comes back unchecked"
    (clear-config!)
    (let [sess (admin-session)
          {:keys [response]} (peri/request sess "/settings/observation-digest")
          body (Jsoup/parse ^String (:body response))]
      (is (not (.hasAttr (.selectFirst body "#enabled") "checked")))))
  (clear-config!))

(deftest test-saving-persists-the-configuration
  (testing "POST /settings/observation-digest stores the three settings"
    (clear-config!)
    (let [{:keys [response] :as sess} (post-digest {:enabled "1"
                                                    :send_at "08:15"
                                                    :recipient "gardener@test.com"})]
      (is (= 303 (:status response)))
      (let [{:keys [response]} (peri/follow-redirect sess)
            body (Jsoup/parse ^String (:body response))]
        (is (= "Observation digest settings updated successfully" (flash-banner-text body))))

      (let [config (digest/get-config *db*)]
        (is (true? (:enabled config)))
        (is (= "08:15" (:send-at config)))
        (is (= "gardener@test.com" (:recipient config))))))
  (clear-config!))

(deftest test-saving-reschedules-the-job
  (testing "enabling with a recipient registers the scheduled job"
    (clear-config!)
    (is (not (contains? (scheduled-job-ids) :observation-digest))
        "nothing scheduled before saving")
    (let [{:keys [response]} (post-digest {:enabled "1"
                                           :send_at "09:00"
                                           :recipient "gardener@test.com"})]
      (is (= 303 (:status response)))
      (is (contains? (scheduled-job-ids) :observation-digest)
          "the job is registered without a restart")))

  (testing "disabling it cancels the job, also without a restart"
    (let [{:keys [response]} (post-digest {:send_at "09:00"
                                           :recipient "gardener@test.com"})]
      (is (= 303 (:status response)))
      (is (not (contains? (scheduled-job-ids) :observation-digest))
          "the job is cancelled without a restart")))
  (clear-config!))

(deftest test-enabling-with-no-recipient-is-rejected
  (testing "the digest cannot be enabled with nowhere to send it"
    (clear-config!)
    (let [{:keys [response]} (post-digest {:enabled "1"
                                           :send_at "07:00"
                                           :recipient ""})]
      (is (= 200 (:status response))
          "the page re-renders rather than redirecting or 500ing")
      (let [body (Jsoup/parse ^String (:body response))]
        (is (re-find #"(?i)recipient"
                     (.text (.selectFirst body "#recipient-errors")))
            "the error names the missing recipient"))
      (is (false? (:enabled (digest/get-config *db*)))
          "the rejected change was not stored")))
  (clear-config!))

(deftest test-page-requires-admin
  (testing "GET /settings/observation-digest returns 403 for non-admin users"
    (let [password "testpassword123"
          email (str "editor-" (random-uuid) "@test.com")]
      (user.i/create! *db* {:email email :password password :role :editor})
      (let [sess (app.test/login email password)
            {:keys [response]} (peri/request sess "/settings/observation-digest")]
        (is (= 403 (:status response)))))))
