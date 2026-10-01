(ns sepal.app.routes.location.rollup-test
  "A location's panel, Observations tab and Media tab cover its sub-locations."
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [integrant.core :as ig]
            [next.jdbc.sql :as jdbc.sql]
            [peridot.core :as peri]
            [sepal.accession.interface :as accession.i]
            [sepal.app.test :as app.test]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.database.interface :as db.i]
            [sepal.location.interface :as location.i]
            [sepal.material.interface :as material.i]
            [sepal.media.interface :as media.i]
            [sepal.observation.interface :as observation.i]
            [sepal.propagation.interface :as propagation.i]
            [sepal.taxon.interface :as taxon.i]
            [sepal.test.interface :as test.i]
            [sepal.user.interface :as user.i])
  (:import [org.jsoup Jsoup]))

(use-fixtures :once default-system-fixture)

(def ^:private password "testpassword123")

(defn- tree
  "Orchard with Row 3 and Row 5 inside it, and a top-level Nursery, plus
  whatever `more` adds. A function, because *db* is bound by the fixture,
  after this namespace loads."
  [& {:as more}]
  (merge {[::user.i/factory :key/user] {:db *db* :password password :role :editor}
          [::taxon.i/factory :key/taxon] {:db *db*}
          [::accession.i/factory :key/accession] {:db *db* :taxon (ig/ref :key/taxon)}
          [::location.i/factory :key/orchard] {:db *db*}
          [::location.i/factory :key/row3] {:db *db* :parent (ig/ref :key/orchard)}
          [::location.i/factory :key/row5] {:db *db* :parent (ig/ref :key/orchard)}
          [::location.i/factory :key/nursery] {:db *db*}}
         more))

(defn- material-in [location-key]
  {:db *db*
   :accession (ig/ref :key/accession)
   :location (ig/ref location-key)
   :data {:status :alive :quantity 1}})

(defn- page [sess path & {:as params}]
  (-> sess
      (peri/request path :params (or params {}))
      :response :body
      (as-> ^String b (Jsoup/parse b))))

(defn- panel [sess location]
  (page sess (str "/location/" (:location/id location) "/panel/")))

(defn- section
  "The panel section titled `title`."
  [doc title]
  (some #(when (str/starts-with? (.text (.selectFirst % ".spl-collapse-title")) title) %)
        (.select doc ".spl-collapse")))

(defn- section-text [doc title]
  (some-> (section doc title) (.text)))

(defn- material-count
  "The Statistics section's Material value, or nil when the section has none."
  [doc]
  (some-> (section doc "Statistics") (.selectFirst "span.font-semibold") (.text) parse-long))

(defn- move! [material location]
  (material.i/update! *db* (:material/id material)
                      {:location-id (:location/id location) :reason "transferred"}))

(deftest test-material-count-covers-the-subtree
  (tf/testing "one material in Row 3 and one in the Orchard itself"
    (tree [::material.i/factory :key/in-row] (material-in :key/row3)
          [::material.i/factory :key/in-orchard] (material-in :key/orchard))
    (fn [{:keys [user orchard row3 row5]}]
      (let [sess (app.test/login (:user/email user) password)]
        (is (= 2 (material-count (panel sess orchard))))
        (is (= 1 (material-count (panel sess row3))))
        (is (= 0 (material-count (panel sess row5))))))))

(deftest test-re-parenting-moves-material-into-the-new-parents-count
  (tf/testing "the plants didn't move, so no material_change is written"
    (tree [::material.i/factory :key/in-nursery] (material-in :key/nursery))
    (fn [{:keys [user orchard nursery in-nursery]}]
      (let [sess (app.test/login (:user/email user) password)
            changes #(db.i/count *db* {:select [:id] :from [:material_change]
                                       :where [:= :material_id (:material/id in-nursery)]})
            before (changes)
            url (str "/location/" (:location/id nursery) "/general/")
            {:keys [response] :as sess} (peri/request sess url)
            token (test.i/response-anti-forgery-token response)]
        (try
          (is (= 0 (material-count (panel sess orchard))))
          (peri/request sess url
                        :request-method :post
                        :params {:__anti-forgery-token token
                                 :name (:location/name nursery)
                                 :code (:location/code nursery)
                                 :description ""
                                 :parent-id (str (:location/id orchard))})
          (is (= (:location/id orchard)
                 (:location/parent-id (location.i/get-by-id *db* (:location/id nursery)))))
          (is (= 1 (material-count (panel sess orchard))))
          (is (= before (changes)))
          (finally
            ;; The nursery's halt deletes it, and the orchard can't go first
            ;; while the nursery sits inside it.
            (location.i/update! *db* (:location/id nursery) {:parent-id nil})
            (jdbc.sql/delete! *db* :activity {:created_by (:user/id user)})))))))

(deftest test-awaiting-planting-rolls-up
  (tf/testing "one intended for Row 3 and planted in Row 5, one intended for
               Row 3 and not planted"
    (tree [::accession.i/factory :key/intended] {:db *db*
                                                 :taxon (ig/ref :key/taxon)
                                                 :intended-location (ig/ref :key/row3)}
          [::accession.i/factory :key/waiting] {:db *db*
                                                :taxon (ig/ref :key/taxon)
                                                :intended-location (ig/ref :key/row3)}
          [::material.i/factory :key/planted] {:db *db*
                                               :accession (ig/ref :key/intended)
                                               :location (ig/ref :key/row5)
                                               :data {:status :alive :quantity 1}})
    (fn [{:keys [user orchard row3 intended waiting]}]
      (let [sess (app.test/login (:user/email user) password)
            orchard-awaiting (section-text (panel sess orchard) "Awaiting planting")
            row3-awaiting (section-text (panel sess row3) "Awaiting planting")]
        (is (str/includes? orchard-awaiting (:accession/code waiting))
            "waiting for a row is waiting for the Orchard")
        (is (not (str/includes? orchard-awaiting (:accession/code intended)))
            "planted in another row is planted in the Orchard")
        (is (str/includes? row3-awaiting (:accession/code intended))
            "but on Row 3's panel it is still waiting")))))

