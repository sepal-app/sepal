(ns sepal.app.routes.media.uploaded
  (:require [reitit.core :as r]
            [sepal.database.interface :as db.i]
            [sepal.app.html :as html]
            [sepal.app.routes.media.index :as index]))


(defn handler [& {:keys [context params viewer] :as _request}]
  (let [{:keys [db imgix-media-domain]} context
        {filename :filename
         content-type :contentType
         organization-id :organizationId
         s3-bucket :s3Bucket
         s3-key :s3Key
         size :size} params
        url  (index/thumbnail-url imgix-media-domain s3-key)
        _result (db.i/execute-one! db {:insert-into :media
                                       :values [{:created_by (:user/id viewer)
                                                 :s3_bucket s3-bucket
                                                 :s3_key s3-key
                                                 :size_in_bytes (Integer/parseInt size)
                                                 :media_type content-type
                                                 :organization_id (Integer/parseInt organization-id)
                                                 :title filename}]})]
    ;; TODO: error handling
    (-> (index/media-item :image url)
        (html/render-partial))))
