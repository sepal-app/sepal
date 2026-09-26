(ns sepal.app.routes.media.transform-test
  "Tests for the media transform route.
   
   Note: Full integration tests require S3 and would be expensive.
   These tests focus on the image service functionality."
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [integrant.core :as ig]
            [peridot.core :as peri]
            [sepal.app.routes.media.transform :as transform]
            [sepal.app.test :as app.test]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*app* *db* default-system-fixture]]
            [sepal.aws-s3.interface :as s3.i]
            [sepal.error.interface :as error.i]
            [sepal.media-transform.interface :as media-transform.i]
            [sepal.media.interface :as media.i]
            [sepal.user.interface :as user.i]
            [sepal.validation.interface :as validation.i]
            [zodiac.core :as z])
  (:import [java.awt.image BufferedImage]
           [java.io ByteArrayInputStream File]
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

(defn- transform-context [media]
  (let [cache-dir (str (Files/createTempDirectory "sepal-cache" (into-array FileAttribute [])))]
    {:media-key-prefix "media/"
     :media-upload-bucket "sepal-test-media"
     :s3-client nil
     :media-transform-service {:cache-ds (media-transform.i/init-cache-db!
                                           (str (File. cache-dir "cache.db")))
                               :cache-dir cache-dir
                               :max-cache-size-bytes (* 10 1024 1024)}
     :resource media}))

(deftest test-a-cached-transform-does-not-download-the-original
  (tf/testing "the second request for the same size is served from the cache
  without going to S3, and the browser may cache it"
    {[::user.i/factory :key/user] {:db *db* :password "testpassword123"}
     [::media.i/factory :key/media] {:db *db*
                                     :user (ig/ref :key/user)
                                     :media-type "image/png"
                                     :s3-key "media/cached.png"
                                     :s3-bucket "sepal-test-media"}}
    (fn [{:keys [media]}]
      (let [downloads (atom 0)
            context (transform-context media)
            request #(transform/handler ::z/context context
                                        :query-params {"w" "100" "h" "100"})]
        (with-redefs-fn {#'transform/download-from-s3
                         (fn [_client _bucket _key]
                           (swap! downloads inc)
                           (let [f (File/createTempFile "sepal-src-" ".png")]
                             (ImageIO/write (BufferedImage. 400 400 BufferedImage/TYPE_INT_RGB)
                                            "png" f)
                             f))}
          (fn []
            (let [first-resp (request)
                  second-resp (request)]
              (is (= 200 (:status first-resp) (:status second-resp)))
              (is (= 1 @downloads) "only the miss downloads the original")
              (is (str/includes? (get-in second-resp [:headers "Cache-Control"]) "max-age")))))))))

(deftest test-an-original-is-streamed-from-s3
  (tf/testing "a request with no transform streams the original rather than
  copying it to a temp file"
    {[::user.i/factory :key/user] {:db *db* :password "testpassword123"}
     [::media.i/factory :key/media] {:db *db*
                                     :user (ig/ref :key/user)
                                     :media-type "image/png"
                                     :s3-key "media/original.png"
                                     :s3-bucket "sepal-test-media"}}
    (fn [{:keys [media]}]
      (let [bytes (.getBytes "not really a png")
            resp (with-redefs-fn {#'s3.i/get-object-stream
                                  (fn [_client _bucket _key]
                                    {:stream (ByteArrayInputStream. bytes)
                                     :content-length (alength bytes)})
                                  #'transform/download-from-s3
                                  (fn [& _] (throw (ex-info "no temp copy" {})))}
                   (fn []
                     (transform/handler ::z/context (transform-context media)
                                        :query-params {"dl" "original.png"})))]
        (is (= 200 (:status resp)))
        (is (= "image/png" (get-in resp [:headers "Content-Type"])))
        (is (= (str (alength bytes)) (get-in resp [:headers "Content-Length"])))
        (is (= "not really a png" (slurp (:body resp))))))))
