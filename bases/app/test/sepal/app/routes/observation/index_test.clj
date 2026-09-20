(ns sepal.app.routes.observation.index-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [integrant.core :as ig]
            [peridot.core :as peri]
            [sepal.accession.interface :as accession.i]
            [sepal.app.test :as app.test]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.location.interface :as location.i]
            [sepal.material.interface :as material.i]
            [sepal.observation.interface :as observation.i]
            [sepal.taxon.interface :as taxon.i]
            [sepal.user.interface :as user.i])
  (:import [java.time LocalDate]
           [org.jsoup Jsoup]))

(use-fixtures :once default-system-fixture)

(def ^:private password "testpassword123")

;; A function, not a top-level def: *db* is bound by the fixture at run time,
;; so a def's value expression would capture nil at namespace load.
(defn- fixtures []
  {[::user.i/factory :key/user] {:db *db* :password password :role :editor}
   [::taxon.i/factory :key/taxon] {:db *db*}
   [::location.i/factory :key/location] {:db *db*}
   [::accession.i/factory :key/accession] {:db *db* :taxon (ig/ref :key/taxon)}
   [::material.i/factory :key/material] {:db *db*
                                         :accession (ig/ref :key/accession)
                                         :location (ig/ref :key/location)}})

(defn- create! [& {:keys [user] :as data}]
  (observation.i/create! *db* (-> data
                                  (dissoc :user)
                                  (assoc :created-by (:user/id user)))))

(defn- fetch
  [sess url & {:as params}]
  (let [{:keys [response]} (if (seq params)
                             (peri/request sess url :params params)
                             (peri/request sess url))]
    (Jsoup/parse ^String (:body response))))

