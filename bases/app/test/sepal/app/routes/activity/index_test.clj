(ns sepal.app.routes.activity.index-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is use-fixtures]]
            [integrant.core :as ig]
            [peridot.core :as peri]
            [sepal.accession.interface :as accession.i]
            [sepal.activity.interface :as activity.i]
            [sepal.app.cli.activity :as import.activity]
            [sepal.app.routes.activity.index :as activity.index]
            [sepal.app.test :as app.test]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.database.interface :as db.i]
            [sepal.location.interface :as location.i]
            [sepal.location.interface.activity :as location.activity]
            [sepal.material.interface :as material.i]
            [sepal.material.interface.activity :as material.activity]
            [sepal.media.interface :as media.i]
            [sepal.media.interface.activity :as media.activity]
            [sepal.note.interface :as note.i]
            [sepal.note.interface.activity :as note.activity]
            [sepal.observation.interface :as observation.i]
            [sepal.observation.interface.activity :as observation.activity]
            [sepal.propagation.interface :as propagation.i]
            [sepal.propagation.interface.activity :as propagation.activity]
            [sepal.settings.interface.activity :as settings.activity]
            [sepal.taxon.interface :as taxon.i]
            [sepal.taxon.interface.activity :as taxon.activity]
            [sepal.user.interface :as user.i])
  (:import [java.time Instant LocalDate]))

(use-fixtures :once default-system-fixture)

(def password "testpassword123")

(defn- clear-activity! []
  (db.i/execute! *db* {:delete-from :activity}))

