(ns sepal.app.routes.setup.shared-test
  (:require [babashka.fs :as fs]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [next.jdbc :as jdbc]
            [ring.adapter.jetty :as jetty]
            [ring.core.protocols :as ring.protocols]
            [sepal.app.routes.setup.shared :as setup.shared]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.mail.interface.protocols :as mail.p]
            [sepal.settings.interface :as settings.i]))

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

;; -----------------------------------------------------------------------------
;; The synchronous entry point
;;
;; import-wfo-taxonomy! is called from outside this repo:
;; cloud/bases/dispatcher/src/sepal/cloud/dispatcher/main.clj's
;; import-plant-list!, which depends on app/projects/app by :local/root. There is
;; nothing in app/ that would fail if it disappeared, which is exactly why it
;; needs a test here — the coupling is invisible from inside this repo.

(defn- sha256-of [path]
  (let [digest (java.security.MessageDigest/getInstance "SHA-256")
        buffer (byte-array 8192)]
    (with-open [in (io/input-stream path)]
      (loop []
        (let [n (.read in buffer)]
          (when (pos? n)
            (.update digest buffer 0 n)
            (recur)))))
    (format "%064x" (BigInteger. 1 (.digest digest)))))

(defn- build-init-db!
  "A stand-in for the published init database: the one table
  import-from-init-db! reads, with two rows in it."
  [path]
  (let [ds (jdbc/get-datasource {:dbtype "sqlite" :dbname path})]
    (jdbc/execute! ds ["create table taxon (id integer primary key,
                                            wfo_taxon_id text,
                                            name text not null,
                                            author text,
                                            rank text not null,
                                            parent_id integer)"])
    (jdbc/execute! ds ["insert into taxon (id, wfo_taxon_id, name, author, rank, parent_id)
                        values (9000001, 'wfo-9000001', 'Testus', 'L.', 'genus', null),
                               (9000002, 'wfo-9000002', 'Testus fictus', 'L.', 'species', 9000001)"])
    path))

(defn- clean-up-imported-taxa! []
  (jdbc/execute! *db* ["delete from taxon where id in (9000001, 9000002)"])
  (settings.i/delete! *db* "setup.wfo_plant_list_version"))

(defn- assert-nothing-imported-yet!
  "Establish the precondition both entry-point tests assert against, and check
  it. The fixture database is shared by the whole namespace and the run-import!
  tests above write setup.wfo_plant_list_version through the same code path, so
  \"the setting was not written\" only means anything from a known-clean start.
  Leaving that implicit is how an absent-result assertion stops being able to
  fail."
  []
  (clean-up-imported-taxa!)
  (is (nil? (settings.i/get-value *db* "setup.wfo_plant_list_version")))
  (is (setup.shared/can-import-wfo? *db*)))

(deftest test-the-synchronous-entry-point-imports-end-to-end
  (testing "called the way cloud/'s dispatcher calls it: one connection, the
            manifest fetched over HTTP and parsed by fetch-manifest, a real init
            database downloaded and checksummed, a real ATTACH and INSERT.
            manifest-url is the only thing redefined, and only because the
            release host is not reachable from a test."
    (let [dir (fs/create-temp-dir {:prefix "sepal-init-db"})
          init-db (build-init-db! (str (fs/path dir "sepal-init.db")))
          digest (sha256-of init-db)
          ;; Set once the server is up, so the manifest it serves can point back
          ;; at the ephemeral port the download will come from.
          base-url (atom nil)
          handler (fn [{:keys [uri]}]
                    (if (= uri "/sepal-init-manifest.json")
                      {:status 200
                       :headers {"Content-Type" "application/json"}
                       :body (json/write-str
                               {:versions [{:schema_version 1
                                            "wfo_plant_list.version" "2025-12_2"
                                            :size_mb 1
                                            :sha256 digest
                                            :url (str @base-url "/sepal-init.db")}]})}
                      {:status 200
                       :headers {"Content-Type" "application/octet-stream"}
                       :body (io/file init-db)}))]
      (try
        (assert-nothing-imported-yet!)
        (with-server handler
          (fn [base]
            (reset! base-url base)
            (with-redefs [setup.shared/manifest-url (str base "/sepal-init-manifest.json")]
              ;; A single connection, not the pool: import-from-init-db! runs
              ;; ATTACH and the INSERT as separate statements and ATTACH is
              ;; per-connection. This is the shape the dispatcher passes.
              (with-open [conn (jdbc/get-connection *db*)]
                (let [result (setup.shared/import-wfo-taxonomy! conn)]
                  (is (nil? (:error result)))
                  (is (true? (:ok result)))
                  (is (= "2025-12_2" (:wfo-version result)))
                  (is (= 2 (:taxa-count result)))
                  (is (str/includes? (str (:message result)) "2025-12_2"))
                  (is (= "2025-12_2"
                         (settings.i/get-value *db* "setup.wfo_plant_list_version"))
                      "the version setting is what tells the dispatcher's template it is seeded")
                  (is (= 2 (count (jdbc/execute!
                                    *db* ["select id from taxon where id in (9000001, 9000002)"])))))))))
        (finally
          (clean-up-imported-taxa!)
          (fs/delete-tree dir))))))

(deftest test-the-synchronous-entry-point-verifies-the-manifest-checksum
  (testing "a truncated init database is a working database with taxa missing,
            and the dispatcher would bake it into every garden. This pins that
            the manifest's sha256 actually reaches the downloader."
    (let [dir (fs/create-temp-dir {:prefix "sepal-init-db"})
          init-db (build-init-db! (str (fs/path dir "sepal-init.db")))
          base-url (atom nil)
          handler (fn [{:keys [uri]}]
                    (if (= uri "/sepal-init-manifest.json")
                      {:status 200
                       :headers {"Content-Type" "application/json"}
                       :body (json/write-str
                               {:versions [{:schema_version 1
                                            "wfo_plant_list.version" "2025-12_2"
                                            :size_mb 1
                                            :sha256 (str/join (repeat 64 "f"))
                                            :url (str @base-url "/sepal-init.db")}]})}
                      {:status 200
                       :headers {"Content-Type" "application/octet-stream"}
                       :body (io/file init-db)}))]
      (try
        (assert-nothing-imported-yet!)
        (with-server handler
          (fn [base]
            (reset! base-url base)
            (with-redefs [setup.shared/manifest-url (str base "/sepal-init-manifest.json")]
              (with-open [conn (jdbc/get-connection *db*)]
                (let [result (setup.shared/import-wfo-taxonomy! conn)]
                  (is (nil? (:ok result)))
                  (is (str/includes? (str (:error result)) "Checksum verification failed"))
                  (is (empty? (jdbc/execute!
                                *db* ["select id from taxon where id in (9000001, 9000002)"]))
                      "taxa from an unverified download were imported anyway")
                  (is (nil? (settings.i/get-value *db* "setup.wfo_plant_list_version"))))))))
        (finally
          (clean-up-imported-taxa!)
          (fs/delete-tree dir))))))

(deftest test-the-synchronous-entry-point-reports-failure-without-throwing
  (testing "the dispatcher converts {:error ...} into a throw itself, so this
            must not throw: a template with no taxa must not be moved into place"
    (with-redefs [setup.shared/fetch-manifest
                  (fn [] (throw (java.net.ConnectException. "refused")))]
      (with-open [conn (jdbc/get-connection *db*)]
        (let [result (setup.shared/import-wfo-taxonomy! conn)]
          (is (nil? (:ok result)))
          (is (str/includes? (str (:error result)) "Could not connect to GitHub")))))))

(deftest test-the-synchronous-entry-point-refuses-a-database-that-has-taxa
  (testing "the guard is the same one the wizard's POST uses"
    (let [dir (fs/create-temp-dir {:prefix "sepal-init-db"})]
      (try
        (jdbc/execute! *db* ["insert into taxon (id, name, rank) values (9000003, 'Occupied', 'genus')"])
        (let [result (setup.shared/import-wfo-taxonomy! *db*)]
          (is (nil? (:ok result)))
          (is (str/includes? (str (:error result)) "taxa already exist")))
        (finally
          (jdbc/execute! *db* ["delete from taxon where id = 9000003"])
          (fs/delete-tree dir))))))

(deftest test-an-install-with-no-reference-path-degrades-rather-than-fails
  (testing "env-opts always resolves a destination, but start-process! does not
            require one — the CLI and the test fixture pass no path at all. That
            has to be a warning, not a failed setup."
    (let [[tracker _] (recording-tracker)
          seen (atom [])]
      (setup.shared/run-import!
        *db* tracker
        {:fetch-manifest-fn (constantly (manifest :synonyms synonyms-entry))
         :download-fn (writing-download seen)
         :import-fn (constantly 9)
         :ref-dest nil})
      (is (= :done (:phase @tracker)))
      (is (= 9 (:taxa-count @tracker)))
      (is (str/includes? (str (:warning @tracker)) "no synonym reference path"))
      (is (= [taxa-url] @seen)
          "a download was attempted with nowhere to put it"))))
