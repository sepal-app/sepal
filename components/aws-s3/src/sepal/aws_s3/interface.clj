(ns sepal.aws-s3.interface
  (:require [integrant.core :as ig]
            [sepal.aws-s3.core :as core])
  (:import [java.time Duration]))

(defn presign-put-url
  [bucket key content-type & {:keys [duration md5 metadata presigner]
                              :or {duration (Duration/ofHours 8)
                                   metadata {}}}]
  (core/presign-put-url bucket
                        key
                        content-type
                        :duration duration
                        :md5 md5
                        :metadata metadata
                        :presigner presigner))

(defn list-objects [client bucket prefix]
  (core/list-objects client bucket prefix))

(defn delete-object [client bucket key]
  (core/delete-object client bucket key))

(defn get-object
  "Download an object from S3 to a local file.
   Returns the destination path on success."
  [client bucket key dest-path]
  (core/get-object client bucket key dest-path))

;; TODO: The credentials-provider component isn't specific to s3
(defmethod ig/init-key ::credentials-provider [_ {:keys [access-key-id secret-access-key]}]
  (core/credentials-provider access-key-id secret-access-key))

(defmethod ig/init-key ::s3-presigner [_ {:keys [accelerate-mode-enabled
                                                 checksum-validation-enabled
                                                 endpoint-override
                                                 credentials-provider]}]
  (core/s3-presigner :accelerate-mode-enabled accelerate-mode-enabled
                     :checksum-validation-enabled checksum-validation-enabled
                     :endpoint-override endpoint-override
                     :credentials-provider credentials-provider))

;; TODO: Don't initi s3 client if s3 keys not set

(defmethod ig/init-key ::s3-client [_ {:keys [credentials-provider endpoint-override]}]
  (core/s3-client :credentials-provider credentials-provider
                  :endpoint-override endpoint-override))