(deftest test-moved-shows-only-moves-out-of-the-subtree
  (tf/testing "a move between rows stays inside the Orchard"
    (tree [::material.i/factory :key/to-row5] (material-in :key/row3)
          [::material.i/factory :key/to-nursery] (material-in :key/row3))
    (fn [{:keys [user orchard row3 row5 nursery to-row5 to-nursery]}]
      (move! to-row5 row5)
      (move! to-nursery nursery)
      (let [sess (app.test/login (:user/email user) password)
            row3-moved (section-text (panel sess row3) "Moved")
            orchard-moved (section-text (panel sess orchard) "Moved")]
        (try
          (is (str/includes? row3-moved (:material/code to-row5)))
          (is (str/includes? row3-moved (:material/code to-nursery)))
          (is (str/includes? orchard-moved (:material/code to-nursery)))
          (is (not (str/includes? orchard-moved (:material/code to-row5))))
          (finally
            ;; Each now stands somewhere its halt doesn't wait for, so it
            ;; goes first. material_change goes with it.
            (doseq [m [to-row5 to-nursery]]
              (jdbc.sql/delete! *db* :material {:id (:material/id m)}))))))))

(deftest test-propagations-roll-up
  (tf/testing "an active batch on Row 3 shows on the Orchard's panel"
    (tree [::propagation.i/factory :key/propagation] {:db *db*
                                                      :accession (ig/ref :key/accession)})
    (fn [{:keys [user orchard row3 propagation]}]
      (propagation.i/update! *db* (:propagation/id propagation)
                             {:location-id (:location/id row3)})
      (let [sess (app.test/login (:user/email user) password)]
        (is (some? (some-> (section (panel sess orchard) "Propagation")
                           (.selectFirst (str "a[href=\"/propagation/"
                                              (:propagation/id propagation) "/\"]")))))))))

(deftest test-sub-location-observations-are-listed-read-only
  (tf/testing "Row 3's observation shows on the Orchard's tab, edited on Row 3's"
    (tree)
    (fn [{:keys [user orchard row3]}]
      (let [own (observation.i/create! *db* {:resource-type "location"
                                             :resource-id (:location/id orchard)
                                             :type "general"
                                             :observed-on "2026-01-02"
                                             :note "rollup own"})
            sub (observation.i/create! *db* {:resource-type "location"
                                             :resource-id (:location/id row3)
                                             :type "general"
                                             :observed-on "2026-01-01"
                                             :note "rollup sub"})
            sess (app.test/login (:user/email user) password)
            doc (page sess (str "/location/" (:location/id orchard) "/observations/"))
            group (some #(when (= "In sub-locations" (.text (.selectFirst % "h3"))) %)
                        (.select doc "section:has(h3)"))]
        (try
          (is (some? group) "a group for the sub-locations")
          (is (some? (some-> group (.selectFirst (str "a[href=\"/location/" (:location/id row3)
                                                      "/observations/\"]"))))
              "linking to the location where it is edited")
          (is (nil? (.selectFirst doc (str "[hx-post*=\"/observations/"
                                           (:observation/id sub) "/\"]")))
              "with no edit form of its own here")
          (is (some? (.selectFirst doc (str "[hx-post=\"/location/" (:location/id orchard)
                                            "/observations/" (:observation/id own) "/\"]")))
              "while the Orchard's own observation keeps its form")
          (finally
            (observation.i/delete! *db* (:observation/id own))
            (observation.i/delete! *db* (:observation/id sub))))))))

(deftest test-media-below-covers-sub-locations
  (tf/testing "media on Row 3 and on material in Row 3 is below the Orchard"
    (tree [::material.i/factory :key/in-row] (material-in :key/row3)
          [::media.i/factory :key/on-row] {:db *db* :user (ig/ref :key/user)}
          [::media.i/factory :key/on-material] {:db *db* :user (ig/ref :key/user)})
    (fn [{:keys [user orchard row3 in-row on-row on-material]}]
      (media.i/link! *db* (:media/id on-row) (:location/id row3) "location")
      (media.i/link! *db* (:media/id on-material) (:material/id in-row) "material")
      (let [sess (app.test/login (:user/email user) password)
            tile (fn [doc m] (.selectFirst doc (str "a[href=\"/media/" (:media/id m) "/\"]")))
            url (str "/location/" (:location/id orchard) "/media/")
            below (page sess url "scope" "below")
            direct (page sess url)]
        (try
          (testing "scope=below"
            (is (some? (tile below on-row)))
            (is (some? (tile below on-material))))
          (testing "the default scope is the location's own media"
            (is (nil? (tile direct on-row)))
            (is (nil? (tile direct on-material))))
          (finally
            (media.i/unlink! *db* (:media/id on-row))
            (media.i/unlink! *db* (:media/id on-material))))))))
