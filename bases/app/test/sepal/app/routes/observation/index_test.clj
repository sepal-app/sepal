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
            [sepal.settings.interface :as settings.i]
            [sepal.taxon.interface :as taxon.i]
            [sepal.user.interface :as user.i])
  (:import [java.time LocalDate ZoneId]
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
  (tf/testing "the overdue checkbox's overdue:<today> query"
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
                                 :type "pest"
                                 :value "light"
                                 :observed-on "2026-01-01"
                                 :next-check-on (str (.plusDays today 5)))
            never-due (create! :resource-type :material
                               :resource-id (:material/id material)
                               :user user
                               :type "disease"
                               :value "light"
                               :observed-on "2026-01-01")
            sess (app.test/login (:user/email user) password)
            body (fetch sess "/observation/" "q" (str "overdue:" today))]
        (is (= #{(str (:observation/id overdue))} (row-ids body))
            "only the row whose next_check_on is on or before today")
        (doseq [o [overdue not-yet-due never-due]]
          (observation.i/delete! *db* (:observation/id o)))))))

(defn- overdue-checkbox [body]
  (.selectFirst body "label[x-data^=termFilter]"))

(deftest test-the-overdue-checkbox-carries-the-overdue-term
  (tf/testing "an overdue checkbox on the page, not only the query syntax"
    (fixtures)
    (fn [{:keys [user]}]
      (let [sess (app.test/login (:user/email user) password)
            body (fetch sess "/observation/")
            checkbox (overdue-checkbox body)]
        (is (some? checkbox) "a checkbox applies the overdue filter without typing it")
        (is (.contains (.attr checkbox "x-data") (str "'overdue:" (LocalDate/now) "'")))
        (is (.contains (.attr checkbox "x-data") ", false)") "unchecked with no overdue term")
        (is (.contains (.text checkbox) "Only overdue observations"))))))

(deftest test-the-overdue-term-is-the-gardens-date
  (tf/testing "a garden far ahead of the server gets its own today"
    (fixtures)
    (fn [{:keys [user]}]
      (let [zone "Pacific/Kiritimati"
            _ (settings.i/set-value! *db* "organization.timezone" zone)
            sess (app.test/login (:user/email user) password)
            body (fetch sess "/observation/")
            garden-today (LocalDate/now (ZoneId/of zone))]
        (is (.contains (.attr (overdue-checkbox body) "x-data")
                       (str "'overdue:" garden-today "'")))
        (settings.i/set-value! *db* "organization.timezone" "UTC")))))

