(ns sepal.app.routes.media.uploaded
  (:require [failjure.core :as f]
            [sepal.app.html :as html]
            [sepal.app.http-response :as http]
            [sepal.app.ui.media :as media.ui]
            [sepal.media.interface :as media.i]
            [sepal.media.interface.activity :as media.activity]
            [sepal.validation.interface :as validation.i]
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
  (let [{:keys [db]} context]
    (f/attempt-all [data (validation.i/validate-form-values FormParams form-params)
                    result (f/try* (media.i/create! db
                                                    {:media-type (:contentType data)
                                                     :s3-bucket (:s3Bucket data)
                                                     :s3-key (:s3Key data)
                                                     :size-in-bytes (:size data)
                                                     :title (:filename data)
                                                     :created-by (:user/id viewer)}))]
      (let [media (assoc result :thumbnail-url (media.ui/thumbnail-url (:media/id result)))]
        ;; Create activity record for the media creation
        (media.activity/create! db
                                media.activity/created
                                (:user/id viewer)
                                media)

        ;; If we were sent a resource type and resource id to link then link
        ;; the media and the resource
        (when (and (some? (:linkResourceType data))
                   (some? (:linkResourceId data)))
          (media.i/link! db
                         (:media/id media)
                         (:linkResourceId data)
                         (:linkResourceType data)))

        (-> (media.ui/media-item :item media)
            (html/render-partial)))
      (f/when-failed [e]
        (http/failure-partial e "The upload could not be saved.")))))
