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
            headers (->> (.select body "[data-section=vernacular-names] .spl-label")
                         (mapv #(.text %)))]
        (is (= ["Name" "Language"] headers))
        (testing "header and rows declare the same grid, which is what lines
                  them up — two grids with different templates would not"
          (let [templates (->> (.select body "[data-section=vernacular-names] [class*=grid-cols-]")
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

(deftest test-creating-a-hybrid-records-what-it-was-crossed-from
  (tf/testing "the cross posts with the taxon and lands as parentage rows"
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :editor}}
    (fn [{:keys [user]}]
      (try
        (let [seed (taxon.i/create! *db* {:name "Acer rubrum" :rank :species})
              pollen (taxon.i/create! *db* {:name "Acer saccharinum" :rank :species})
              sess (app.test/login (:user/email user) "testpassword123")
              {:keys [response] :as sess} (-> sess (peri/request "/taxon/new/"))
              token (test.i/response-anti-forgery-token response)
              {:keys [response]} (-> sess
                                     (peri/request "/taxon/new/"
                                                   :request-method :post
                                                   :params {:name "Acer × freemanii"
                                                            :author ""
                                                            :rank "species"
                                                            :parentage-parent-0 (str (:taxon/id seed))
                                                            :parentage-role-0 "seed"
                                                            :parentage-parent-1 (str (:taxon/id pollen))
                                                            :parentage-role-1 "pollen"
                                                            :parentage-parent-2 ""
                                                            :parentage-role-2 ""
                                                            :__anti-forgery-token token}))]
          (is (= 200 (:status response))
              (str "Expected 200, got " (:status response) " with body: " (:body response)))
          (let [redirect (get-in response [:headers "HX-Redirect"])
                id (parse-long (last (remove empty? (str/split redirect #"/"))))
                rows (taxon.i/list-parentage *db* id)]
            (is (= ["Acer rubrum" "Acer saccharinum"] (mapv :parent/name rows)))
            (is (= [:seed :pollen] (mapv :parentage/role rows)))
            (testing "and the empty third slot recorded nothing"
              (is (= 2 (count rows))))
            (testing "while parent_id is untouched -- containment is not the cross"
              (is (nil? (:taxon/parent-id (taxon.i/get-by-id *db* id)))))))
        (finally
          ;; taxon_parentage.created_by references "user", and the user
          ;; factory's teardown hard-deletes -- the only hard delete in the
          ;; codebase. Clear the rows or the foreign key refuses it.
          (jdbc.sql/delete! *db* :taxon_parentage {:created_by (:user/id user)})
          (jdbc.sql/delete! *db* :activity {:created_by (:user/id user)}))))))

(deftest test-the-parentage-section-is-only-offered-for-a-hybrid
  (tf/testing "a non-hybrid name has no cross to record"
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :editor}}
    (fn [{:keys [user]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response]} (-> sess (peri/request "/taxon/new/"))
            body (Jsoup/parse ^String (:body response))
            section (.selectFirst body "[data-section=parentage]")]
        (is (some? section) "the section is rendered")
        (testing "closed until the name carries a hybrid marker, tracked off the
                  Name field rather than waiting for a save. Closed rather than
                  hidden, because a section that vanishes makes the form's shape
                  vary; and closed rather than disabled, because a hybrid whose
                  name was typed without the marker must still be able to record
                  its cross."
          (is (= "details" (.tagName section)))
          (is (not (.hasAttr section "open")) "a plain name starts it closed")
          (is (= "" (.attr section "x-show")) "not hidden")
          (is (= "" (.attr section "x-bind:disabled")) "not disabled")
          (is (str/includes? (.attr section "x-init") "getElementById('name')"))
          (is (str/includes? (.attr section "x-init") "d.open = true")
              "Alpine only opens it -- removing a marker must not shut it"))
        (testing "and it offers two slots, because a cross usually has two parents"
          (is (some? (.selectFirst body "[name=parentage-parent-0]")))
          (is (some? (.selectFirst body "[name=parentage-parent-1]")))
          (is (nil? (.selectFirst body "[name=parentage-parent-2]"))))))))

(deftest test-a-cross-of-four-genera-can-be-entered-in-one-pass
  ;; × Potinara is Brassavola × Cattleya × Laelia × Sophronitis, and that case
  ;; is why parentage is a table rather than two columns. A form that grew by
  ;; one slot per save would make it the most tedious thing to record.
  (tf/testing "Add parent fetches another slot, indexed past the last"
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :editor}}
    (fn [{:keys [user]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response]} (-> sess (peri/request "/taxon/parentage-row/?index=2"))
            body (Jsoup/parse ^String (:body response))]
        (is (= 200 (:status response)))
        (testing "the slot carries the index it was asked for, so its ids stay
                  distinct from the rows already on the page"
          (is (some? (.selectFirst body "[name=parentage-parent-2]")))
          (is (some? (.selectFirst body "[name=parentage-role-2]")))
          (is (nil? (.selectFirst body "[name=parentage-parent-0]"))))
        (testing "and the button comes back out of band holding the next index,
                  because the server is the only thing that knows which index
                  it just handed out"
          (let [button (.selectFirst body "#parentage-add")]
            (is (some? button))
            (is (= "true" (.attr button "hx-swap-oob")))
            (is (str/includes? (.attr button "hx-get") "index=3"))))))))

(deftest test-the-form-starts-with-two-slots-and-an-add-button
  (tf/testing "a cross usually has two parents"
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :editor}}
    (fn [{:keys [user]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response]} (-> sess (peri/request "/taxon/new/"))
            body (Jsoup/parse ^String (:body response))]
        (is (some? (.selectFirst body "[name=parentage-parent-0]")))
        (is (some? (.selectFirst body "[name=parentage-parent-1]")))
        (is (nil? (.selectFirst body "[name=parentage-parent-2]")))
        (testing "with a way to reach the third"
          (let [button (.selectFirst body "#parentage-add")]
            (is (some? button))
            (is (str/includes? (.attr button "hx-get") "index=2"))
            (is (= "#parentage-rows" (.attr button "hx-target")))
            (is (= "beforeend" (.attr button "hx-swap")))))))))

(deftest test-the-hybrid-section-starts-open-for-a-name-that-has-a-marker
  ;; Editing a hybrid should not require opening the section that holds the
  ;; thing you came to edit.
  (tf/testing "a taxon already named as a cross"
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :editor}}
    (fn [{:keys [user]}]
      (let [taxon (taxon.i/create! *db* {:name "Acer × freemanii" :rank :species})
            sess (app.test/login (:user/email user) "testpassword123")
            ;; /name/ is the tab holding the form; /taxon/:id/ is the record page.
            {:keys [response]} (-> sess (peri/request (str "/taxon/" (:taxon/id taxon) "/name/")))
            section (.selectFirst (Jsoup/parse ^String (:body response))
                                  "[data-section=parentage]")]
        (is (some? section))
        (is (.hasAttr section "open"))))))
