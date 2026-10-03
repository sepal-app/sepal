(ns sepal.app.routes.media.uploaded-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is use-fixtures]]
            [integrant.core :as ig]
            [next.jdbc.sql :as jdbc.sql]
            [peridot.core :as peri]
            [sepal.activity.interface :as activity.i]
            [sepal.app.routes.media.uploaded :as uploaded]
            [sepal.app.test :as app.test]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.aws-s3.interface :as aws-s3.i]
            [sepal.media.interface :as media.i]
            [sepal.media.interface.activity :as media.activity]
            [sepal.taxon.interface :as taxon.i]
            [sepal.test.interface :as test.i]
            [sepal.user.interface :as user.i]
            [zodiac.core :as z]))

(use-fixtures :once default-system-fixture)

(def ^:private bucket "sepal-test-media")

(defn- upload!
  "Post `form-params` to the handler as `viewer`, with S3 holding a 10-byte JPEG
  at media/pod.jpg and nothing else."
  [viewer form-params & {:keys [context]}]
  (with-redefs [aws-s3.i/head-object (fn [_client b k]
                                       (when (and (= b bucket) (= k "media/pod.jpg"))
                                         {:content-length 10
                                          :content-type "image/jpeg"}))
                z/url-for (constantly "/media/1")]
    (uploaded/handler ::z/context (or context
                                      {:db *db*
                                       :s3-client ::s3-client
                                       :media-upload-bucket bucket
                                       :media-key-prefix "media/"})
                      :form-params form-params
                      :viewer viewer)))

(defn- media-at [s3-key]
  (first (jdbc.sql/find-by-keys *db* :media {:s3_key s3-key})))

(deftest test-an-upload-from-a-record-records-the-link
  (tf/testing "uploading from a taxon's Media tab records one upload that names the taxon"
    {[::user.i/factory :key/user] {:db *db* :password "testpassword123" :role :editor}
     [::taxon.i/factory :key/taxon] {:db *db* :name "Uploadia"}}
    (fn [{:keys [user taxon]}]
      (let [response (upload! user {"filename" "pod.jpg"
                                    "s3Key" "media/pod.jpg"
                                    "linkResourceType" "taxon"
                                    "linkResourceId" (str (:taxon/id taxon))})
            media-id (:media/id (media-at "media/pod.jpg"))
            sess (app.test/login (:user/email user) "testpassword123")]
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

(deftest test-an-upload-is-described-by-s3
  (tf/testing "the bucket, size and type come from S3, not from the browser"
    {[::user.i/factory :key/user] {:db *db* :role :editor}}
    (fn [{:keys [user]}]
      (let [response (upload! user {"filename" "pod.jpg" "s3Key" "media/pod.jpg"})
            media (media-at "media/pod.jpg")]
        (try
          (is (= 200 (:status response)))
          (is (= {:media/s3-bucket bucket
                  :media/size-in-bytes 10
                  :media/media-type "image/jpeg"
                  :media/title "pod.jpg"}
                 (select-keys media [:media/s3-bucket :media/size-in-bytes
                                     :media/media-type :media/title])))
          (finally
            (jdbc.sql/delete! *db* :activity {:created_by (:user/id user)})
            (jdbc.sql/delete! *db* :media {:id (:media/id media)})))))))

(deftest test-an-upload-is-refused-without-an-object-of-its-own
  (tf/testing "a key outside the garden's prefix, or one S3 holds nothing at, records nothing"
    {[::user.i/factory :key/user] {:db *db* :role :editor}}
    (fn [{:keys [user]}]
      (doseq [s3-key ["other/pod.jpg" "media/../other/pod.jpg" "media/missing.jpg"]]
        (is (= 422 (:status (upload! user {"filename" "pod.jpg" "s3Key" s3-key})))
            s3-key)
        (is (nil? (media-at s3-key)) s3-key)))))

(deftest test-an-upload-is-refused-on-a-key-already-recorded
  (tf/testing "a second media item on one object is refused, so deleting one cannot orphan the other"
    {[::user.i/factory :key/user] {:db *db* :role :editor}
     [::media.i/factory :key/media] {:db *db*
                                     :user (ig/ref :key/user)
                                     :s3-bucket bucket
                                     :s3-key "media/pod.jpg"}}
    (fn [{:keys [user media]}]
      (is (= 422 (:status (upload! user {"filename" "pod.jpg" "s3Key" "media/pod.jpg"}))))
      (is (= [(:media/id media)]
             (map :media/id (jdbc.sql/find-by-keys *db* :media {:s3_key "media/pod.jpg"})))))))

(deftest test-an-upload-is-refused-when-s3-fails
  (tf/testing "an S3 error is answered as a failed upload, not a 500"
    {[::user.i/factory :key/user] {:db *db* :role :editor}}
    (fn [{:keys [user]}]
      (let [response (with-redefs [aws-s3.i/head-object (fn [& _]
                                                          (throw (ex-info "S3 is unreachable" {})))]
                       (uploaded/handler ::z/context {:db *db*
                                                      :s3-client ::s3-client
                                                      :media-upload-bucket bucket
                                                      :media-key-prefix "media/"}
                                         :form-params {"filename" "pod.jpg" "s3Key" "media/pod.jpg"}
                                         :viewer user))]
        (is (= 422 (:status response)))
        (is (nil? (media-at "media/pod.jpg")))))))

(deftest test-recording-is-refused-when-uploads-are-off
  (tf/testing "with no S3 client the route answers 404 rather than recording"
    {[::user.i/factory :key/user] {:db *db* :password "testpassword123" :role :editor}}
    (fn [{:keys [user]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response] :as sess} (peri/request sess "/settings/profile")
            token (test.i/response-anti-forgery-token response)
            {:keys [response]} (peri/request sess "/media/uploaded"
                                             :request-method :post
                                             :headers {"x-csrf-token" token}
                                             :params {:filename "pod.jpg"
                                                      :s3Key "media/pod.jpg"})]
        (is (= 404 (:status response)))
        (is (nil? (media-at "media/pod.jpg")))))))
