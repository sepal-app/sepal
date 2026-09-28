(ns sepal.app.routes.setup.taxonomy-test
  "The taxonomy step at the route level: what starts a job, what refuses to,
  and what the progress endpoint returns."
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [peridot.core :as peri]
            [ring.core.protocols :as ring.protocols]
            [sepal.app.instance :as instance]
            [sepal.app.routes.setup.shared :as setup.shared]
            [sepal.app.test :as app.test]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db* *system* default-system-fixture]]
            [sepal.taxon.interface :as taxon.i]
            [sepal.user.interface :as user.i]))

(use-fixtures :once default-system-fixture)

(defn- admin-session
  "A session logged in as a new admin, which the steps after the admin step
  require."
  []
  (let [email (str "admin-" (random-uuid) "@test.com")]
    (user.i/create! *db* {:email email :password "password123" :role :admin})
    (app.test/login email "password123")))

(defn- tracker []
  (get *system* ::instance/setup-job))

(defn- reset-fixture [f]
  ;; The test database has setup complete, which closes the wizard.
  (setup.shared/reset-setup! *db*)
  (reset! (tracker) setup.shared/initial-job-state)
  (f))

(use-fixtures :each reset-fixture)

(defn- stub-import-opts
  "default-import-opts, but with nothing that touches the network. The real ones
  fetch a manifest from GitHub and download 35 MB, which no unit test may do."
  [seen]
  {:fetch-manifest-fn (fn []
                        (swap! seen conj :manifest)
                        {:versions [{:schema_version 1
                                     (keyword "wfo_plant_list.version") "2025-12_2"
                                     :size_mb 35
                                     :sha256 "abc"
                                     :url "https://example.invalid/init.db"}]})
   :download-fn (fn [_url dest {:keys [on-bytes]}]
                  (spit dest "stub")
                  (when on-bytes
                    (on-bytes {:bytes-done 4 :bytes-total 4 :approximate? false}))
                  dest)
   :import-fn (fn [_db _path] 42)})

(defn- wait-for [pred]
  (loop [waited 0]
    (cond
      (pred) true
      (> waited 5000) false
      :else (do (Thread/sleep 10) (recur (+ waited 10))))))

(defn- token [response]
  (some-> (re-find #"__anti-forgery-token\" value=\"([^\"]+)\"" (:body response))
          second))

(deftest test-the-idle-step-offers-the-import
  (testing "with an empty taxon table and no job running, the page is the start
            button and not a progress bar"
    (let [{:keys [response]} (-> (admin-session)
                                 (peri/request "/setup/taxonomy"))]
      (is (= 200 (:status response)))
      (is (str/includes? (:body response) "Import WFO Plant List"))
      (is (not (str/includes? (:body response) "x-setup-progress"))))))

(deftest test-the-post-starts-the-job-and-renders-the-progress-bar
  (testing "the POST returns the page with the bar on it rather than a 303 — a
            redirect would have nothing to report progress into"
    (let [seen (atom [])]
      (with-redefs [setup.shared/default-import-opts (stub-import-opts seen)]
        (let [sess (admin-session)
              {:keys [response] :as sess} (peri/request sess "/setup/taxonomy")
              {:keys [response]} (peri/request sess "/setup/taxonomy"
                                               :request-method :post
                                               :params {:__anti-forgery-token (token response)
                                                        :action "import"})]
          (is (= 200 (:status response)))
          (is (str/includes? (:body response) "x-setup-progress"))
          (is (str/includes? (:body response) "/setup/taxonomy/progress"))
          (is (wait-for #(contains? setup.shared/terminal-phases (:phase @(tracker)))))
          (is (= :done (:phase @(tracker))))
          (is (= [:manifest] @seen)))))))

(deftest test-a-get-during-a-running-job-rejoins-rather-than-restarts
  (testing "a reload mid-download must not show the start button again"
    (reset! (tracker) {:phase :downloading-taxa :bytes-done 5 :bytes-total 10})
    (let [{:keys [response]} (-> (admin-session)
                                 (peri/request "/setup/taxonomy"))]
      (is (= 200 (:status response)))
      (is (str/includes? (:body response) "x-setup-progress"))
      (is (not (str/includes? (:body response) "Import WFO Plant List")))
      ;; The initial frame is inlined so the bar is right before the first SSE
      ;; message lands.
      (is (str/includes? (:body response) "downloading-taxa")))))

(deftest test-a-post-with-taxa-already-present-starts-nothing
  (testing "can-import-wfo? is what stops a second 127 MB download into a
            garden that already has taxa"
    (tf/testing "a garden with a taxon"
      {[::taxon.i/factory :key/taxon] {:db *db*}}
      (fn [_]
        (let [seen (atom [])]
          (with-redefs [setup.shared/default-import-opts (stub-import-opts seen)]
            ;; The token comes from the regional step because the
            ;; taxa-already-exist view renders no form and so carries none, and
            ;; a request with no token is refused by CSRF before it ever reaches
            ;; the handler this test is about.
            (let [sess (admin-session)
                  {:keys [response] :as sess} (peri/request sess "/setup/regional")
                  {:keys [response]} (peri/request sess "/setup/taxonomy"
                                                   :request-method :post
                                                   :params {:__anti-forgery-token (token response)
                                                            :action "import"})]
              (is (= 303 (:status response)))
              (is (str/includes? (get-in response [:headers "Location"]) "/setup/review"))
              (is (= :idle (:phase @(tracker))))
              (is (empty? @seen)))))))))

(deftest test-the-progress-route-returns-promptly-on-an-idle-install
  ;; The each-fixture leaves the tracker idle, which is the state of an install
  ;; whose wizard has not reached the import. If this body does not return,
  ;; every open progress request pins a thread, and 200 of them exhaust the
  ;; Jetty pool.
  (let [{:keys [response]} (-> (admin-session)
                               (peri/request "/setup/taxonomy/progress"))
        out (java.io.ByteArrayOutputStream.)
        worker (future
                 (ring.protocols/write-body-to-stream (:body response) nil out)
                 :returned)]
    (is (= 200 (:status response)))
    (is (= :returned (deref worker 2000 ::timed-out))
        "the idle stream never ended, so a GET pins a thread")
    (is (str/includes? (.toString out "UTF-8") "\"phase\":\"idle\""))))

(deftest test-the-progress-route-streams-server-sent-events
  (reset! (tracker) {:phase :done :taxa-count 42 :wfo-version "2025-12_2"})
  (let [{:keys [response]} (-> (admin-session)
                               (peri/request "/setup/taxonomy/progress"))]
    (is (= 200 (:status response)))
    (is (= "text/event-stream" (get-in response [:headers "Content-Type"])))
    (is (= "no-cache" (get-in response [:headers "Cache-Control"])))
    ;; nginx buffers proxied responses by default, which would undo the
    ;; per-frame flush.
    (is (= "no" (get-in response [:headers "X-Accel-Buffering"])))
    (testing "and the body a real client would read terminates"
      (let [out (java.io.ByteArrayOutputStream.)
            worker (future
                     (ring.protocols/write-body-to-stream (:body response) nil out)
                     :returned)]
        (is (= :returned (deref worker 2000 ::timed-out)))
        (is (str/includes? (.toString out "UTF-8") "\"taxaCount\":42"))))))
