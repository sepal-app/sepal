(ns sepal.aws-s3.interface-test
  (:require [clojure.test :refer :all]
            [integrant.core :as ig]
            [sepal.aws-s3.core :as core]
            [sepal.aws-s3.interface :as aws-s3.i])
  (:import [java.time Duration]
           [software.amazon.awssdk.services.s3 S3Client]))

(def ^:private test-creds
  (core/credentials-provider "test-key" "test-secret"))

(deftest s3-client-builds-with-a-region
  (let [client (ig/init-key ::aws-s3.i/s3-client
                            {:region "auto"
                             :credentials-provider test-creds})]
    (is (instance? S3Client client))
    (.close client)))

(deftest s3-client-builds-without-a-region
  ;; An endpoint override means an S3-compatible store, where the region only
  ;; signs the request. Building must not need AWS_REGION or an ~/.aws/config
  ;; on the host, neither of which a CI runner has.
  (let [client (ig/init-key ::aws-s3.i/s3-client
                            {:endpoint-override "https://example.r2.cloudflarestorage.com"
                             :credentials-provider test-creds})]
    (is (instance? S3Client client))
    (.close client)))

(deftest presigner-builds-without-a-region
  (let [presigner (ig/init-key ::aws-s3.i/s3-presigner
                               {:endpoint-override "https://example.r2.cloudflarestorage.com"
                                :credentials-provider test-creds})]
    (is (some? presigner))
    (.close presigner)))

(deftest presigner-signs-with-the-given-region
  ;; No network: presigning is local computation. The region appears in the
  ;; URL's credential scope, so the URL proves the opt reached the builder.
  (let [presigner (ig/init-key ::aws-s3.i/s3-presigner
                               {:region "auto"
                                :credentials-provider test-creds})
        url (aws-s3.i/presign-put-url "some-bucket" "some-key" "image/jpeg"
                                      :presigner presigner
                                      :duration (Duration/ofMinutes 5))]
    (is (re-find #"%2Fauto%2Fs3%2Faws4_request" url))))
