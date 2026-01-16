(ns sepal.app.routes.media.transform-test
  "Tests for the media transform route.
   
   Note: Full integration tests require S3 and would be expensive.
   These tests focus on the image service functionality."
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [peridot.core :as peri]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*app* *db* default-system-fixture]]
            [sepal.media-transform.interface :as media-transform.i]
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
