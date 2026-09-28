(ns sepal.app.route-permission-test
  "Every route's permission, checked from the route table itself, so a new
  route is covered without a new test."
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [peridot.core :as peri]
            [reitit.core :as r]
            [reitit.ring]
            [sepal.app.authorization :as authz]
            [sepal.app.server :as server]
            [sepal.app.test :as app.test]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.test.interface :as test.i]
            [sepal.user.interface :as user.i]))

(use-fixtures :once default-system-fixture)

(def ^:private mutating [:post :put :patch :delete])

(def ^:private known-permissions
  (conj (reduce into #{} (vals authz/permissions)) :public))

(defn- route-table []
  (r/routes (reitit.ring/router (server/routes) {:conflicts nil})))

(defn- methods-of
  "The methods a route answers, with the permission each needs. A route with
  :handler answers every method."
  [data]
  (let [methods (if (:handler data)
                  (into [:get] mutating)
                  (filter #(get data %) (into [:get] mutating)))]
    (for [m methods]
      [m (or (get-in data [m :permission]) (:permission data))])))

(deftest every-route-declares-a-known-permission
  (doseq [[path data] (route-table)
          [method permission] (methods-of data)]
    (testing (str (str/upper-case (name method)) " " path)
      (is (contains? known-permissions permission)))))

(defn- groups-with-permission
  "Paths of route groups, anywhere in the tree, whose own data declares
  :permission. A group's data is merged into every route under it."
  ([tree] (groups-with-permission "" tree))
  ([prefix [path & more]]
   (let [[data children] (if (map? (first more)) [(first more) (rest more)] [nil more])
         full (str prefix path)
         children (mapcat #(if (string? (first %)) [%] %) children)]
     (concat (when (and (seq children) (contains? data :permission)) [full])
             (mapcat #(groups-with-permission full %) children)))))

(deftest only-leaf-routes-declare-a-permission
  (is (empty? (groups-with-permission (server/routes)))))

(defn- session-as [role]
  (let [email (str (name role) "-" (random-uuid) "@test.com")]
    (user.i/create! *db* {:email email :password "password123" :role role})
    (let [sess (app.test/login email "password123")
          {:keys [response] :as sess} (peri/request sess "/settings/profile")]
      [sess (test.i/response-anti-forgery-token response)])))

(defn- concrete-path
  "The path with every parameter filled in. The ids name no record: the
  permission check runs before any loader, so it answers first."
  [path]
  (str/replace path #":[a-z-]+" "999999"))

(defn- send-as
  "The token goes in the header, as htmx sends it. peridot puts :params in the
  query string for a DELETE, where the anti-forgery check doesn't look."
  [[sess token] method path]
  (:response (peri/request sess (concrete-path path)
                           :request-method method
                           :headers {"x-csrf-token" token})))

(def ^:private tables
  [:accession :activity :collection :contact :location :material :media
   :media_link :note :observation :propagation :settings :tag :taxon :user])

(defn- row-counts []
  (into {} (for [t tables]
             [t (:n (jdbc/execute-one! *db* [(str "select count(*) as n from " (name t))]
                                       {:builder-fn rs/as-unqualified-maps}))])))

(deftest mutating-requests-need-the-permission
  (let [reader (session-as :reader)
        editor (session-as :editor)
        cases (for [[path data] (route-table)
                    [method permission] (methods-of data)
                    :when (and (some #{method} mutating)
                               (not= :public permission))]
                [path method permission])
        before (row-counts)]
    (is (seq cases))

    (testing "a reader is refused wherever they lack the permission, and nothing is written"
      (doseq [[path method permission] cases
              :when (not (authz/has-permission? :reader permission))]
        (is (= 403 (:status (send-as reader method path)))
            (str (str/upper-case (name method)) " " path)))
      (is (= before (row-counts))))

    (testing "an editor is refused exactly where they lack the permission"
      ;; Anything but 403, a 404 from a loader included, shows the check passed.
      ;; It is also what catches a missing CSRF token, which refuses every POST.
      (doseq [[path method permission] cases]
        (let [status (:status (send-as editor method path))]
          (if (authz/has-permission? :editor permission)
            (is (not= 403 status) (str (str/upper-case (name method)) " " path))
            (is (= 403 status) (str (str/upper-case (name method)) " " path))))))))
