(ns sepal.app.routes.media.uploaded
  (:require [reitit.core :as r]
            [sepal.app.html :as html]
            [sepal.app.ui.media :as media.ui]
            [sepal.error.interface :as error.i]
            [sepal.media.interface :as media.i]))

(defn handler [& {:keys [context params ::r/router viewer] :as _request}]
  (let [{:keys [db imgix-media-domain]} context
        {filename :filename
         content-type :contentType
         organization-id :organizationId
         link-resource-type :linkResourceType
         link-resource-id :linkResourceId
         s3-bucket :s3Bucket
         s3-key :s3Key
         size :size} params
        result  (-> (media.i/create! db
                                     {:created-by (:user/id viewer)
                                      :media-type content-type
                                      :organization-id (Integer/parseInt organization-id)
                                      :s3-bucket s3-bucket
                                      :s3-key s3-key
                                      :size-in-bytes (Integer/parseInt size)
                                      :title filename})
                    (assoc :thumbnail-url (media.ui/thumbnail-url imgix-media-domain s3-key)))]

    ;; If the media was successfully added and we were sent an resource type and
    ;; resource id to link then link the media and the resource
    (when (and (some? link-resource-type)
               (some? link-resource-id)
               (not (error.i/error? result)))
      (media.i/link! db
                     (:media/id result)
                     link-resource-id
                     link-resource-type))

    (if-not (error.i/error? result)
      (->  (media.ui/media-item :item result
                                :router router)
           (html/render-partial))
      ;; TODO: handle error
      (throw result))))
