(ns sepal.app.feature-toggle-test
  "What a garden sees with each feature turned off."
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [integrant.core :as ig]
            [next.jdbc.sql :as jdbc.sql]
            [peridot.core :as peri]
            [sepal.accession.interface :as accession.i]
            [sepal.app.test :as app.test]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.location.interface :as location.i]
            [sepal.material.interface :as material.i]
            [sepal.media.interface :as media.i]
            [sepal.observation.interface :as observation.i]
            [sepal.observation.interface.activity :as observation.activity]
            [sepal.propagation.interface :as propagation.i]
            [sepal.tag.interface :as tag.i]
            [sepal.taxon.interface :as taxon.i]
            [sepal.test.interface :as test.i]
            [sepal.user.interface :as user.i])
  (:import [org.jsoup Jsoup]))

(use-fixtures :once default-system-fixture)

(def ^:private password "testpassword123")

(defn- fixtures []
  {[::user.i/factory :key/user] {:db *db* :password password :role :admin}
   [::taxon.i/factory :key/taxon] {:db *db*}
   [::accession.i/factory :key/acc] {:db *db* :taxon (ig/ref :key/taxon)}
   [::location.i/factory :key/loc] {:db *db*}
   [::material.i/factory :key/mat] {:db *db*
                                    :accession (ig/ref :key/acc)
                                    :location (ig/ref :key/loc)}
   [::propagation.i/factory :key/prop] {:db *db* :accession (ig/ref :key/acc)}
   [::tag.i/factory :key/tag] {:db *db*}
   [::media.i/factory :key/media] {:db *db* :user (ig/ref :key/user)}})

(defn- session [user]
  (app.test/login (:user/email user) password))

(defn- get-page [sess path]
  (:response (peri/request sess path)))

(defn- post
  "A write with a valid token, so a 404 is the feature check's and not CSRF's."
  [sess path]
  (let [{:keys [response] :as sess} (peri/request sess "/settings/profile")
        token (test.i/response-anti-forgery-token response)]
    (:response (peri/request sess path
                             :request-method :post
                             :headers {"x-csrf-token" token}))))

(defn- body [response]
  (Jsoup/parse ^String (:body response)))

(deftest test-propagation-off-routes
  (tf/testing "with Propagation off, its list and writes are gone and a record reads only"
    (fixtures)
    (fn [{:keys [user prop]}]
      (app.test/with-features-off *db* [:propagation]
        (fn []
          (let [sess (session user)
                detail (str "/propagation/" (:propagation/id prop) "/")]
            (is (= 404 (:status (get-page sess "/propagation/"))))
            (is (= 404 (:status (get-page sess "/propagation/new/"))))
            (is (= 404 (:status (post sess detail))) "an edit is refused")
            (is (= 404 (:status (post sess "/lists/propagation/columns"))))
            (let [response (get-page sess detail)]
              (is (= 200 (:status response)))
              (is (some? (.selectFirst (body response) ".spl-reader-page"))
                  "an admin gets the read-only page")
              (is (nil? (.selectFirst (body response) "form#propagation-form"))))))))))

(deftest test-observations-off-routes
  (tf/testing "with Observations off, its list, tabs and bulk action are gone"
    (fixtures)
    (fn [{:keys [user mat loc]}]
      (app.test/with-features-off *db* [:observations]
        (fn []
          (let [sess (session user)]
            (is (= 404 (:status (get-page sess "/observation/"))))
            (is (= 404 (:status (get-page sess (str "/material/" (:material/id mat) "/observations/")))))
            (is (= 404 (:status (get-page sess (str "/location/" (:location/id loc) "/observations/")))))
            (is (= 404 (:status (post sess "/material/bulk/observation/"))))))))))

(deftest test-media-off-routes
  (tf/testing "with Media off, its list, upload and tabs are gone and an item reads only"
    (fixtures)
    (fn [{:keys [user media acc]}]
      (app.test/with-features-off *db* [:media]
        (fn []
          (let [sess (session user)
                detail (str "/media/" (:media/id media) "/")]
            (is (= 404 (:status (get-page sess "/media/"))))
            (is (= 404 (:status (post sess "/media/uploaded"))))
            (is (= 404 (:status (get-page sess (str "/accession/" (:accession/id acc) "/media/")))))
            (let [response (get-page sess detail)]
              (is (= 200 (:status response)))
              (is (nil? (.selectFirst (body response) "#media-form")) "no edit form"))))))))

(deftest test-tags-off-routes
  (tf/testing "with Tags off, its list, tabs and bulk actions are gone and a tag reads only"
    (fixtures)
    (fn [{:keys [user tag mat]}]
      (app.test/with-features-off *db* [:tags]
        (fn []
          (let [sess (session user)]
            (is (= 404 (:status (get-page sess "/tag/"))))
            (is (= 404 (:status (get-page sess (str "/material/" (:material/id mat) "/tags/")))))
            (is (= 404 (:status (post sess "/accession/bulk/tags/"))))
            (let [response (get-page sess (str "/tag/" (:tag/id tag) "/"))]
              (is (= 200 (:status response)))
              (is (some? (.selectFirst (body response) ".spl-reader-page"))))))))))

(deftest test-sidenav-omits-turned-off-sections
  (tf/testing "each turned-off section leaves the rail; the rest stay"
    (fixtures)
    (fn [{:keys [user]}]
      (app.test/with-features-off *db* [:observations :propagation :media :tags]
        (fn []
          (let [rail (.selectFirst (body (get-page (session user) "/activity")) "nav.spl-rail")]
            (doseq [href ["/observation/" "/propagation/" "/media/" "/tag/"]]
              (is (nil? (.selectFirst rail (str "a[href='" href "']"))) href))
            (is (some? (.selectFirst rail "a[href='/material/']")))))))))

