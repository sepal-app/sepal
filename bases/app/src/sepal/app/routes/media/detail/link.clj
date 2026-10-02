(ns sepal.app.routes.media.detail.link
  (:require [failjure.core :as f]
            [sepal.app.http-response :as http]
            [sepal.app.routes.media.detail.shared :as shared]
            [sepal.app.routes.media.link-info :as link-info]
            [sepal.database.interface :as db.i]
            [sepal.error.interface :as error.i]
            [sepal.i18n.interface :refer [tr]]
            [sepal.media.interface :as media.i]
            [sepal.media.interface.activity :as media.activity]
            [sepal.validation.interface :as validation.i]
            [zodiac.core :as z]))

(defn- link!
  "Link the media and record it, in one transaction. Returns the link, or the
  error from `media.i/link!`."
  [db separator media created-by resource-id resource-type]
  (db.i/with-transaction [tx db]
    (let [link (media.i/link! tx (:media/id media) resource-id resource-type)]
      (when-not (error.i/error? link)
        (media.activity/create-link! tx media.activity/linked created-by media link
                                     (:text (link-info/link-info tx link separator))))
      link)))

(defn- unlink!
  "Remove the media's link and record what it named, in one transaction."
  [db separator media created-by]
  (db.i/with-transaction [tx db]
    (when-let [link (media.i/get-link tx (:media/id media))]
      (let [text (:text (link-info/link-info tx link separator))]
        (media.i/unlink! tx (:media/id media))
        (media.activity/create-link! tx media.activity/unlinked created-by media link text)))))

(def LinkParams
  [:map {:closed true}
   [:resource-id [:string {:min 1}]]
   [:resource-type [:enum "accession" "material" "taxon" "location"]]])

(defn post-handler [{:keys [::z/context form-params viewer]}]
  (let [{:keys [db material-separator resource]} context]
    (f/attempt-all [{:keys [resource-id resource-type]} (validation.i/validate-form-values
                                                          LinkParams form-params)
                    _linked (f/try* (link! db material-separator resource (:user/id viewer)
                                           resource-id resource-type))]
      (http/saved (shared/page context resource true))
      (f/when-failed [e]
        (http/not-saved e (tr "Could not link the media"))))))

(defn delete-handler [{:keys [::z/context viewer]}]
  (let [{:keys [db material-separator resource]} context]
    (f/attempt-all [_unlinked (f/try* (unlink! db material-separator resource (:user/id viewer)))]
      (http/saved (shared/page context resource true))
      (f/when-failed [e]
        (http/not-saved e (tr "Could not unlink the media"))))))
