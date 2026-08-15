(ns sepal.app.routes.media.keys-test
  (:require [clojure.test :refer [deftest is testing]]
            [sepal.app.routes.media.keys :as media.keys]))

(def ^:private context
  {:media-key-prefix "brooklyn/" :media-upload-bucket "sepal-media"})

(deftest test-own-key?
  (testing "a key under this instance's prefix in the right bucket is accepted"
    (is (media.keys/own-key? context
                             {:media/s3-key "brooklyn/deadbeef.jpg"
                              :media/s3-bucket "sepal-media"})))

  (testing "another instance's prefix is refused"
    (is (not (media.keys/own-key? context
                                  {:media/s3-key "queens/deadbeef.jpg"
                                   :media/s3-bucket "sepal-media"}))))

  (testing "a prefix that merely starts the same is refused"
    (is (not (media.keys/own-key? context
                                  {:media/s3-key "brooklynheights/deadbeef.jpg"
                                   :media/s3-bucket "sepal-media"}))))

  (testing "another bucket is refused even under the right prefix"
    (is (not (media.keys/own-key? context
                                  {:media/s3-key "brooklyn/deadbeef.jpg"
                                   :media/s3-bucket "someone-elses-bucket"}))))

  (testing "a missing key or bucket is refused"
    (is (not (media.keys/own-key? context {:media/s3-key nil
                                           :media/s3-bucket "sepal-media"})))
    (is (not (media.keys/own-key? context {:media/s3-key "brooklyn/x.jpg"
                                           :media/s3-bucket nil}))))

  (testing "a path traversal attempt is refused"
    (is (not (media.keys/own-key? context
                                  {:media/s3-key "brooklyn/../queens/x.jpg"
                                   :media/s3-bucket "sepal-media"})))))
