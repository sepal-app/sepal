(ns sepal.app.routes.setup.shared-test
  (:require [babashka.fs :as fs]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [ring.adapter.jetty :as jetty]
            [ring.core.protocols :as ring.protocols]
            [sepal.app.routes.setup.shared :as setup.shared]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.mail.interface.protocols :as mail.p]))

(use-fixtures :once default-system-fixture)

(defn- stub-mail []
  (reify mail.p/MailClient
    (send-message [_ _] {:status :sent})))

(deftest test-checks-read-the-context-not-the-environment
  (testing "a hosted process serves many gardens from one environment, so a
            check that reads System/getenv gives every garden the same wrong
            answer"
    (let [checks (setup.shared/check-server-config
                   {:db *db*
                    :mail (stub-mail)
                    :s3-client :a-client
                    :media-upload-bucket "garden-media"
                    :app-domain "garden.example.org"})]
      (is (= :ok (get-in checks [:smtp :status])))
      (is (= :ok (get-in checks [:s3 :status])))
      (is (= :ok (get-in checks [:app-domain :status]))))))

(deftest test-checks-warn-on-what-the-context-lacks
  (testing "an instance with no mail client, no bucket and no domain says so"
    (let [checks (setup.shared/check-server-config {:db *db*})]
      (is (= :warning (get-in checks [:smtp :status])))
      (is (= :warning (get-in checks [:s3 :status])))
      (is (= :warning (get-in checks [:app-domain :status]))))))

(deftest test-s3-needs-both-a-client-and-a-bucket
  (testing "a client with no bucket cannot store anything"
    (let [checks (setup.shared/check-server-config {:db *db* :s3-client :a-client})]
      (is (= :warning (get-in checks [:s3 :status]))))))

;; -----------------------------------------------------------------------------
;; The taxonomy import job
;;
;; Everything below drives run-import! with stubs. The real thing downloads
;; 35 MB and 127 MB from GitHub, so a test that used the real collaborators
;; would be a network test that took minutes; the seam is the only reason this
;; is testable at all.

(def ^:private taxa-url "https://example.invalid/sepal-init.db")
(def ^:private syn-url "https://example.invalid/sepal-synonyms.db")

(defn- manifest
  "A manifest in the shape the release publishes: a :versions vector whose
  entries carry a :schema_version. `synonyms` omitted means the key is absent,
  which is what the published sepal-init-v1 entry looks like."
  [& {:keys [synonyms]}]
  {:schema_version 1
   :versions [(cond-> {:schema_version 1
                       (keyword "wfo_plant_list.version") "2025-12_2"
                       :size_mb 35
                       :sha256 "0000000000000000000000000000000000000000000000000000000000000000"
                       :url taxa-url}
                synonyms (assoc :synonyms synonyms))]})

(def ^:private synonyms-entry
  {:url syn-url
   :sha256 "1111111111111111111111111111111111111111111111111111111111111111"
   :size_mb 127})

(defn- writing-download
  "A download-fn that writes to the destination and reports progress the way
  download-file! does. Records the URLs it was asked for in `seen`."
  [seen]
  (fn [url dest {:keys [on-bytes]}]
    (swap! seen conj url)
    (spit dest (str "stub for " url))
    (when on-bytes
      (on-bytes {:bytes-done 10 :bytes-total 10 :approximate? false}))
    dest))

(defn- recording-tracker
  "A tracker plus a vector of every phase it passed through."
  []
  (let [phases (atom [])
        tracker (atom setup.shared/initial-job-state)]
    (add-watch tracker :phases
               (fn [_ _ old new]
                 (when (not= (:phase old) (:phase new))
                   (swap! phases conj (:phase new)))))
    [tracker phases]))

(defn- wait-for
  "Poll `pred` until it is true or the deadline passes. Returns pred's value."
  [pred]
  (loop [waited 0]
    (cond
      (pred) true
      (> waited 5000) false
      :else (do (Thread/sleep 10) (recur (+ waited 10))))))

(defn- with-ref-dest
  "Run (f ref-dest) with a reference-file destination inside a temp dir."
  [f]
  (let [dir (fs/create-temp-dir {:prefix "sepal-ref-dest"})]
    (try
      (f (str (fs/path dir "sepal-synonyms.db")))
      (finally (fs/delete-tree dir)))))

