(ns sepal.app.routes.taxon.detail.tags
  (:require [failjure.core :as f]
            [sepal.app.http-response :as http]
            [sepal.app.routes.taxon.detail.shared :as taxon.shared]
            [sepal.app.routes.taxon.panel :as taxon.panel]
            [sepal.app.routes.taxon.routes :as taxon.routes]
            [sepal.app.tag-links :as tag-links]
            [sepal.app.ui.page :as ui.page]
            [sepal.app.ui.pages.detail :as pages.detail]
            [sepal.app.ui.tag :as tag.ui]
            [sepal.database.interface :as db.i]
            [sepal.error.interface :as error.i]
            [sepal.i18n.interface :refer [tr]]
            [sepal.tag.interface :as tag.i]
            [sepal.validation.interface :as validation.i]
            [zodiac.core :as z]))

(def FormParams
  [:map {:closed true}
   [:tag-name [:string {:min 1}]]])

(defn page-content [& {:keys [taxon tags all-tags]}]
  (taxon.shared/page
    :taxon taxon
    :active taxon.shared/tags-tab
    :body
    (tag.ui/section :tags tags
                    :all-tags all-tags
                    :action (z/url-for taxon.routes/detail-tags {:id (:taxon/id taxon)})
                    :remove-url-fn (fn [tag] (z/url-for taxon.routes/detail-tag
                                                        {:id (:taxon/id taxon)
                                                         :tag-id (:tag/id tag)})))))

(defn render [& {:keys [taxon tags all-tags panel-data timezone]}]
  (ui.page/page :content (pages.detail/page-content-with-panel
                           :content (page-content :taxon taxon :tags tags :all-tags all-tags)
                           :panel-content (taxon.panel/panel-content
                                            :taxon (:taxon panel-data)
                                            :parent (:parent panel-data)
                                            :stats (:stats panel-data)
                                            :synonyms (:synonyms panel-data)
                                            :activities (:activities panel-data)
                                            :activity-count (:activity-count panel-data)
                                            :timezone timezone))
                :breadcrumbs (taxon.shared/breadcrumbs taxon)))

(defn add! [db taxon-id created-by data]
  (db.i/with-transaction [tx db]
    (let [tag (tag-links/resolve-or-create! tx (:tag-name data) created-by)]
      (if (error.i/error? tag)
        tag
        (let [linked? (tag-links/link! tx tag :taxon taxon-id created-by)]
          (if (error.i/error? linked?) linked? tag))))))

(defn remove! [db taxon-id removed-by tag]
  (db.i/with-transaction [tx db]
    (tag-links/unlink! tx tag :taxon taxon-id removed-by)))

(defn- page
  "The tab, as its GET renders it. Every write answers with it, so the panel
  beside the chips is current too."
  [{:keys [db resource timezone] :as context}]
  (render :taxon resource
          :tags (tag.i/get-for-resource db :taxon (:taxon/id resource))
          :all-tags (tag.i/list-all db)
          :panel-data (taxon.panel/fetch-panel-data context db resource)
          :timezone timezone))

(defn get-handler [{:keys [::z/context]}]
  (page context))

(defn post-handler [{:keys [::z/context form-params viewer]}]
  (let [{:keys [db resource]} context]
    (f/attempt-all [data (validation.i/validate-form-values FormParams form-params)
                    _saved (f/try* (add! db (:taxon/id resource) (:user/id viewer) data))]
      (http/saved (page context))
      (f/when-failed [e]
        (http/not-saved e (tr "The tag could not be added."))))))

(defn row-handler [{:keys [::z/context path-params viewer]}]
  (let [{:keys [db resource]} context
        id (:taxon/id resource)
        tag-id (parse-long (:tag-id path-params))
        ;; Unlike synonyms.clj's row-handler, this lookup is a bare get-by-id
        ;; rather than a lookup re-scoped through get-for-resource: a tag-id
        ;; that exists globally but isn't linked to this taxon still
        ;; resolves here, but remove!'s untag! call is itself scoped by
        ;; resource-id/resource-type and reports back whether it actually
        ;; deleted a row, so a stale or foreign id is a true no-op -- no
        ;; exception, and no `unlinked` activity event gets written for it.
        tag (when tag-id (tag.i/get-by-id db tag-id))]
    (f/attempt-all [_removed (f/try* (when tag
                                       (remove! db id (:user/id viewer) tag)))]
      (http/saved (page context))
      (f/when-failed [e]
        (http/not-saved e (tr "The tag could not be removed."))))))
