(ns sepal.app.routes.media.transform-test
  "Tests for the media transform route.
   
   Note: Full integration tests require S3 and would be expensive.
   These tests focus on the image service functionality."
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [integrant.core :as ig]
            [peridot.core :as peri]
            [sepal.app.test :as app.test]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*app* *db* default-system-fixture]]
            [sepal.media-transform.interface :as media-transform.i]
            [sepal.media.interface :as media.i]
            [sepal.user.interface :as user.i]))

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
