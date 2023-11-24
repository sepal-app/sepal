(ns sepal.app.routes.media.s3
  (:require [babashka.fs :as fs]
            [sepal.app.json :as json]
            [sepal.aws-s3.interface :as aws-s3.i])
  (:import [java.security SecureRandom]))

(defn random-hex [length]
  (let [ba (byte-array (int (/ length 2)))]
    (doto (SecureRandom.)
      (.nextBytes ba))
    (.toString (BigInteger. 1 ba) 16)))

(defn handler [& {:keys [context params] :as _request}]
  (let [{:keys [s3-presigner media-upload-bucket]} context
        {filename :filename
         content-type :contentType
         organization-id :organizationId} params
        filename (format "organization_id=%s/%s.%s"
                         organization-id
                         (random-hex 20)
                         (fs/extension filename))]
    (json/json-response {:method "PUT"
                         :url (aws-s3.i/presign-put-url media-upload-bucket
                                                        filename
                                                        content-type
                                                        :presigner s3-presigner)
                         :headers {}})))
