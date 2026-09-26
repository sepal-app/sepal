(ns sepal.app.routes.media.detail.link-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is use-fixtures]]
            [integrant.core :as ig]
            [next.jdbc.sql :as jdbc.sql]
            [peridot.core :as peri]
            [sepal.activity.interface :as activity.i]
            [sepal.app.test :as app.test]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.media.interface :as media.i]
            [sepal.media.interface.activity :as media.activity]
            [sepal.taxon.interface :as taxon.i]
            [sepal.test.interface :as test.i]
            [sepal.user.interface :as user.i]))

(use-fixtures :once default-system-fixture)

(def ^:private password "testpassword123")

(deftest test-linking-and-unlinking-are-recorded
  (tf/testing "each writes an event naming the taxon, and both reach the feed"
    {[::user.i/factory :key/user] {:db *db* :password password :role :editor}
     [::taxon.i/factory :key/taxon] {:db *db* :name "Linkia rubra"}
     [::media.i/factory :key/media] {:db *db*
                                     :user (ig/ref :key/user)
                                     :media-type "image/jpeg"}}
    (fn [{:keys [user taxon media]}]
      (let [sess (app.test/login (:user/email user) password)
            url (format "/media/%s/link/" (:media/id media))
            {:keys [response] :as sess} (peri/request sess url)
            token (test.i/response-anti-forgery-token response)
            events #(->> (activity.i/get-by-resource *db*
                                                     :resource-type :media
                                                     :resource-id (:media/id media))
                         (map (juxt :activity/type (comp :link-text :activity/data)))
                         set)]
        (try
          (peri/request sess url
                        :request-method :post
                        :params {:__anti-forgery-token token
                                 :resource-type "taxon"
                                 :resource-id (str (:taxon/id taxon))})
          (is (= "taxon" (:media-link/resource-type (media.i/get-link *db* (:media/id media)))))
          (-> sess
              (peri/header "X-CSRF-Token" token)
              (peri/request url :request-method :delete))
          (is (nil? (media.i/get-link *db* (:media/id media))))
          (is (= #{[media.activity/linked "Linkia rubra"]
                   [media.activity/unlinked "Linkia rubra"]}
                 (events)))
          (let [feed (-> (peri/request sess "/activity") :response :body)]
            (is (str/includes? feed "linked to Linkia rubra"))
            (is (str/includes? feed "unlinked from Linkia rubra")))
          (finally
            (jdbc.sql/delete! *db* :activity {:created_by (:user/id user)})))))))