(deftest test-the-import-walks-every-phase-in-order
  (testing "a stubbed download and import produce the whole sequence, not just
            a final state — a job that jumped straight to :done would show a bar
            that never moves"
    (with-ref-dest
      (fn [ref-dest]
        (let [[tracker phases] (recording-tracker)
              seen (atom [])]
          (setup.shared/run-import!
            *db* tracker
            {:fetch-manifest-fn (constantly (manifest :synonyms synonyms-entry))
             :download-fn (writing-download seen)
             :import-fn (constantly 453210)
             :ref-dest ref-dest})
          (is (= [:fetching-manifest
                  :downloading-taxa
                  :importing-taxa
                  :downloading-synonyms
                  :done]
                 @phases))
          (is (= [taxa-url syn-url] @seen))
          (is (= 453210 (:taxa-count @tracker)))
          (is (= "2025-12_2" (:wfo-version @tracker)))
          (is (true? (:synonyms? @tracker)))
          (is (nil? (:warning @tracker))))))))

(deftest test-the-reference-file-arrives-by-atomic-rename
  (testing "the pool opens the file with immutable=1, so overwriting it in place
            is undefined behaviour: the download lands beside the destination
            and only then takes its name"
    (with-ref-dest
      (fn [ref-dest]
        (let [[tracker _] (recording-tracker)
              seen (atom [])]
          (setup.shared/run-import!
            *db* tracker
            {:fetch-manifest-fn (constantly (manifest :synonyms synonyms-entry))
             :download-fn (writing-download seen)
             :import-fn (constantly 1)
             :ref-dest ref-dest})
          (is (= :done (:phase @tracker)))
          (is (fs/exists? ref-dest))
          (is (= (str "stub for " syn-url) (slurp ref-dest)))
          (is (not (fs/exists? (str ref-dest ".part")))
              "the partial download was left behind"))))))