(deftest test-a-combined-query-of-a-filter-and-overdue-narrows-and-shows-checked
  (tf/testing "type:phenology plus overdue:<today> applies both filters"
    (fixtures)
    (fn [{:keys [user material]}]
      (let [today (LocalDate/now)
            matching (create! :resource-type :material
                              :resource-id (:material/id material)
                              :user user
                              :type "phenology"
                              :observed-on "2026-01-01"
                              :next-check-on (str (.minusDays today 5)))
            ;; Observed before `matching`, so it doesn't follow it up.
            right-type-not-overdue (create! :resource-type :material
                                            :resource-id (:material/id material)
                                            :user user
                                            :type "phenology"
                                            :observed-on "2025-12-01"
                                            :next-check-on (str (.plusDays today 5)))
            overdue-but-wrong-type (create! :resource-type :material
                                            :resource-id (:material/id material)
                                            :user user
                                            :type "condition"
                                            :observed-on "2026-01-01"
                                            :next-check-on (str (.minusDays today 5)))
            sess (app.test/login (:user/email user) password)
            body (fetch sess "/observation/" "q" (str "type:phenology overdue:" today))]
        (is (= #{(str (:observation/id matching))} (row-ids body))
            "both filters narrow together")
        (is (.contains (.attr (overdue-checkbox body) "x-data") ", true)")
            "the checkbox reflects that the overdue term is already in the query")
        (doseq [o [matching right-type-not-overdue overdue-but-wrong-type]]
          (observation.i/delete! *db* (:observation/id o)))))))

(deftest test-a-material-observations-subject-links-to-its-observations-tab
  (tf/testing "a material observation's Subject cell opens the material's Observations tab"
    (fixtures)
    (fn [{:keys [user material]}]
      (let [observation (create! :resource-type :material
                                 :resource-id (:material/id material)
                                 :user user
                                 :type "general"
                                 :observed-on "2026-01-01")
            sess (app.test/login (:user/email user) password)
            body (fetch sess "/observation/")
            href (str "/material/" (:material/id material) "/observations/")
            row (.selectFirst body (str "[data-observation-id=" (:observation/id observation) "]"))]
        (is (some? row))
        (is (some? (.selectFirst row (str "td:first-child a[href='" href "']"))))
        (observation.i/delete! *db* (:observation/id observation))))))

(deftest test-a-location-observations-subject-links-to-its-observations-tab
  (tf/testing "a location observation's Subject cell opens the location's Observations tab"
    (fixtures)
    (fn [{:keys [user location]}]
      (let [observation (create! :resource-type :location
                                 :resource-id (:location/id location)
                                 :user user
                                 :type "general"
                                 :observed-on "2026-01-01")
            sess (app.test/login (:user/email user) password)
            body (fetch sess "/observation/")
            href (str "/location/" (:location/id location) "/observations/")
            row (.selectFirst body (str "[data-observation-id=" (:observation/id observation) "]"))]
        (is (some? row))
        (is (some? (.selectFirst row (str "a[href='" href "']"))))
        (observation.i/delete! *db* (:observation/id observation))))))

(deftest test-a-readers-subject-link-goes-to-the-subjects-page
  (tf/testing "a reader can't open the Observations tab, so the link goes to the material"
    (assoc-in (fixtures) [[::user.i/factory :key/user] :role] :reader)
    (fn [{:keys [user material]}]
      (let [observation (create! :resource-type :material
                                 :resource-id (:material/id material)
                                 :user user
                                 :type "general"
                                 :observed-on "2026-01-01")
            sess (app.test/login (:user/email user) password)
            body (fetch sess "/observation/")
            row (.selectFirst body (str "[data-observation-id=" (:observation/id observation) "]"))]
        (is (some? (.selectFirst row (str "a[href='/material/" (:material/id material) "/']"))))
        (observation.i/delete! *db* (:observation/id observation))))))

(deftest test-a-bare-word-searches-the-subjects-codes
  (tf/testing "free text matches accession codes, full material codes and locations"
    (fixtures)
    (fn [{:keys [user material accession location]}]
      (let [on-material (create! :resource-type :material
                                 :resource-id (:material/id material)
                                 :user user
                                 :type "general"
                                 :observed-on "2026-01-01"
                                 :note "unmistakablenoteword")
            on-location (create! :resource-type :location
                                 :resource-id (:location/id location)
                                 :user user
                                 :type "general"
                                 :observed-on "2026-01-01")
            material-id (str (:observation/id on-material))
            location-id (str (:observation/id on-location))
            sess (app.test/login (:user/email user) password)
            ids (fn [q] (row-ids (fetch sess "/observation/" "q" q)))
            acc-code (:accession/code accession)
            mat-code (:material/code material)]
        (is (contains? (ids acc-code) material-id) "the accession code")
        (is (contains? (ids (str acc-code "." (subs mat-code 0 1))) material-id)
            "the start of the full code")
        (is (not (contains? (ids mat-code) material-id))
            "the material's own code alone")
        (is (contains? (ids (:location/name location)) location-id) "the location name")
        (is (contains? (ids (:location/code location)) location-id) "the location code")
        (is (empty? (ids "unmistakablenoteword")) "not the note")
        (doseq [o [on-material on-location]]
          (observation.i/delete! *db* (:observation/id o)))))))

(deftest test-the-observer-column-falls-back-to-the-creating-user
  (tf/testing "no observed_by set -- the Observer cell shows who logged it"
    (fixtures)
    (fn [{:keys [user material]}]
      (let [observation (create! :resource-type :material
                                 :resource-id (:material/id material)
                                 :user user
                                 :type "general"
                                 :observed-on "2026-01-01")
            sess (app.test/login (:user/email user) password)
            body (fetch sess "/observation/")
            row (.selectFirst body (str "[data-observation-id=" (:observation/id observation) "]"))]
        (is (.contains (.text row) (:user/email user)))
        (observation.i/delete! *db* (:observation/id observation))))))

(deftest test-the-search-form-submits-one-q
  (tf/testing "the export form's hidden q is not inside the search form"
    (fixtures)
    (fn [{:keys [user]}]
      (let [sess (app.test/login (:user/email user) password)
            body (fetch sess "/observation/" "q" "type:general")
            search-form (.selectFirst body "form[hx-get]")]
        ;; A second q turns the parameter into a vector, which search.i/parse
        ;; can't read, so every search from this page would fail.
        (is (= 1 (count (.select search-form "[name=q]"))))))))

(deftest test-a-followed-up-check-is-not-overdue
  (tf/testing "overdue: leaves out a check a later observation settled; due: still matches it"
    (fixtures)
    (fn [{:keys [user material]}]
      (let [today (LocalDate/now)
            settled (create! :resource-type :material
                             :resource-id (:material/id material)
                             :user user
                             :type "pest"
                             :value "moderate"
                             :observed-on (str (.minusDays today 20))
                             :next-check-on (str (.minusDays today 10)))
            follow-up (create! :resource-type :material
                               :resource-id (:material/id material)
                               :user user
                               :type "pest"
                               :value "none"
                               :observed-on (str (.minusDays today 9)))
            sess (app.test/login (:user/email user) password)
            id (str (:observation/id settled))]
        (is (not (contains? (row-ids (fetch sess "/observation/" "q" (str "overdue:" today))) id)))
        (is (contains? (row-ids (fetch sess "/observation/" "q" (str "due:<=" today))) id))
        (doseq [o [settled follow-up]]
          (observation.i/delete! *db* (:observation/id o)))))))
