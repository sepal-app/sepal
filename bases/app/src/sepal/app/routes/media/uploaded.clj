(ns sepal.app.routes.media.uploaded
  (:require [failjure.core :as f]
            [sepal.app.html :as html]
            [sepal.app.http-response :as http]
            [sepal.app.routes.media.keys :as media.keys]
            [sepal.app.routes.media.link-info :as link-info]
            [sepal.app.ui.media :as media.ui]
            [sepal.aws-s3.interface :as aws-s3.i]
            [sepal.error.interface :as error.i]
            [sepal.i18n.interface :refer [tr]]
            [sepal.media.interface :as media.i]
            [sepal.media.interface.activity :as media.activity]
            [sepal.validation.interface :as validation.i]
            [zodiac.core :as z]))

(def FormParams
  [:map {:closed true}
   [:filename :string]
   [:linkResourceType {:optional true} [:maybe :string]]
   [:linkResourceId {:optional true} [:maybe :string]]
   [:s3Key :string]])

(defn- uploaded-object
  "The object at `s3-key`, as S3 describes it. The browser names the key, so it
  must be under this garden's prefix and S3 must hold an object there."
  [{:keys [s3-client media-upload-bucket] :as context} s3-key]
  (if (media.keys/own-key? context {:media/s3-bucket media-upload-bucket
                                    :media/s3-key s3-key})
    (or (aws-s3.i/head-object s3-client media-upload-bucket s3-key)
        (error.i/error :not-found "No object was uploaded at this key"))
    (error.i/error :forbidden "The key is not this garden's")))

(defn- create-media
  [{:keys [::z/context form-params viewer]}]
  (let [{:keys [db material-separator media-upload-bucket]} context]
    (f/attempt-all [data (validation.i/validate-form-values FormParams form-params)
                    object (uploaded-object context (:s3Key data))
                    result (f/try* (media.i/create! db
                                                    {:media-type (or (:content-type object)
                                                                     "application/octet-stream")
                                                     :s3-bucket media-upload-bucket
                                                     :s3-key (:s3Key data)
                                                     :size-in-bytes (:content-length object)
                                                     :title (:filename data)
                                                     :created-by (:user/id viewer)}))]
      (let [media (assoc result :thumbnail-url (media.ui/thumbnail-url (:media/id result)))
            ;; Sent when the upload was made from a record's Media tab.
            link (when (and (some? (:linkResourceType data))
                            (some? (:linkResourceId data)))
                   (let [link (media.i/link! db
                                             (:media/id media)
                                             (:linkResourceId data)
                                             (:linkResourceType data))]
                     (when-not (error.i/error? link) link)))]
        ;; One event for the upload, naming the record it was linked to: the
        ;; link is part of the upload, not a second thing that happened.
        (media.activity/create! db
                                media.activity/created
                                (:user/id viewer)
                                media
                                :link link
                                :link-text (when link
                                             (:text (link-info/link-info db link material-separator))))

        (-> (media.ui/media-item :item media)
            (html/render-partial)))
      (f/when-failed [e]
        (http/failure-partial e (tr "The upload could not be saved."))))))

(defn handler
  "Record a file the browser has uploaded to S3. Without S3 credentials media
  upload is off, so there is nothing to record and this answers 404."
  [& {:keys [::z/context] :as request}]
  (if (:s3-client context)
    (create-media request)
    (http/not-found)))