(deftest test-a-manifest-with-no-synonyms-key-is-not-an-error
  (testing "`synonyms` is absent — not null — on the published sepal-init-v1
            entry, which means no reference is available yet"
    (with-ref-dest
      (fn [ref-dest]
        (let [[tracker phases] (recording-tracker)
              seen (atom [])]
          (setup.shared/run-import!
            *db* tracker
            {:fetch-manifest-fn (constantly (manifest))
             :download-fn (writing-download seen)
             :import-fn (constantly 7)
             :ref-dest ref-dest})
          (is (= :done (:phase @tracker)))
          (is (false? (:synonyms? @tracker)))
          (is (nil? (:error @tracker)))
          (is (not-any? #{:downloading-synonyms} @phases))
          (is (= [taxa-url] @seen))
          (is (not (fs/exists? ref-dest))))))))

(deftest test-a-failed-reference-download-still-reaches-done
  (testing "the taxa are already imported by then, and synonymy is an
            enhancement — a managed garden that starts without the plant list
            never gets one, but it can get synonyms later"
    (with-ref-dest
      (fn [ref-dest]
        (let [[tracker _] (recording-tracker)]
          (setup.shared/run-import!
            *db* tracker
            {:fetch-manifest-fn (constantly (manifest :synonyms synonyms-entry))
             :download-fn (fn [url dest {:keys [on-bytes]}]
                            (if (= url syn-url)
                              (throw (ex-info "503 from the release host" {}))
                              (do (spit dest "taxa")
                                  (when on-bytes (on-bytes {:bytes-done 4 :bytes-total 4}))
                                  dest)))
             :import-fn (constantly 12)
             :ref-dest ref-dest})
          (is (= :done (:phase @tracker)))
          (is (= 12 (:taxa-count @tracker)))
          (is (string? (:warning @tracker)))
          (is (str/includes? (str (:warning @tracker)) "503 from the release host"))
          (is (nil? (:error @tracker))))))))

(deftest test-a-failed-taxonomy-download-fails-the-whole-import
  (testing "taxonomy is not optional, so the wizard has to offer a retry rather
            than move on"
    (with-ref-dest
      (fn [ref-dest]
        (let [[tracker _] (recording-tracker)]
          (setup.shared/run-import!
            *db* tracker
            {:fetch-manifest-fn (constantly (manifest :synonyms synonyms-entry))
             :download-fn (fn [_url _dest _opts]
                            (throw (ex-info "the plant list download died" {})))
             :import-fn (constantly 12)
             :ref-dest ref-dest})
          (is (= :failed (:phase @tracker)))
          (is (= "the plant list download died" (:error @tracker)))
          (is (nil? (:taxa-count @tracker))))))))

(deftest test-a-reference-entry-with-no-checksum-is-refused
  (testing "a truncated 127 MB SQLite file is a working database with rows
            missing, so an unverifiable reference would degrade silently"
    (with-ref-dest
      (fn [ref-dest]
        (let [[tracker _] (recording-tracker)
              seen (atom [])]
          (setup.shared/run-import!
            *db* tracker
            {:fetch-manifest-fn (constantly (manifest :synonyms (dissoc synonyms-entry :sha256)))
             :download-fn (writing-download seen)
             :import-fn (constantly 3)
             :ref-dest ref-dest})
        ;; :done, because the taxa imported; the reference is a warning.
          (is (= :done (:phase @tracker)))
          (is (str/includes? (str (:warning @tracker)) "no checksum"))
          (is (= [taxa-url] @seen)
              "the unverifiable reference was downloaded anyway")
          (is (not (fs/exists? ref-dest))))))))

(deftest test-a-run-that-throws-outside-a-download-still-lands-in-the-tracker
  (testing "run-import! is called from a future nobody joins: an uncaught
            exception would vanish and leave the bar frozen in a running phase"
    (with-ref-dest
      (fn [ref-dest]
        (let [[tracker _] (recording-tracker)]
          (setup.shared/run-import!
            *db* tracker
            {:fetch-manifest-fn (fn [] (throw (java.net.ConnectException. "refused")))
             :download-fn (writing-download (atom []))
             :import-fn (constantly 1)
             :ref-dest ref-dest})
          (is (= :failed (:phase @tracker)))
          (is (str/includes? (:error @tracker) "Could not connect to GitHub")))))))

(deftest test-an-incompatible-manifest-fails-rather-than-imports-nothing
  (with-ref-dest
    (fn [ref-dest]
      (let [[tracker _] (recording-tracker)
            seen (atom [])]
        (setup.shared/run-import!
          *db* tracker
          {:fetch-manifest-fn (constantly {:versions [{:schema_version 99 :url taxa-url}]})
           :download-fn (writing-download seen)
           :import-fn (constantly 1)
           :ref-dest ref-dest})
        (is (= :failed (:phase @tracker)))
        (is (str/includes? (:error @tracker) "No compatible WFO Plant List version"))
        (is (empty? @seen))))))

;; -----------------------------------------------------------------------------
;; Byte counting

(deftest test-copy-counting-reports-every-byte
  (testing "io/copy cannot report progress, which is the only reason
            copy-counting! exists"
    (let [content (byte-array 200000 (byte 7))
          in (java.io.ByteArrayInputStream. content)
          out (java.io.ByteArrayOutputStream.)
          calls (atom [])
          total (setup.shared/copy-counting! in out #(swap! calls conj %))]
      (is (= 200000 total))
      (is (= 200000 (last @calls)))
      (is (> (count @calls) 1)
          "200 KB through a 64 KiB buffer must report more than once, or the
           callback is only firing at the end and the bar would jump 0→100")
      (is (= 200000 (.size out)))
      (is (apply < @calls) "the counts must be monotonically increasing"))))

(deftest test-copy-counting-handles-an-empty-stream
  (let [out (java.io.ByteArrayOutputStream.)
        calls (atom [])]
    (is (zero? (setup.shared/copy-counting!
                 (java.io.ByteArrayInputStream. (byte-array 0))
                 out
                 #(swap! calls conj %))))
    (is (empty? @calls))))

;; -----------------------------------------------------------------------------
;; The percentage denominator
;;
;; These run a real Jetty because the two cases differ only in whether the
;; server sent a Content-Length, which is a property of the HTTP response and
;; not of anything we could stub.

(defn- with-server
  "Run (f base-url) against a live Jetty serving `handler` on an ephemeral port."
  [handler f]
  (let [server (jetty/run-jetty handler {:port 0 :join? false})]
    (try
      (f (str "http://localhost:" (.getPort (.getURI server))))
      (finally (.stop server)))))

(deftest test-content-length-is-the-exact-denominator
  (testing "the header is exact, so :approximate? is false and the manifest's
            rounded-up size_mb is not used"
    (let [payload (byte-array 4096 (byte 3))]
      (with-server (fn [_] {:status 200
                            :headers {"Content-Type" "application/octet-stream"}
                            :body payload})
        (fn [base]
          (let [dest (str (fs/create-temp-file {:prefix "sepal-dl"}))
                reports (atom [])]
            (setup.shared/download-file! base dest
                                         {:size-mb 99
                                          :on-bytes #(swap! reports conj %)})
            (is (= 4096 (fs/size dest)))
            (is (= 4096 (:bytes-total (last @reports))))
            (is (false? (:approximate? (last @reports))))
            (is (= 4096 (:bytes-done (last @reports))))
            (fs/delete-if-exists dest)))))))

(deftest test-a-missing-content-length-falls-back-to-the-manifest-size
  (testing "size_mb comes from `du -m` rounded up, so a total derived from it is
            approximate and the UI has to say so"
    (let [payload (byte-array 4096 (byte 3))]
      (with-server (fn [_] {:status 200
                            :headers {"Content-Type" "application/octet-stream"
                                      ;; Chunked: Jetty sends no Content-Length
                                      ;; for a stream of unknown length.
                                      "Transfer-Encoding" "chunked"}
                            :body (java.io.ByteArrayInputStream. payload)})
        (fn [base]
          (let [dest (str (fs/create-temp-file {:prefix "sepal-dl"}))
                reports (atom [])]
            (setup.shared/download-file! base dest
                                         {:size-mb 2
                                          :on-bytes #(swap! reports conj %)})
            (is (= 4096 (fs/size dest)))
            (is (= (* 2 1024 1024) (:bytes-total (last @reports))))
            (is (true? (:approximate? (last @reports))))
            (fs/delete-if-exists dest)))))))

(deftest test-a-checksum-mismatch-deletes-the-file-and-throws
  (with-server (fn [_] {:status 200
                        :headers {"Content-Type" "application/octet-stream"}
                        :body (byte-array 16 (byte 1))})
    (fn [base]
      (let [dest (str (fs/create-temp-file {:prefix "sepal-dl"}))]
        (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Checksum verification failed"
                              (setup.shared/download-file! base dest {:sha256 "deadbeef"})))
        (is (not (fs/exists? dest)))))))

;; -----------------------------------------------------------------------------
;; Starting it once

(deftest test-a-second-start-does-not-start-a-second-download
  (testing "a double form submit, or an impatient reload, must not start two
            127 MB downloads"
    (with-ref-dest
      (fn [ref-dest]
        (let [tracker (atom setup.shared/initial-job-state)
              gate (promise)
              calls (atom 0)
              opts {:fetch-manifest-fn (fn []
                                         (swap! calls inc)
                                         @gate
                                         (manifest))
                    :download-fn (writing-download (atom []))
                    :import-fn (constantly 5)
                    :ref-dest ref-dest}]
          (is (= :started (setup.shared/start-import! *db* tracker opts)))
        ;; Hold the job inside fetch-manifest-fn so the second start lands while
        ;; it is genuinely in flight.
          (is (wait-for #(pos? @calls)))
          (is (= :already-running (setup.shared/start-import! *db* tracker opts)))
          (deliver gate true)
          (is (wait-for #(contains? setup.shared/terminal-phases (:phase @tracker))))
          (is (= :done (:phase @tracker)))
          (is (= 1 @calls)
              "the collaborator ran twice, so two jobs were started"))))))

(deftest test-a-failed-run-can-be-retried
  (testing "the wizard shows a retry button, so a terminal :failed has to be
            startable again"
    (with-ref-dest
      (fn [ref-dest]
        (let [tracker (atom {:phase :failed :error "earlier boom"})
              opts {:fetch-manifest-fn (constantly (manifest))
                    :download-fn (writing-download (atom []))
                    :import-fn (constantly 5)
                    :ref-dest ref-dest}]
          (is (= :started (setup.shared/start-import! *db* tracker opts)))
          (is (wait-for #(contains? setup.shared/terminal-phases (:phase @tracker))))
          (is (= :done (:phase @tracker)))
          (is (nil? (:error @tracker))
              "the stale error survived the restart"))))))

;; -----------------------------------------------------------------------------
;; The progress stream

(defn- frames
  "The payloads of every `data:` frame in an SSE stream."
  [s]
  (->> (str/split-lines s)
       (keep #(when (str/starts-with? % "data: ") (subs % 6)))))

(deftest test-the-stream-stops-on-a-terminal-phase
  (testing "without the terminal check the connection lives for the life of the
            process"
    (let [tracker (atom {:phase :done :taxa-count 4})
          out (java.io.ByteArrayOutputStream.)
          worker (future
                   (ring.protocols/write-body-to-stream
                     (setup.shared/sse-body tracker :poll-ms 10) nil out)
                   :returned)]
      (is (= :returned (deref worker 2000 ::timed-out))
          "the body never returned, so the loop is not checking for a terminal phase")
      (let [payloads (frames (.toString out "UTF-8"))]
        (is (= 1 (count payloads)))
        (is (str/includes? (first payloads) "\"phase\":\"done\""))))))

(deftest test-the-stream-stops-on-a-failed-phase
  (let [tracker (atom {:phase :failed :error "nope"})
        out (java.io.ByteArrayOutputStream.)
        worker (future
                 (ring.protocols/write-body-to-stream
                   (setup.shared/sse-body tracker :poll-ms 10) nil out)
                 :returned)]
    (is (= :returned (deref worker 2000 ::timed-out)))
    (is (= 1 (count (frames (.toString out "UTF-8")))))))

(deftest test-the-stream-keeps-emitting-while-the-job-runs
  (testing "one frame and out would be the dead-button behaviour again"
    (let [tracker (atom {:phase :downloading-taxa :bytes-done 1 :bytes-total 100})
          out (java.io.ByteArrayOutputStream.)
          worker (future
                   (ring.protocols/write-body-to-stream
                     (setup.shared/sse-body tracker :poll-ms 10) nil out)
                   :returned)]
      (Thread/sleep 100)
      (swap! tracker merge {:phase :done})
      (is (= :returned (deref worker 2000 ::timed-out)))
      (let [payloads (frames (.toString out "UTF-8"))]
        (is (>= (count payloads) 2))
        (is (some #(str/includes? % "\"phase\":\"downloading-taxa\"") payloads))
        (is (str/includes? (last payloads) "\"phase\":\"done\""))))))

(deftest test-each-frame-is-flushed-rather-than-buffered
  (testing "io/writer buffers 8 KiB and the encoder behind it another 8 KiB, so
            without the .flush the browser sees nothing until the stream closes
            — which is exactly the synchronous behaviour this replaces.

            ByteArrayOutputStream/size counts only the bytes that actually
            reached the stream, so a non-zero size mid-run is the flush."
    (let [tracker (atom {:phase :downloading-taxa :bytes-done 1 :bytes-total 100})
          out (java.io.ByteArrayOutputStream.)
          worker (future
                   (ring.protocols/write-body-to-stream
                     (setup.shared/sse-body tracker :poll-ms 10) nil out)
                   :returned)]
      (Thread/sleep 200)
      (let [mid-run (.size out)]
        (swap! tracker merge {:phase :done})
        (is (= :returned (deref worker 2000 ::timed-out)))
        (is (pos? mid-run)
            (str "after 200 ms of a still-running job only " mid-run
                 " bytes had reached the output stream, so the frames are being buffered"))
        ;; Well under one writer buffer, so a buffered run would still be at 0.
        (is (< mid-run 8192))))))

(deftest test-jetty-delivers-frames-as-they-are-written
  (testing "an end-to-end check that nothing between the writer and the socket
            re-buffers the stream. With the flush the client reads a frame
            within a tick; without it, it reads nothing until a full writer
            buffer has accumulated, which at ~180 bytes a frame is more than
            forty of them."
    (let [tracker (atom {:phase :downloading-taxa :bytes-done 1 :bytes-total 100})
          handler (fn [_] {:status 200
                           :headers {"Content-Type" "text/event-stream"}
                           :body (setup.shared/sse-body tracker :poll-ms 50)})]
      (with-server handler
        (fn [base]
          (with-open [in (io/reader (.getInputStream (.openConnection (java.net.URL. base))))]
            (let [first-line (.readLine in)]
              (is (str/includes? first-line "\"phase\":\"downloading-taxa\""))
              ;; The job is still running: the read above returned from a flush,
              ;; not from the stream closing.
              (swap! tracker merge {:phase :done})
              (let [rest-of-it (doall (line-seq in))
                    payloads (frames (str/join "\n" (cons first-line rest-of-it)))]
                (is (str/includes? (last payloads) "\"phase\":\"done\""))
                (is (< (count payloads) 10)
                    (str (count payloads) " frames arrived after the first read, so"
                         " the stream had been accumulating them instead of"
                         " flushing each one"))))))))))

(deftest test-the-frame-is-json-a-browser-can-use
  (testing "camelCase keys and no Clojure keywords: `approximate?` would arrive
            as a key JavaScript cannot reach with dot notation"
    (let [frame (setup.shared/job-frame {:phase :downloading-synonyms
                                         :bytes-done 25
                                         :bytes-total 100
                                         :approximate? true
                                         :wfo-version "2025-12_2"
                                         :taxa-count 453210
                                         :synonyms? true})]
      (is (= "downloading-synonyms" (get frame "phase")))
      (is (= 25 (get frame "percent")))
      (is (true? (get frame "approximate")))
      (is (= 453210 (get frame "taxaCount")))
      (is (not-any? keyword? (keys frame)))
      (is (not-any? keyword? (vals frame))))))

(deftest test-a-phase-with-no-byte-counts-has-no-percentage
  (testing "the import is one INSERT…SELECT of ~453k rows and is deliberately
            indeterminate"
    (is (nil? (get (setup.shared/job-frame {:phase :importing-taxa}) "percent")))
    (is (nil? (get (setup.shared/job-frame {:phase :fetching-manifest}) "percent")))))
