(ns sepal.app.routes.media.transform-test
  "Tests for the media transform route.
   
   Note: Full integration tests require S3 and would be expensive.
   These tests focus on the image service functionality."
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [integrant.core :as ig]
            [peridot.core :as peri]
            [sepal.app.routes.media.transform :as transform]
            [sepal.app.test :as app.test]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*app* *db* default-system-fixture]]
            [sepal.error.interface :as error.i]
            [sepal.media-transform.interface :as media-transform.i]
            [sepal.media.interface :as media.i]
            [sepal.user.interface :as user.i]
            [sepal.validation.interface :as validation.i]
            [zodiac.core :as z])
  (:import [java.awt.image BufferedImage]
           [java.io File]
           [java.nio.file Files]
           [java.nio.file.attribute FileAttribute]
           [javax.imageio ImageIO]))

(use-fixtures :once default-system-fixture)

(deftest test-image-content-type-detection
  (testing "image-content-type? correctly identifies images"
    (is (true? (media-transform.i/image-content-type? "image/jpeg")))
    (is (true? (media-transform.i/image-content-type? "image/png")))
    (is (true? (media-transform.i/image-content-type? "image/gif")))
    (is (false? (media-transform.i/image-content-type? "application/pdf")))
    (is (false? (media-transform.i/image-content-type? "text/plain")))
    (is (false? (media-transform.i/image-content-type? nil)))))

(deftest test-transform-route-requires-auth
  (tf/testing "transform route requires authentication"
    {[::user.i/factory :key/user] {:db *db* :password "testpassword123"}}
    (fn [{:keys [_user]}]
      ;; Unauthenticated request should redirect to login
      (let [{:keys [response]} (-> (peri/session *app*)
                                   (peri/request "/media/1/transform"))]
        (is (#{302 303} (:status response))
            "Unauthenticated request should redirect")))))

(deftest test-transform-route-refuses-foreign-instance-key
  (tf/testing "media whose s3-key belongs to another instance's prefix is refused"
    {[::user.i/factory :key/user] {:db *db* :password "testpassword123"}
     [::media.i/factory :key/media] {:db *db*
                                     :user (ig/ref :key/user)
                                     :media-type "image/jpeg"
                                     :s3-key "elsewhere/deadbeef.jpg"
                                     :s3-bucket "sepal-test-media"}}
    (fn [{:keys [user media]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response]} (peri/request sess (str "/media/" (:media/id media) "/transform"))]
        (is (= 404 (:status response))
            "A key outside this instance's prefix should look like no media at all")))))

(deftest test-transform-params-are-validated
  (testing "the string-keyed query map decodes into sized transform opts"
    (is (= {:w 300 :h 300 :fit "crop" :q 85}
           (validation.i/validate-form-values transform/Params
                                              {"w" "300" "h" "300" "fit" "crop" "q" "85"})))
    (is (= {:w 800}
           (validation.i/validate-form-values transform/Params {"w" "800"})))
    (is (error.i/error? (validation.i/validate-form-values transform/Params {"w" "0"}))
        "zero and negative sizes are nonsense, not something to round-trip to Thumbnailator")
    (is (error.i/error? (validation.i/validate-form-values transform/Params {"w" "abc"})))
    (is (error.i/error? (validation.i/validate-form-values transform/Params {"q" "101"})))))

(deftest test-transform-handler-serves-resized-image
  (tf/testing "a transform request returns a resized image, not the full-size original"
    {[::user.i/factory :key/user] {:db *db* :password "testpassword123"}
     [::media.i/factory :key/media] {:db *db*
                                     :user (ig/ref :key/user)
                                     :media-type "image/png"
                                     :s3-key "media/source.png"
                                     :s3-bucket "sepal-test-media"}}
    (fn [{:keys [media]}]
      (let [src (File/createTempFile "sepal-src-" ".png")
            _ (ImageIO/write (BufferedImage. 600 900 BufferedImage/TYPE_INT_RGB)
                             "png" src)
            cache-dir (str (Files/createTempDirectory "sepal-cache" (into-array FileAttribute [])))
            cache-ds (media-transform.i/init-cache-db! (str (File. cache-dir "cache.db")))
            context {:media-key-prefix "media/"
                     :media-upload-bucket "sepal-test-media"
                     :s3-client nil
                     :media-transform-service {:cache-ds cache-ds
                                               :cache-dir cache-dir}
                     :resource media}
            ;; The download step is S3's; the route's own job starts at the
            ;; downloaded file.
            resp (with-redefs-fn {#'transform/download-from-s3
                                  (fn [_client _bucket _key] src)}
                   (fn []
                     (transform/handler ::z/context context
                                        :query-params {"w" "300"
                                                       "h" "300"
                                                       "fit" "crop"})))]
        (try
          (is (= 200 (:status resp)))
          (let [img (ImageIO/read ^File (:body resp))]
            (is (some? img) "the body is a readable image")
            (is (= 300 (.getWidth img))
                "the served image is the requested width, not the source's 600")
            (is (= 300 (.getHeight img))
                "the served image is the requested height, not the source's 900"))
          (finally
            (.delete src)))))))
