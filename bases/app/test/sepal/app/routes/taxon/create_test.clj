(ns sepal.app.routes.taxon.create-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [next.jdbc.sql :as jdbc.sql]
            [peridot.core :as peri]
            [sepal.app.test :as app.test]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.taxon.interface :as taxon.i]
            [sepal.test.interface :as test.i]
            [sepal.user.interface :as user.i])
  (:import [org.jsoup Jsoup]))

(use-fixtures :once default-system-fixture)

(deftest test-create-taxon-validation-errors
  (tf/testing "POST with invalid data returns 422 with OOB error elements"
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :editor}}
    (fn [{:keys [user]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response] :as sess} (-> sess
                                            (peri/request "/taxon/new/"))
            create-token (test.i/response-anti-forgery-token response)
            {:keys [response]} (-> sess
                                   (peri/request "/taxon/new/"
                                                 :request-method :post
                                                 :params {:__anti-forgery-token create-token
                                                          :name ""
                                                          :author ""
                                                          :rank ""
                                                          :parent-id ""}))]
        (is (= 422 (:status response))
            (str "Expected 422, got " (:status response) " with body: " (:body response)))

        (is (= "text/html" (get-in response [:headers "Content-Type"]))
            "Should return text/html content type for HTMX OOB swap")

        (let [body (Jsoup/parse ^String (:body response))]
          (let [oob-elements (.select body "[hx-swap-oob]")]
            (is (pos? (.size oob-elements))
                "Should have elements with hx-swap-oob attribute"))

          (is (some? (.selectFirst body "#name-errors"))
              "Should have error list for name field"))))))

(deftest test-create-taxon-form-has-htmx-attributes
  (tf/testing "Form has HTMX attributes for OOB error swapping"
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :editor}}
    (fn [{:keys [user]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response]} (-> sess
                                   (peri/request "/taxon/new/"))
            body (Jsoup/parse ^String (:body response))
            form (.selectFirst body "form#taxon-form")]
        (is (some? (.attr form "hx-post"))
            "Form should have hx-post attribute")

        (is (= "none" (.attr form "hx-swap"))
            "Form should have hx-swap='none' for OOB error updates")))))

(deftest test-create-taxon-form-has-error-containers
  (tf/testing "Form fields have error containers with correct IDs for OOB targeting"
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :editor}}
    (fn [{:keys [user]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response]} (-> sess
                                   (peri/request "/taxon/new/"))
            body (Jsoup/parse ^String (:body response))]
        (is (some? (.selectFirst body "#name-errors"))
            "Name field should have error container with id name-errors")))))

(deftest test-create-a-cultivar-through-the-form
  (tf/testing "the rank select offers cultivar, grex and group, and posting one saves"
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :editor}}
    (fn [{:keys [user]}]
      ;; A successful POST logs an activity row against this ephemeral user.
      ;; activity.created_by deliberately has no ON DELETE CASCADE: activity is
      ;; an audit trail, and production never hard-deletes a user —
      ;; user.i/archive! sets status to :archived instead. The only hard
      ;; delete in the codebase is the test factory's own teardown
      ;; (user/core.clj:153), so this cleanup is a concession to that
      ;; fixture, not a workaround for a missing constraint. Same as
      ;; sepal.taxon.interface.activity-test.
      (try
        (let [sess (app.test/login (:user/email user) "testpassword123")
              {:keys [response] :as sess} (-> sess (peri/request "/taxon/new/"))
              token (test.i/response-anti-forgery-token response)
              body (Jsoup/parse ^String (:body response))
              options (->> (.select body "select[name=rank] option")
                           (map #(.val %))
                           (remove str/blank?)
                           set)]
          (is (= 36 (count options))
              (str "expected 36 rank options, got " (count options)))
          (is (every? options ["cultivar" "grex" "group" "aggregate"])
              "the ICNCP categories and aggregate must be selectable")

          (let [{:keys [response]} (-> sess
                                       (peri/request "/taxon/new/"
                                                     :request-method :post
                                                     :params {:__anti-forgery-token token
                                                              :name "Acer palmatum 'Sango-kaku'"
                                                              :author ""
                                                              :rank "cultivar"
                                                              :parent-id ""}))]
            (is (contains? #{200 204 302} (:status response))
                (str "expected a redirect or success, got " (:status response)
                     " with body: " (:body response)))

            (let [saved (jdbc.sql/find-by-keys *db* :taxon {:name "Acer palmatum 'Sango-kaku'"})]
              (is (= 1 (count saved)) "the POST persisted exactly one taxon")
              (is (= "cultivar" (:taxon/rank (first saved)))
                  "the rank landed as cultivar, not silently dropped or coerced"))))
        (finally
          (jdbc.sql/delete! *db* :activity {:created_by (:user/id user)}))))))

