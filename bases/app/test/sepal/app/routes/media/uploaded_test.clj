(ns sepal.app.routes.media.uploaded-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is use-fixtures]]
            [next.jdbc.sql :as jdbc.sql]
            [peridot.core :as peri]
            [sepal.activity.interface :as activity.i]
            [sepal.app.test :as app.test]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.media.interface.activity :as media.activity]
            [sepal.taxon.interface :as taxon.i]
            [sepal.test.interface :as test.i]
            [sepal.user.interface :as user.i]))

(use-fixtures :once default-system-fixture)

(deftest test-an-upload-from-a-record-records-the-link
  (tf/testing "uploading from a taxon's Media tab records one upload that names the taxon"
    {[::user.i/factory :key/user] {:db *db* :password "testpassword123" :role :editor}
     [::taxon.i/factory :key/taxon] {:db *db* :name "Uploadia"}}
    (fn [{:keys [user taxon]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response] :as sess} (peri/request sess "/settings/profile")
            token (test.i/response-anti-forgery-token response)
            {:keys [response]} (peri/request sess "/media/uploaded"
                                             :request-method :post
                                             :params {:__anti-forgery-token token
                                                      :filename "pod.jpg"
                                                      :contentType "image/jpeg"
                                                      :linkResourceType "taxon"
                                                      :linkResourceId (str (:taxon/id taxon))
                                                      :s3Bucket "b"
                                                      :s3Key "media/pod.jpg"
                                                      :size "10"})
            media-id (some-> (jdbc.sql/find-by-keys *db* :media {:s3_key "media/pod.jpg"}) first :media/id)]
        (try
          (is (= 200 (:status response)))
          (is (= [[media.activity/created "Uploadia"]]
                 (->> (activity.i/get-by-resource *db* :resource-type :media :resource-id media-id)
                      (map (juxt :activity/type (comp :link-text :activity/data)))))
              "one event for the upload, naming the record it was linked to")
          (let [feed (-> (peri/request sess "/activity") :response :body)]
            (is (str/includes? feed "uploaded a media item"))
            (is (str/includes? feed "linked to Uploadia")))
          (finally
            (jdbc.sql/delete! *db* :activity {:created_by (:user/id user)})
            (jdbc.sql/delete! *db* :media {:id media-id})))))))
