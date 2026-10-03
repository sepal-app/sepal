(ns sepal.app.routes.media.s3
  (:require [babashka.fs :as fs]
            [clojure.string :as s]
            [failjure.core :as f]
            [sepal.app.http-response :as http]
            [sepal.app.json :as json]
            [sepal.aws-s3.interface :as aws-s3.i]
            [sepal.validation.interface :as validation.i]
            [zodiac.core :as z])
  (:import [java.security SecureRandom]
           [java.time Duration]))

(defn random-hex [length]
  (let [ba (byte-array (int (/ length 2)))]
    (doto (SecureRandom.)
      (.nextBytes ba))
    (.toString (BigInteger. 1 ba) 16)))

(def FormParams
  [:map {:closed true}
   [:filename :string]
   [:contentType :string]])

(def ^:private signature-duration
  "How long a signed URL stays valid. The uploader signs each file just before
  it uploads it, so this only has to outlast the start of one request."
  (Duration/ofMinutes 15))

(defn- sign
  "A presigned PUT for one file, as the JSON Uppy's `signRequest` returns: the
  URL, the key the server chose, and the headers the URL was signed with."
  [context form-params]
  (let [{:keys [s3-presigner media-upload-bucket media-key-prefix]} context]
    (f/attempt-all [data (validation.i/validate-form-values FormParams form-params)]
      (let [;; Lowercase so the header the browser sends matches the signature.
            content-type (s/lower-case (:contentType data))
            s3-key (format "%s%s.%s"
                           media-key-prefix
                           (random-hex 20)
                           (fs/extension (:filename data)))]
        (json/json-response
          {:url (aws-s3.i/presign-put-url media-upload-bucket
                                          s3-key
                                          content-type
                                          :duration signature-duration
                                          :presigner s3-presigner)
           :key s3-key
           :headers {"content-type" content-type}}))
      (f/when-failed [_]
        (http/unprocessable-entity "Cannot sign this upload")))))

(defn handler
  "Sign the file a browser is about to upload. Without S3 credentials the app
  builds no presigner and media upload is off, so there is nothing to sign
  with: no page offers an upload, and this answers 404."
  [& {:keys [::z/context form-params] :as _request}]
  (if (:s3-presigner context)
    (sign context form-params)
    (http/not-found)))