(deftest test-vernacular-name-columns-are-headed
  (tf/testing "the rows repeat, so the columns are headed once instead of every
               input carrying a label. They had only an aria-label, which left
               two unexplained boxes on the page."
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :editor}}
    (fn [{:keys [user]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response]} (-> sess (peri/request "/taxon/new/"))
            body (Jsoup/parse ^String (:body response))
            headers (->> (.select body ".spl-fieldset .spl-label")
                         (mapv #(.text %)))]
        (is (= ["Name" "Language"] headers))
        (testing "header and rows declare the same grid, which is what lines
                  them up — two grids with different templates would not"
          (let [templates (->> (.select body ".spl-fieldset [class*=grid-cols-]")
                               (map #(.attr % "class"))
                               (map #(re-find #"grid-cols-\[[^\]]*\]" %))
                               set)]
            (is (= 1 (count templates))
                (str "expected one grid template, got " templates))))))))

(deftest test-rank-guess-endpoint
  (tf/testing "plain text, and blank when the name implies nothing — a blank
               leaves the field alone rather than clearing it"
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :editor}}
    (fn [{:keys [user]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            guess (fn [n]
                    (-> sess
                        (peri/request "/taxon/rank-guess/" :params {:name n})
                        :response :body))]
        (is (= "family" (guess "Rosaceae")))
        (is (= "genus" (guess "Acer")))
        (is (= "species" (guess "Acer palmatum")))
        (is (= "variety" (guess "Acer palmatum var. dissectum")))
        (is (= "cultivar" (guess "Rosa 'Peace'")))
        (is (= "" (guess "Acer palmatum dissectum"))
            "three bare words name no rank it can be sure of")
        (is (= "" (guess "")))))))

(deftest test-only-the-create-form-guesses-the-rank
  (tf/testing "the edit form must not re-rank a taxon you are only renaming"
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :editor}
     [::taxon.i/factory :key/taxon] {:db *db*}}
    (fn [{:keys [user taxon]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            name-input (fn [path]
                         (-> sess
                             (peri/request path)
                             :response :body
                             (as-> b (Jsoup/parse ^String b))
                             (.selectFirst "input#name")))]
        (is (= "/taxon/rank-guess/" (.attr (name-input "/taxon/new/") "hx-get"))
            "the create form asks")
        (is (str/blank? (.attr (name-input (str "/taxon/" (:taxon/id taxon) "/name/"))
                               "hx-get"))
            "the edit form does not")))))

(deftest test-parent-suggestion-endpoint
  (tf/testing "it answers only when the name says what its parent is called,
               exactly one taxon has that name, and that taxon's rank is the
               one the parent's own name implies. Hanging a taxon off the
               wrong parent is worse than leaving the field empty."
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :editor}
     [::taxon.i/factory :key/genus] {:db *db* :name "Zzyzxia" :rank :genus}}
    (fn [{:keys [user genus]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            suggest (fn [n]
                      (-> sess
                          (peri/request "/taxon/parent-suggestion/" :params {:name n})
                          :response :body))]
        (is (str/includes? (suggest "Zzyzxia testica") (str (:taxon/id genus)))
            "a binomial resolves to its genus")
        (is (str/includes? (suggest "Zzyzxia 'Cultivarname'") (str (:taxon/id genus)))
            "a cultivar of a genus resolves to that genus")
        (is (= "" (suggest "Zzyzxia"))
            "a one-word name says nothing about what is above it")
        (is (= "" (suggest "Nosuchgenus testica"))
            "no taxon by that name")
        (is (= "" (suggest "")))))))

