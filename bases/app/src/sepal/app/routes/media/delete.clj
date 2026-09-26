(ns sepal.app.routes.media.delete
  "The shared delete dialog for a media item. Not a `sepal.app.delete`
  resource: deleting media also removes its stored object, which needs the S3
  client and the instance's key prefix."
  (:require [clojure.tools.logging :as log]
            [sepal.app.flash :as flash]
            [sepal.app.html :as html]
            [sepal.app.http-response :as http]
            [sepal.app.routes.media.keys :as media.keys]
            [sepal.app.routes.media.routes :as media.routes]
            [sepal.app.ui.delete :as ui.delete]
            [sepal.aws-s3.interface :as s3.i]
            [sepal.database.interface :as db.i]
            [sepal.error.interface :as error.i]
            [sepal.media.interface :as media.i]
            [sepal.media.interface.activity :as media.activity]
            [zodiac.core :as z]))

(defn delete!
  "Delete the media row, recording the event in the same transaction while the
  row still names its file, then remove the stored object. The object goes
  last: a row left pointing at nothing is worse than an orphaned object."
  [db s3-client media deleted-by]
  (db.i/with-transaction [tx db]
    (media.activity/create! tx media.activity/deleted deleted-by media)
    (media.i/delete! tx (:media/id media)))
  (try
    (s3.i/delete-object s3-client (:media/s3-bucket media) (:media/s3-key media))
    (catch Exception ex
      ;; TODO: handle errors
      (error.i/ex->error ex))))

(defn- label [media]
  (str "media " (or (:media/title media) "item")))

(defn handler [{:keys [::z/context request-method viewer]}]
  (let [{:keys [db resource s3-client]} context]
    (cond
      (not (media.keys/own-key? context resource))
      ;; Not this instance's object, so as far as this garden is concerned
      ;; there is no such media. Same answer the transform route gives.
      (do (log/warn "Refusing to delete media outside this instance's prefix"
                    {:s3-key (:media/s3-key resource)})
          (http/not-found))

      (= :post request-method)
      (do (delete! db s3-client resource (:user/id viewer))
          (-> (http/see-other media.routes/index)
              (flash/success (str "Deleted " (label resource)))))

      :else
      (html/render-partial
        (ui.delete/dialog :action (z/url-for media.routes/delete {:id (:media/id resource)})
                          :label (label resource)
                          :blockers [])))))
