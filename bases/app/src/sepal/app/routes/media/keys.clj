(ns sepal.app.routes.media.keys
  "Whether an S3 object belongs to this instance.

  The S3 client, the presigner and the bucket are shared by every instance in
  the process, so the key prefix is the only thing separating one garden's
  objects from another's. Every path that takes a key out of the database and
  hands it to S3 checks it here first."
  (:require [clojure.string :as str]))

(defn own-key?
  [{:keys [media-key-prefix media-upload-bucket]} media]
  (let [s3-key (:media/s3-key media)
        bucket (:media/s3-bucket media)]
    (boolean (and s3-key
                  bucket
                  media-key-prefix
                  media-upload-bucket
                  (= bucket media-upload-bucket)
                  (str/starts-with? s3-key media-key-prefix)
                  (not (str/includes? s3-key ".."))))))