(defmacro with-cleared-activity
  "Run the body against an empty activity table, and empty it again afterwards.

  The system fixture is :once, so rows left behind leak into whichever test runs
  next. The clear has to happen inside the test function rather than in an :each
  fixture: the user factory's teardown runs before that fixture would, and it
  cannot delete a user an activity row still references."
  [& body]
  `(do (clear-activity!)
       (try
         ~@body
         (finally
           (clear-activity!)))))

(defn- get-activity-page [user]
  (-> (app.test/login (:user/email user) password)
      (peri/request "/activity")
      :response))

(deftest test-empty-state-shown-when-there-is-no-activity
  (tf/testing "An editor with an empty feed sees the empty state"
    {[::user.i/factory :key/user] {:db *db*
                                   :password password
                                   :role :editor}}
    (fn [{:keys [user]}]
      (with-cleared-activity
        (let [response (get-activity-page user)]
          (is (app.test/body-contains? response "No activity yet")
              "Empty feed should render the empty state heading"))))))

(deftest test-empty-state-offers-create-links-to-an-editor
  (tf/testing "The empty state links an editor to the first records to create"
    {[::user.i/factory :key/user] {:db *db*
                                   :password password
                                   :role :editor}}
    (fn [{:keys [user]}]
      (with-cleared-activity
        (let [body (app.test/parse-body (get-activity-page user))]
          (is (some? (.selectFirst body "a[href='/location/new/']"))
              "Editor should be offered a link to create a location")
          (is (some? (.selectFirst body "a[href='/accession/new/']"))
              "Editor should be offered a link to create an accession"))))))

(deftest test-empty-state-withholds-create-links-from-a-reader
  (tf/testing "A reader has no create permission, so is offered no create links"
    {[::user.i/factory :key/user] {:db *db*
                                   :password password
                                   :role :reader}}
    (fn [{:keys [user]}]
      (with-cleared-activity
        (let [response (get-activity-page user)
              body (app.test/parse-body response)]
          (is (app.test/body-contains? response "No activity yet")
              "A reader should still see the empty state heading")
          (is (nil? (.selectFirst body "a[href='/location/new/']"))
              "Reader should not be offered a link to create a location")
          (is (nil? (.selectFirst body "a[href='/accession/new/']"))
              "Reader should not be offered a link to create an accession"))))))

(deftest test-empty-state-offers-the-invite-link-to-an-admin
  (tf/testing "Only an admin can invite, so only an admin is offered the link"
    {[::user.i/factory :key/user] {:db *db*
                                   :password password
                                   :role :admin}}
    (fn [{:keys [user]}]
      (with-cleared-activity
        (let [body (app.test/parse-body (get-activity-page user))]
          (is (some? (.selectFirst body "a[href='/settings/users/invite']"))
              "Admin should be offered a link to invite a user"))))))

(deftest test-empty-state-withholds-the-invite-link-from-an-editor
  (tf/testing "An editor cannot invite, so is offered no invite link"
    {[::user.i/factory :key/user] {:db *db*
                                   :password password
                                   :role :editor}}
    (fn [{:keys [user]}]
      (with-cleared-activity
        (let [body (app.test/parse-body (get-activity-page user))]
          (is (nil? (.selectFirst body "a[href='/settings/users/invite']"))
              "Editor should not be offered a link to invite a user"))))))

(deftest test-empty-state-hidden-when-the-feed-renders-an-activity
  (tf/testing "A feed with a renderable activity shows it instead of the empty state"
    {[::user.i/factory :key/user] {:db *db*
                                   :password password
                                   :role :editor}
     [::location.i/factory :key/location] {:db *db*}}
    (fn [{:keys [user location]}]
      (with-cleared-activity
        (location.activity/create! *db*
                                   location.activity/created
                                   (:user/id user)
                                   location)
        (let [response (get-activity-page user)]
          (is (not (app.test/body-contains? response "No activity yet"))
              "A rendered activity should suppress the empty state")
          (is (app.test/body-contains? response (:location/name location))
              "The location activity should be rendered"))))))

(deftest test-empty-state-shown-when-every-activity-is-unrenderable
  (tf/testing "Activity the feed has no renderer for leaves the page blank, so the
  empty state is what should show"
    {[::user.i/factory :key/user] {:db *db*
                                   :password password
                                   :role :editor}}
    (fn [{:keys [user]}]
      (with-cleared-activity
        (settings.activity/create! *db*
                                   settings.activity/updated
                                   (:user/id user)
                                   {:changes {:organization.long_name "Kew"}})
        (let [response (get-activity-page user)]
          (is (app.test/body-contains? response "No activity yet")
              "An activity with no renderer should not suppress the empty state"))))))

(deftest test-an-import-is-rendered-rather-than-filtered-out
  ;; It was not. The loader wrote the event and the feed dropped it, because
  ;; `renderable` keeps only what `activity-data` answers for and this type had
  ;; no method. Written, and shown to nobody.
  (tf/testing "a completed import appears in the feed, with its counts"
    {[::user.i/factory :key/user] {:db *db*
                                   :password password
                                   :role :editor}}
    (fn [{:keys [user]}]
      (with-cleared-activity
        (import.activity/create! *db*
                                 import.activity/completed
                                 (:user/id user)
                                 {:counts {"accession" 2646 "material" 3437}})
        (let [response (get-activity-page user)]
          (is (not (app.test/body-contains? response "No activity yet"))
              "an import should suppress the empty state")
          (is (app.test/body-contains? response "6,083 records")
              "the total is what the row names")
          ;; The raw body, not `body-contains?`: that reads visible text, and
          ;; the counts sit in the chip's `title` the way every other chip
          ;; carries its context.
          (is (str/includes? (:body response) "accession 2646, material 3437")
              "and the per-table counts are there to be read, since they are
               the whole of what the event records"))))))

(deftest test-empty-state-not-appended-to-a-later-page
  (tf/testing "A later page that comes back empty renders nothing, not the empty
  state: the infinite-scroll sentinel swaps pages in with beforeend, so an empty
  state here would land underneath a populated feed"
    {[::user.i/factory :key/user] {:db *db*
                                   :password password
                                   :role :editor}}
    (fn [{:keys [user]}]
      (with-cleared-activity
        (let [{:keys [response]} (-> (app.test/login (:user/email user) password)
                                     (peri/request "/activity?page=2"
                                                   :headers {"hx-request" "true"}))]
          (is (not (app.test/body-contains? response "No activity yet"))
              "Page 2 should not render the empty state"))))))

(deftest test-a-chip-tooltip-carries-its-own-timestamp
  ;; A card collapses a run of events into one sentence with one relative time,
  ;; so without this a chip's own timestamp is nowhere on the page.
  (is (= "Accession \u2022 Quercus alba \u2022 September 16, 2026 at 9:00 AM EDT"
         (activity.index/chip-title "Accession \u2022 Quercus alba"
                                    (java.time.Instant/parse "2026-09-16T13:00:00Z")
                                    "America/New_York")))
  (is (= "Accession"
         (activity.index/chip-title "Accession" nil "America/New_York"))))

(deftest test-a-day-that-straddles-a-page-boundary-gets-one-heading
  (tf/testing "The next page of a day repeats its cards, not its heading. Days
  are grouped within a page, so without the last-day the previous page ended on
  the reader sees 'Yesterday' twice with a page gutter between them."
    {[::user.i/factory :key/user] {:db *db*
                                   :password password
                                   :role :editor}
     [::location.i/factory :key/location] {:db *db*}}
    (fn [{:keys [user location]}]
      (with-cleared-activity
        (dotimes [_ 4]
          (location.activity/create! *db*
                                     location.activity/created
                                     (:user/id user)
                                     location))
        (let [session (app.test/login (:user/email user) password)
              today (str (java.time.LocalDate/now (java.time.ZoneId/of "UTC")))
              page-2 (fn [query]
                       (-> session
                           (peri/request (str "/activity?page=2&page-size=2" query)
                                         :headers {"hx-request" "true"})
                           :response
                           (app.test/parse-body)))]
          (is (some? (.selectFirst (page-2 "") "h2.spl-changelog-day"))
              "With no last-day the second page repeats the heading")
          (is (nil? (.selectFirst (page-2 (str "&last-day=" today))
                                  "h2.spl-changelog-day"))
              "Carrying the day the previous page ended on drops the repeat")
          (is (some? (.selectFirst (page-2 (str "&last-day=" today))
                                   ".spl-changelog-entry"))
              "and the cards on that page are still rendered")
          (is (some? (.selectFirst (page-2 "&last-day=not-a-date")
                                   "h2.spl-changelog-day"))
              "A malformed last-day costs a repeated heading, not an error"))))))

(deftest test-only-the-first-page-carries-the-feed-container
  (tf/testing "Later pages are appended into it, so a second one would pay the
  gutter twice and open a visible gap at every page boundary"
    {[::user.i/factory :key/user] {:db *db*
                                   :password password
                                   :role :editor}
     [::location.i/factory :key/location] {:db *db*}}
    (fn [{:keys [user location]}]
      (with-cleared-activity
        (dotimes [_ 4]
          (location.activity/create! *db*
                                     location.activity/created
                                     (:user/id user)
                                     location))
        (let [session (app.test/login (:user/email user) password)
              body (fn [query & {:keys [htmx?]}]
                     (-> session
                         (peri/request (str "/activity?page-size=2&" query)
                                       :headers (if htmx?
                                                  {"hx-request" "true"}
                                                  {}))
                         :response
                         (app.test/parse-body)))]
          (is (some? (.selectFirst (body "page=1") "#activity-days"))
              "A page load opens the container")
          (is (nil? (.selectFirst (body "page=2" :htmx? true) "#activity-days"))
              "The sentinel's response is appended into it rather than opening
               another")
          (is (some? (.selectFirst (body "page=2") "#activity-days"))
              "but a browser loading page 2 directly still gets a container"))))))

(defn- create-overdue-fixture-observation! [& {:keys [user location next-check-on]}]
  (observation.i/create! *db* {:resource-type :location
                               :resource-id (:location/id location)
                               :created-by (:user/id user)
                               :type "general"
                               :observed-on "2026-01-01"
                               :next-check-on next-check-on}))

(deftest test-the-overdue-observation-count-appears-when-something-is-overdue
  (tf/testing "The dashboard counts observations whose due date has passed"
    {[::user.i/factory :key/user] {:db *db*
                                   :password password
                                   :role :editor}
     [::location.i/factory :key/location] {:db *db*}}
    (fn [{:keys [user location]}]
      (let [today (LocalDate/now)
            observation (create-overdue-fixture-observation!
                          :user user
                          :location location
                          :next-check-on (str (.minusDays today 1)))]
        (try
          (is (app.test/body-contains? (get-activity-page user) "1 overdue observation")
              "One overdue observation is counted on the dashboard")
          (finally
            (observation.i/delete! *db* (:observation/id observation))))))))

(deftest test-the-overdue-observation-count-is-absent-when-nothing-is-overdue
  (tf/testing "No count at all, not a zero, when nothing is due yet"
    {[::user.i/factory :key/user] {:db *db*
                                   :password password
                                   :role :editor}
     [::location.i/factory :key/location] {:db *db*}}
    (fn [{:keys [user location]}]
      (let [today (LocalDate/now)
            observation (create-overdue-fixture-observation!
                          :user user
                          :location location
                          :next-check-on (str (.plusDays today 5)))]
        (try
          (is (nil? (.selectFirst (app.test/parse-body (get-activity-page user))
                                  "[data-overdue-count]"))
              "A dashboard with nothing overdue shows no overdue element, not a zero")
          (finally
            (observation.i/delete! *db* (:observation/id observation))))))))

(deftest test-the-overdue-observation-count-links-to-the-overdue-filter
  (tf/testing "The count links to the observation index, filtered to the same rows"
    {[::user.i/factory :key/user] {:db *db*
                                   :password password
                                   :role :editor}
     [::location.i/factory :key/location] {:db *db*}}
    (fn [{:keys [user location]}]
      (let [today (LocalDate/now)
            observation (create-overdue-fixture-observation!
                          :user user
                          :location location
                          :next-check-on (str (.minusDays today 1)))]
        (try
          (let [session (app.test/login (:user/email user) password)
                body (app.test/parse-body (get-activity-page user))
                link (.selectFirst body "[data-overdue-count]")
                href (some-> link (.attr "href"))
                response (:response (peri/request session href))]
            (is (some? href) "the count is a link")
            (is (= 200 (:status response)) "the link resolves")
            (is (some-> (app.test/parse-body response)
                        (.selectFirst "label[x-data^=termFilter]")
                        (.attr "x-data")
                        (.endsWith ", true)"))
                "the index's overdue checkbox is checked, because the link applies the very same term it uses"))
          (finally
            (observation.i/delete! *db* (:observation/id observation))))))))

(deftest test-propagation-and-observation-events-are-rendered
  (tf/testing "propagation and observation events appear in the feed"
    {[::user.i/factory :key/user] {:db *db*
                                   :password password
                                   :role :editor}
     [::taxon.i/factory :key/taxon] {:db *db*}
     [::accession.i/factory :key/acc] {:db *db*
                                       :taxon (ig/ref :key/taxon)}
     [::location.i/factory :key/loc] {:db *db*}}
    (fn [{:keys [user acc loc]}]
      (with-cleared-activity
        (let [prop (propagation.i/create! *db* {:type :tissue_culture
                                                :parent-accession-id (:accession/id acc)})
              obs (observation.i/create! *db* {:resource-type :location
                                               :resource-id (:location/id loc)
                                               :created-by (:user/id user)
                                               :type "general"
                                               :observed-on "2026-01-01"})]
          (try
            (propagation.activity/create! *db* propagation.activity/created (:user/id user) prop)
            (observation.activity/create! *db* observation.activity/created (:user/id user) obs)
            (let [response (get-activity-page user)]
              (is (app.test/body-contains? response
                                           (str "Tissue culture from " (:accession/code acc))))
              (is (str/includes? (:body response)
                                 (str "/propagation/" (:propagation/id prop) "/")))
              (is (str/includes? (:body response)
                                 (str "/location/" (:location/id loc) "/observations/")))
              (is (app.test/body-contains? response "a propagation")
                  "the summary names the resource")
              (is (app.test/body-contains? response "an observation")))
            (finally
              (clear-activity!)
              (db.i/execute! *db* {:delete-from :observation
                                   :where [:= :id (:observation/id obs)]})
              (db.i/execute! *db* {:delete-from :propagation
                                   :where [:= :id (:propagation/id prop)]}))))))))

(deftest test-note-and-media-events-are-rendered
  (tf/testing "note and media events appear in the feed"
    {[::user.i/factory :key/user] {:db *db*
                                   :password password
                                   :role :editor}
     [::taxon.i/factory :key/taxon] {:db *db*}
     [::accession.i/factory :key/acc] {:db *db*
                                       :taxon (ig/ref :key/taxon)}}
    (fn [{:keys [user acc]}]
      (with-cleared-activity
        (let [note (note.i/create! *db* {:body "a note"
                                         :resource-type :accession
                                         :resource-id (:accession/id acc)
                                         :created-by (:user/id user)})
              media (media.i/create! *db* {:s3-bucket "b" :s3-key "media/feed-test.jpg"
                                           :size-in-bytes 1 :media-type "image/jpeg"
                                           :created-by (:user/id user)})]
          (try
            (note.activity/create! *db* note.activity/created (:user/id user) note)
            (media.activity/create! *db* media.activity/created (:user/id user) media)
            (let [response (get-activity-page user)]
              (is (str/includes? (:body response)
                                 (str "/accession/" (:accession/id acc) "/notes/")))
              (is (app.test/body-contains? response "feed-test.jpg"))
              (is (app.test/body-contains? response "a note"))
              (is (app.test/body-contains? response "a media item")))
            (db.i/execute! *db* {:delete-from :media :where [:= :id (:media/id media)]})
            (media.activity/create! *db* media.activity/deleted (:user/id user) media)
            (let [response (get-activity-page user)]
              (is (app.test/body-contains? response "deleted a media item")
                  "a deleted item still shows, named from the event")
              (is (not (str/includes? (:body response)
                                      (str "/media/" (:media/id media) "/")))
                  "and it links nowhere, since the media is gone"))
            (finally
              (clear-activity!)
              (db.i/execute! *db* {:delete-from :note :where [:= :id (:note/id note)]})
              (db.i/execute! *db* {:delete-from :media :where [:= :id (:media/id media)]}))))))))


(deftest test-a-material-update-is-named-by-its-full-code
  (tf/testing "an updated material's chip carries its accession code, not the
  material code alone"
    {[::user.i/factory :key/user] {:db *db*
                                   :password password
                                   :role :editor}
     [::taxon.i/factory :key/taxon] {:db *db*}
     [::accession.i/factory :key/acc] {:db *db*
                                       :taxon (ig/ref :key/taxon)}
     [::location.i/factory :key/loc] {:db *db*}
     [::material.i/factory :key/mat] {:db *db*
                                      :accession (ig/ref :key/acc)
                                      :location (ig/ref :key/loc)}}
    (fn [{:keys [user acc mat]}]
      (with-cleared-activity
        (material.activity/create! *db* material.activity/updated (:user/id user) mat)
        (let [chip (-> (get-activity-page user)
                       (app.test/parse-body)
                       (.selectFirst (str "a[href*=/material/" (:material/id mat) "/]")))]
          (is (some? chip))
          (is (str/includes? (.text chip) (:accession/code acc))))))))

(deftest test-a-taxon-event-with-no-subject-is-named-from-its-payload
  (tf/testing "an imported taxon event with no taxon row still shows the name it
  recorded, and links nowhere"
    {[::user.i/factory :key/user] {:db *db*
                                   :password password
                                   :role :editor}}
    (fn [{:keys [user]}]
      (with-cleared-activity
        (activity.i/create! *db* {:type taxon.activity/updated
                                  :created-at (Instant/now)
                                  :created-by (:user/id user)
                                  :data {:taxon-name "Acalypha"
                                         :taxon-author "L."
                                         :taxon-rank :genus}})
        (let [chip (-> (get-activity-page user)
                       (app.test/parse-body)
                       (.selectFirst ".spl-chip"))]
          (is (str/includes? (.text chip) "Acalypha"))
          (is (nil? (.selectFirst chip "a"))))))))