(deftest test-read-only-page-has-no-link-to-the-list
  (tf/testing "the breadcrumb names the section without linking to its 404"
    (fixtures)
    (fn [{:keys [user prop]}]
      (app.test/with-features-off *db* [:propagation]
        (fn []
          (let [page (body (get-page (session user) (str "/propagation/" (:propagation/id prop) "/")))]
            (is (nil? (.selectFirst page ".spl-crumbs a[href='/propagation/']")))))))))

(deftest test-record-tabs-and-actions-omit-turned-off-features
  (tf/testing "a record keeps the tabs and actions of the features that are on"
    (fixtures)
    (fn [{:keys [user mat acc taxon loc]}]
      (app.test/with-features-off *db* [:observations :propagation :media :tags]
        (fn []
          (let [sess (session user)
                tabs (fn [path] (.selectFirst (body (get-page sess path)) "nav.spl-tabs"))
                material (tabs (str "/material/" (:material/id mat) "/general/"))
                accession (tabs (str "/accession/" (:accession/id acc) "/general/"))
                taxon-tabs (tabs (str "/taxon/" (:taxon/id taxon) "/name/"))
                location (tabs (str "/location/" (:location/id loc) "/general/"))]
            (doseq [[label nav] [["material" material] ["accession" accession]
                                 ["taxon" taxon-tabs] ["location" location]]
                    suffix ["/media/" "/observations/" "/tags/"]]
              (is (nil? (.selectFirst nav (str "a[href$='" suffix "']"))) (str label suffix)))
            (is (some? (.selectFirst accession "a[href$='/notes/']")) "Notes stays")
            (is (nil? (.selectFirst (body (get-page sess (str "/material/" (:material/id mat) "/general/")))
                                    "a[href*='/propagation/new/']"))
                "no Add a propagation")))))))

(deftest test-material-panel-omits-observations
  (tf/testing "the material panel has no Observations section with Observations off"
    (fixtures)
    (fn [{:keys [user mat]}]
      (let [panel #(.text (body (get-page (session user) (str "/material/" (:material/id mat) "/panel/"))))]
        (is (re-find #"Observations" (panel)))
        (app.test/with-features-off *db* [:observations]
          #(is (not (re-find #"Observations" (panel)))))))))

(deftest test-lists-omit-turned-off-bulk-actions
  (tf/testing "material keeps status and move; accessions and taxa lose selection with Tags off"
    (fixtures)
    (fn [{:keys [user]}]
      (app.test/with-features-off *db* [:observations :tags]
        (fn []
          (let [sess (session user)
                material (body (get-page sess "/material/"))]
            (is (some? (.selectFirst material "#bulk-material-status")))
            (is (some? (.selectFirst material "#bulk-material-move")))
            (is (nil? (.selectFirst material "#bulk-material-observation")))
            (is (nil? (.selectFirst material "#bulk-material-tag-add")))
            (is (nil? (.selectFirst material "#bulk-material-tag-remove")))
            (is (nil? (.selectFirst (body (get-page sess "/accession/")) ".spl-bulk-bar")))
            (is (nil? (.selectFirst (body (get-page sess "/taxon/")) ".spl-bulk-bar")))))))))

(deftest test-observation-events-link-to-the-record
  (tf/testing "with Observations off, an observation event links to the material, not its tab"
    (fixtures)
    (fn [{:keys [user mat]}]
      (let [id (:material/id mat)
            observation (observation.i/create! *db* {:resource-type :material
                                                     :resource-id id
                                                     :type "general"
                                                     :observed-on "2026-01-01"
                                                     :note "flowering"
                                                     :created-by (:user/id user)})]
        (try
          (observation.activity/create! *db* observation.activity/created (:user/id user) observation)
          (app.test/with-features-off *db* [:observations]
            (fn []
              (let [page (body (get-page (session user) "/activity"))]
                (is (some? (.selectFirst page (str "a[href='/material/" id "/']"))))
                (is (nil? (.selectFirst page (str "a[href='/material/" id "/observations/']")))))))
          (finally
            (jdbc.sql/delete! *db* :activity {:created_by (:user/id user)})
            (observation.i/delete! *db* (:observation/id observation))))))))

(deftest test-overdue-observations-follow-the-toggle
  (tf/testing "the overdue count on the Activity page shows with Observations on and not off"
    (fixtures)
    (fn [{:keys [user mat]}]
      (let [observation (observation.i/create! *db* {:resource-type :material
                                                     :resource-id (:material/id mat)
                                                     :type "general"
                                                     :observed-on "2020-01-01"
                                                     :next-check-on "2020-02-01"
                                                     :note "recheck"
                                                     :created-by (:user/id user)})
            overdue #(.selectFirst (body (get-page (session user) "/activity")) "[data-overdue-count]")]
        (try
          (is (some? (overdue)))
          (app.test/with-features-off *db* [:observations]
            #(is (nil? (overdue))))
          (finally
            (observation.i/delete! *db* (:observation/id observation))))))))

(deftest test-tag-filter-is-ignored-with-tags-off
  (tf/testing "a tag: term filters with Tags on and is ignored with it off"
    (fixtures)
    (fn [{:keys [user]}]
      (let [rows #(app.test/first-cells (body (get-page (session user) "/material/?q=tag:no-such-tag")))]
        (is (empty? (rows)))
        (app.test/with-features-off *db* [:tags]
          #(is (seq (rows))))))))