(deftest test-parent-suggestion-matches-either-hybrid-marker
  (tf/testing "WFO stores × and a keyboard types x, so a cultivar of a
               nothospecies must find its parent whichever was typed"
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :editor}
     [::taxon.i/factory :key/notho] {:db *db*
                                     :name "Zzyzxid × testica"
                                     :rank :species}}
    (fn [{:keys [user notho]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            suggest (fn [n]
                      (-> sess
                          (peri/request "/taxon/parent-suggestion/" :params {:name n})
                          :response :body))
            id (str (:taxon/id notho))]
        (is (str/includes? (suggest "Zzyzxid × testica 'Cultivarname'") id)
            "typed with the multiplication sign")
        (is (str/includes? (suggest "Zzyzxid x testica 'Cultivarname'") id)
            "typed with a letter x, which is what a keyboard offers")))))

(deftest test-parent-suggestion-needs-the-rank-to-fit
  (tf/testing "a taxon named like a genus but filed as something else is not
               the parent the name describes"
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :editor}
     [::taxon.i/factory :key/mis-ranked] {:db *db* :name "Zzyzxib" :rank :family}}
    (fn [{:keys [user]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")]
        (is (= "" (-> sess
                      (peri/request "/taxon/parent-suggestion/"
                                    :params {:name "Zzyzxib testica"})
                      :response :body))
            "`Zzyzxib` implies a genus, and this one is a family")))))

(deftest test-parent-suggestion-needs-exactly-one-match
  (tf/testing "two taxa of the same name is ambiguous, so it answers nothing"
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :editor}
     [::taxon.i/factory :key/one] {:db *db* :name "Zzyzxic" :rank :genus}
     [::taxon.i/factory :key/two] {:db *db* :name "Zzyzxic" :rank :genus}}
    (fn [{:keys [user]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")]
        (is (= "" (-> sess
                      (peri/request "/taxon/parent-suggestion/"
                                    :params {:name "Zzyzxic testica"})
                      :response :body)))))))

(deftest test-only-the-create-form-suggests-a-parent
  (tf/testing "the edit form must not re-parent a taxon you are only renaming"
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :editor}
     [::taxon.i/factory :key/taxon] {:db *db*}}
    (fn [{:keys [user taxon]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            ;; The hx-* attributes sit on a listener element beside the
            ;; select, not on the select: htmx marks its requesting element
            ;; with htmx-request as a request runs, and a picker that reacted
            ;; to its own class list changing closed its open dropdown.
            listener (fn [path]
                       (-> sess
                           (peri/request path)
                           :response :body
                           (as-> b (Jsoup/parse ^String b))
                           (.selectFirst "#parent-suggestion")))]
        (is (= "/taxon/parent-suggestion/"
               (.attr (listener "/taxon/new/") "hx-get"))
            "the create form asks")
        (is (nil? (listener (str "/taxon/" (:taxon/id taxon) "/name/")))
            "the edit form does not")))))

(deftest test-parent-id-prefills-the-form
  (tf/testing "a taxon's \"Add a child taxon\" names the parent. The select is
               searched client-side, so without the option rendered here the
               field arrives empty and the link saves nothing."
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :editor}
     [::taxon.i/factory :key/parent] {:db *db*}}
    (fn [{:keys [user parent]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response]} (-> sess
                                   (peri/request "/taxon/new/"
                                                 :params {:parent-id (str (:taxon/id parent))}))
            body (Jsoup/parse ^String (:body response))
            picker (.selectFirst body "sepal-combobox#parent-id")]
        (is (some? picker))
        (is (= (str (:taxon/id parent)) (.attr picker "data-value")))
        (is (= (:taxon/name parent) (.attr picker "data-text"))
            "the name rides on the element, so the field shows it on arrival
             without asking the server for a record it was just given")))))

(deftest test-an-unknown-parent-id-is-ignored
  (tf/testing "a stale link should still render a usable empty form"
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :editor}}
    (fn [{:keys [user]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response]} (-> sess
                                   (peri/request "/taxon/new/"
                                                 :params {:parent-id "0"}))
            body (Jsoup/parse ^String (:body response))
            picker (.selectFirst body "sepal-combobox#parent-id")]
        (is (= 200 (:status response)))
        (is (some? picker))
        (is (str/blank? (.attr picker "data-value")) "no parent chosen")))))
