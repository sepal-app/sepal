(ns sepal.app.routes.media.detail
  (:require [failjure.core :as f]
            [sepal.app.authorization :as authz]
            [sepal.app.http-response :as http]
            [sepal.app.routes.media.detail.shared :as shared]
            [sepal.database.interface :as db.i]
            [sepal.i18n.interface :refer [tr]]
            [sepal.media.interface :as media.i]
            [sepal.media.interface.activity :as media.activity]
            [sepal.media.interface.permission :as media.perm]
            [sepal.validation.interface :as validation.i]
            [zodiac.core :as z]))

(def FormParams
  [:map {:closed true}
   [:title {:decode/form validation.i/empty->nil} [:maybe :string]]
   [:description {:decode/form validation.i/empty->nil} [:maybe :string]]])

(defn save! [db media-id updated-by data]
  (db.i/with-transaction [tx db]
    (let [media (media.i/update! tx media-id data)]
      (media.activity/create! tx media.activity/updated updated-by media)
      media)))

(defn get-handler [{:keys [::z/context viewer]}]
  (shared/page context (:resource context) (authz/user-has-permission? viewer media.perm/edit)))

(defn post-handler [{:keys [::z/context form-params viewer]}]
  (let [{:keys [db resource]} context
        id (:media/id resource)]
    (f/attempt-all [data (validation.i/validate-form-values FormParams form-params)
                    _saved (f/try* (save! db id (:user/id viewer) data))]
      (http/saved (shared/page context (media.i/get-by-id db id) true)
                  (tr "Media updated successfully"))
      (f/when-failed [e]
        (http/not-saved e (tr "Could not save the media"))))))