(defn- row-ids [body]
  (->> (.select body "[data-observation-id]")
       (map #(.attr % "data-observation-id"))
       set))

(deftest test-the-list-renders-with-no-filter
  (tf/testing "GET /observation/ with an existing observation and no query"
    (fixtures)
    (fn [{:keys [user material]}]
      (let [observation (create! :resource-type :material
                                 :resource-id (:material/id material)
                                 :user user
                                 :type "general"
                                 :observed-on "2026-01-01"
                                 :note "seen once")
            sess (app.test/login (:user/email user) password)
            body (fetch sess "/observation/")]
        (is (some? (.selectFirst body "table.spl-table")))
        (is (contains? (row-ids body) (str (:observation/id observation))))
        (observation.i/delete! *db* (:observation/id observation))))))

(deftest test-filtering-by-type-value-and-date-range-returns-the-exact-subset
  (tf/testing "type:phenology value:flowering over a date range"
    (fixtures)
    (fn [{:keys [user material]}]
      (let [in-range (create! :resource-type :material
                              :resource-id (:material/id material)
                              :user user
                              :type "phenology"
                              :value "flowering"
                              :observed-on "2026-03-05")
            out-of-range (create! :resource-type :material
                                  :resource-id (:material/id material)
                                  :user user
                                  :type "phenology"
                                  :value "flowering"
                                  :observed-on "2026-04-05")
            different-type (create! :resource-type :material
                                    :resource-id (:material/id material)
                                    :user user
                                    :type "condition"
                                    :value "fair"
                                    :observed-on "2026-03-06")
            sess (app.test/login (:user/email user) password)
            body (fetch sess "/observation/"
                        "q" "type:phenology value:flowering observed:>=2026-03-01 observed:<=2026-03-10")]
        (is (= #{(str (:observation/id in-range))} (row-ids body))
            "only the phenology/flowering row inside the date range")
        (doseq [o [in-range out-of-range different-type]]
          (observation.i/delete! *db* (:observation/id o)))))))

(deftest test-the-overdue-filter-returns-only-rows-whose-due-date-has-passed
  (tf/testing "the overdue affordance's due:<=<today> query"
    (fixtures)
    (fn [{:keys [user material]}]
      (let [today (LocalDate/now)
            overdue (create! :resource-type :material
                             :resource-id (:material/id material)
                             :user user
                             :type "general"
                             :observed-on "2026-01-01"
                             :next-check-on (str (.minusDays today 5)))
            not-yet-due (create! :resource-type :material
                                 :resource-id (:material/id material)
                                 :user user
                                 :type "general"
                                 :observed-on "2026-01-01"
                                 :next-check-on (str (.plusDays today 5)))
            never-due (create! :resource-type :material
                               :resource-id (:material/id material)
                               :user user
                               :type "general"
                               :observed-on "2026-01-01")
            sess (app.test/login (:user/email user) password)
            body (fetch sess "/observation/" "q" (str "due:<=" today))]
        (is (= #{(str (:observation/id overdue))} (row-ids body))
            "only the row whose next_check_on is on or before today")
        (doseq [o [overdue not-yet-due never-due]]
          (observation.i/delete! *db* (:observation/id o)))))))

(deftest test-the-overdue-toggle-is-a-one-click-affordance
  (tf/testing "an overdue link on the page, not only the query syntax"
    (fixtures)
    (fn [{:keys [user]}]
      (let [sess (app.test/login (:user/email user) password)
            body (fetch sess "/observation/")
            link (.selectFirst body "a[href*=due]")]
        (is (some? link) "a link applies the overdue filter without typing it")))))

(deftest test-the-overdue-toggle-adds-to-an-existing-query-rather-than-replacing-it
  (tf/testing "clicking Overdue with a filter already typed"
    (fixtures)
    (fn [{:keys [user]}]
      (let [sess (app.test/login (:user/email user) password)
            body (fetch sess "/observation/" "q" "type:phenology")
            link (.selectFirst body "a[href*=due]")
            href (.attr link "href")]
        (is (some? link))
        (is (.contains href "type") "the existing filter is still in the toggle's href")
        (is (.contains href "due") "the overdue term is added to it")))))

(deftest test-a-combined-query-of-a-filter-and-overdue-narrows-and-shows-active
  (tf/testing "type:phenology plus due:<=<today> applies both filters"
    (fixtures)
    (fn [{:keys [user material]}]
      (let [today (LocalDate/now)
            matching (create! :resource-type :material
                              :resource-id (:material/id material)
                              :user user
                              :type "phenology"
                              :observed-on "2026-01-01"
                              :next-check-on (str (.minusDays today 5)))
            right-type-not-overdue (create! :resource-type :material
                                            :resource-id (:material/id material)
                                            :user user
                                            :type "phenology"
                                            :observed-on "2026-01-01"
                                            :next-check-on (str (.plusDays today 5)))
            overdue-but-wrong-type (create! :resource-type :material
                                            :resource-id (:material/id material)
                                            :user user
                                            :type "condition"
                                            :observed-on "2026-01-01"
                                            :next-check-on (str (.minusDays today 5)))
            sess (app.test/login (:user/email user) password)
            body (fetch sess "/observation/" "q" (str "type:phenology due:<=" today))]
        (is (= #{(str (:observation/id matching))} (row-ids body))
            "both filters narrow together")
        (is (some? (.selectFirst body "a:contains(Showing overdue)"))
            "the toggle reflects that the overdue term is already in the query")
        (doseq [o [matching right-type-not-overdue overdue-but-wrong-type]]
          (observation.i/delete! *db* (:observation/id o)))))))

(deftest test-a-material-observations-row-links-to-the-material
  (tf/testing "a material observation's Subject cell links to the material"
    (fixtures)
    (fn [{:keys [user material]}]
      (let [observation (create! :resource-type :material
                                 :resource-id (:material/id material)
                                 :user user
                                 :type "general"
                                 :observed-on "2026-01-01")
            sess (app.test/login (:user/email user) password)
            body (fetch sess "/observation/")
            href (str "/material/" (:material/id material) "/")
            row (.selectFirst body (str "[data-observation-id=" (:observation/id observation) "]"))]
        (is (some? row))
        (is (some? (.selectFirst row (str "a[href='" href "']"))))
        (observation.i/delete! *db* (:observation/id observation))))))

(deftest test-a-location-observations-row-links-to-the-location
  (tf/testing "a location observation's Subject cell links to the location"
    (fixtures)
    (fn [{:keys [user location]}]
      (let [observation (create! :resource-type :location
                                 :resource-id (:location/id location)
                                 :user user
                                 :type "general"
                                 :observed-on "2026-01-01")
            sess (app.test/login (:user/email user) password)
            body (fetch sess "/observation/")
            href (str "/location/" (:location/id location) "/")
            row (.selectFirst body (str "[data-observation-id=" (:observation/id observation) "]"))]
        (is (some? row))
        (is (some? (.selectFirst row (str "a[href='" href "']"))))
        (observation.i/delete! *db* (:observation/id observation))))))

(deftest test-a-row-links-to-its-own-detail-page
  (tf/testing "GET /observation/:id/ shows the observation and its subject"
    (fixtures)
    (fn [{:keys [user material]}]
      (let [observation (create! :resource-type :material
                                 :resource-id (:material/id material)
                                 :user user
                                 :type "phenology"
                                 :value "flowering"
                                 :observed-on "2026-01-01")
            sess (app.test/login (:user/email user) password)
            body (fetch sess (str "/observation/" (:observation/id observation) "/"))
            href (str "/material/" (:material/id material) "/")]
        (is (some? (.selectFirst body (str "a[href='" href "']"))))
        (is (.contains (.text body) "Flowering"))
        (observation.i/delete! *db* (:observation/id observation))))))
