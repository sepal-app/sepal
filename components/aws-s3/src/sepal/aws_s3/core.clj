(ns sepal.aws-s3.core
  (:require [camel-snake-kebab.core :as csk]
            [camel-snake-kebab.extras :as cske]
            [clojure.string :as s])
  (:import [java.net URI]
           [software.amazon.awssdk.auth.credentials AwsBasicCredentials StaticCredentialsProvider]
           [software.amazon.awssdk.services.s3 S3Client]
           [software.amazon.awssdk.services.s3 S3Configuration]
           [software.amazon.awssdk.services.s3.model ListObjectsV2Request]
           [software.amazon.awssdk.services.s3.model PutObjectRequest]
           [software.amazon.awssdk.services.s3.presigner S3Presigner]
           [software.amazon.awssdk.services.s3.presigner.model PutObjectPresignRequest]))

(defn s3-presigner
  ([]
   (s3-presigner nil))
  ([& {:keys [accelerate-mode-enabled
              checksum-validation-enabled
              endpoint-override
              credentials-provider]
       :or {checksum-validation-enabled true}}]
   (let [s3config (cond-> (S3Configuration/builder)
                    accelerate-mode-enabled (.accelerateModeEnabled accelerate-mode-enabled)
                    checksum-validation-enabled (.checksumValidationEnabled checksum-validation-enabled)
                    :always (.build))]
     (cond-> (doto (S3Presigner/builder)
               (.serviceConfiguration s3config))
       credentials-provider  (.credentialsProvider credentials-provider)
       endpoint-override (.endpointOverride (URI. endpoint-override))
       :always (.build)))))

(defn credentials-provider
  [access-key-id secret-access-key]
  (let [credentials  (AwsBasicCredentials/create access-key-id secret-access-key)]
    (StaticCredentialsProvider/create credentials)))

(defn s3-client [& {:keys [credentials-provider endpoint-override region]}]
  (cond-> (S3Client/builder)
    credentials-provider  (.credentialsProvider credentials-provider)
    endpoint-override (.endpointOverride (URI. endpoint-override))
    region (.region region)

    :always (.build)))

(defn presign-put-url
  "Given a `bucket`, key (`k`), `content-type`, and java.time `duration`
  generate a pre-signed url to be used to upload an item to the bucket.
  If no `duration` is provided will default to 8 hours.

  If an md5 is provided it should be base64 encoded.
  "
  [bucket key content-type & {:keys [duration md5 presigner metadata]}]
  (let [builder (cond-> (doto (PutObjectRequest/builder)
                          (.bucket bucket)
                          (.key key)
                          (.contentType (s/lower-case content-type)))
                  md5 (.contentMD5 md5)
                  metadata (.metadata (cske/transform-keys csk/->kebab-case-string metadata)))
        obj-req (.build builder)
        presigned-req (-> (PutObjectPresignRequest/builder)
                          (.signatureDuration duration)
                          (.putObjectRequest obj-req)
                          (.build))]
    (-> (or presigner (s3-presigner))
        (.presignPutObject presigned-req)
        .url
        .toString)))

(defn list-objects [client bucket prefix]
  (let [req (->  (ListObjectsV2Request/builder)
                 (.bucket bucket)
                 (.prefix prefix)
                 (.build))
        resp (-> client
                 (.listObjectsV2 req))]
    ;; TODO: Use datafy on the response
    (mapv #(hash-map :key (.key %)
                     :last-modified (.lastModified %)
                     :size (.size %))
          (.contents resp))))
