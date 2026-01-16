(ns sepal.app.routes.media.uploaded
  (:require [sepal.app.html :as html]
            [sepal.app.params :as params]
            [sepal.app.ui.media :as media.ui]
            [sepal.error.interface :as error.i]
            [sepal.media.interface :as media.i]
            [sepal.media.interface.activity :as media.activity]
            [zodiac.core :as z]))

(def FormParams
  [:map {:closed true}
   [:filename :string]
   [:contentType :string]
   [:linkResourceType [:maybe :string]]
   [:linkResourceId [:maybe :string]]
   [:s3Bucket :string]
   [:s3Key :string]
   [:size :int]])

(defn handler [& {:keys [::z/context form-params viewer] :as _request}]
  (let [{:keys [db]} context
        {filename :filename
         content-type :contentType
         link-resource-type :linkResourceType
         link-resource-id :linkResourceId
         s3-bucket :s3Bucket
         s3-key :s3Key
         size :size} (params/decode FormParams form-params)
        result (media.i/create! db
                                {:media-type content-type
                                 :s3-bucket s3-bucket
                                 :s3-key s3-key
                                 :size-in-bytes size
                                 :title filename
                                 :created-by (:user/id viewer)})]

    (if (error.i/error? result)
      ;; TODO: handle error properly
      (throw (ex-info "Failed to create media" {:error result}))

      (let [media (assoc result :thumbnail-url (media.ui/thumbnail-url (:media/id result)))]
        ;; Create activity record for the media creation
        (media.activity/create! db
                                media.activity/created
                                (:user/id viewer)
                                media)

        ;; If we were sent a resource type and resource id to link then link
        ;; the media and the resource
        (when (and (some? link-resource-type)
                   (some? link-resource-id))
          (media.i/link! db
                         (:media/id media)
                         link-resource-id
                         link-resource-type))

        (-> (media.ui/media-item :item media)
            (html/render-partial))))))
