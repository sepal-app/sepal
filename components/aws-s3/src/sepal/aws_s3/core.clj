(ns sepal.aws-s3.core
  (:require [camel-snake-kebab.core :as csk]
            [camel-snake-kebab.extras :as cske]
            [clojure.string :as s])
  (:import [java.io File]
           [java.net URI]
           [java.nio.file Path]
           [software.amazon.awssdk.auth.credentials AwsBasicCredentials StaticCredentialsProvider]
           [software.amazon.awssdk.core.exception SdkClientException]
           [software.amazon.awssdk.regions Region]
           [software.amazon.awssdk.regions.providers DefaultAwsRegionProviderChain]
           [software.amazon.awssdk.services.s3 S3Client]
           [software.amazon.awssdk.services.s3 S3Configuration]
           [software.amazon.awssdk.services.s3.model
            GetObjectRequest ListObjectsV2Request PutObjectRequest DeleteObjectRequest]
           [software.amazon.awssdk.services.s3.presigner S3Presigner]
           [software.amazon.awssdk.services.s3.presigner.model PutObjectPresignRequest]))

(defn- resolve-region
  "The `Region` to build with, or nil to leave the choice to the builder.

  An explicit `region` wins. Otherwise the SDK's default chain (AWS_REGION,
  `~/.aws/config`, EC2 metadata) is consulted here rather than inside the
  builder, so that an exhausted chain can be told from a resolved one.

  An exhausted chain with an `endpoint-override` set means an S3-compatible
  store, where the region only signs the request and any valid one will do.
  Without an override the region selects the AWS endpoint, so return nil and
  let the builder raise."
  [region endpoint-override]
  (if region
    (Region/of region)
    (or (try
          (.getRegion (DefaultAwsRegionProviderChain.))
          (catch SdkClientException _ nil))
        (when endpoint-override Region/US_EAST_1))))

(defn s3-presigner
  ([]
   (s3-presigner nil))
  ([& {:keys [accelerate-mode-enabled
              checksum-validation-enabled
              endpoint-override
              region
              credentials-provider]
       :or {checksum-validation-enabled true}}]
   (let [s3config (cond-> (S3Configuration/builder)
                    accelerate-mode-enabled (.accelerateModeEnabled accelerate-mode-enabled)
                    checksum-validation-enabled (.checksumValidationEnabled checksum-validation-enabled)
                    :always (.build))
         region (resolve-region region endpoint-override)]
     (cond-> (doto (S3Presigner/builder)
               (.serviceConfiguration s3config))
       credentials-provider  (.credentialsProvider credentials-provider)
       region                (.region region)
       endpoint-override     (.endpointOverride (URI. endpoint-override))
       :always (.build)))))

(defn credentials-provider
  [access-key-id secret-access-key]
  (let [credentials  (AwsBasicCredentials/create access-key-id secret-access-key)]
    (StaticCredentialsProvider/create credentials)))

(defn s3-client [& {:keys [credentials-provider endpoint-override region]}]
  (let [region (resolve-region region endpoint-override)]
    (cond-> (S3Client/builder)
      credentials-provider  (.credentialsProvider credentials-provider)
      region                (.region region)
      endpoint-override     (.endpointOverride (URI. endpoint-override))
      :always (.build))))

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

(defn delete-object [client bucket key]
  (let [req (-> (DeleteObjectRequest/builder)
                (.bucket bucket)
                (.key key)
                (.build))]
    (.deleteObject client req)))

(defn get-object
  "Download an object from S3 to a local file.
   Returns the destination path on success."
  [client bucket key dest-path]
  (let [req (-> (GetObjectRequest/builder)
                (.bucket bucket)
                (.key key)
                (.build))
        dest-file (if (instance? File dest-path)
                    dest-path
                    (File. (str dest-path)))
        dest (if (instance? Path dest-path)
               dest-path
               (.toPath dest-file))]
    ;; Ensure parent directory exists
    (when-let [parent (.getParentFile dest-file)]
      (.mkdirs parent))
    (.getObject client req dest)
    dest-path))
