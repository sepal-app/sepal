(ns sepal.app.routes.material.detail.tags
  (:require [failjure.core :as f]
            [sepal.accession.interface :as accession.i]
            [sepal.app.http-response :as http]
            [sepal.app.routes.material.detail.shared :as material.shared]
            [sepal.app.routes.material.panel :as material.panel]
            [sepal.app.routes.material.routes :as material.routes]
            [sepal.app.tag-links :as tag-links]
            [sepal.app.ui.page :as ui.page]
            [sepal.app.ui.pages.detail :as pages.detail]
            [sepal.app.ui.tag :as tag.ui]
            [sepal.database.interface :as db.i]
            [sepal.error.interface :as error.i]
            [sepal.i18n.interface :refer [tr]]
            [sepal.tag.interface :as tag.i]
            [sepal.taxon.interface :as taxon.i]
            [sepal.validation.interface :as validation.i]
            [zodiac.core :as z]))

(def FormParams
  [:map {:closed true}
   [:tag-name [:string {:min 1}]]])

(defn page-content [& {:keys [material accession taxon tags all-tags separator]}]
  (material.shared/page
    :material material
    :accession accession
    :separator separator
    :taxon taxon
    :active material.shared/tags-tab
    :body
    (tag.ui/section :tags tags
                    :all-tags all-tags
                    :action (z/url-for material.routes/detail-tags {:id (:material/id material)})
                    :remove-url-fn (fn [tag] (z/url-for material.routes/detail-tag
                                                        {:id (:material/id material)
                                                         :tag-id (:tag/id tag)})))))

(defn render [& {:keys [material accession taxon tags all-tags panel-data separator timezone]}]
  (ui.page/page :page-title-buttons (material.shared/actions :material material)
                :content (pages.detail/page-content-with-panel
                           :content (page-content :material material :accession accession
                                                  :taxon taxon :tags tags :all-tags all-tags
                                                  :separator separator)
                           :panel-content (material.panel/panel-content
                                            :panel-data panel-data
                                            :material (:material panel-data)
                                            :accession (:accession panel-data)
                                            :taxon (:taxon panel-data)
                                            :location (:location panel-data)
                                            :history (:history panel-data)
                                            :observations (:observations panel-data)
                                            :observation-count (:observation-count panel-data)
                                            :activities (:activities panel-data)
                                            :activity-count (:activity-count panel-data)
                                            :timezone timezone))
                :breadcrumbs (material.shared/breadcrumbs :accession accession
                                                          :material material
                                                          :separator separator
                                                          :taxon taxon)))

(defn add! [db material-id created-by data]
  (db.i/with-transaction [tx db]
    (let [tag (tag-links/resolve-or-create! tx (:tag-name data) created-by)]
      (if (error.i/error? tag)
        tag
        (let [linked? (tag-links/link! tx tag :material material-id created-by)]
          (if (error.i/error? linked?) linked? tag))))))

(defn remove! [db material-id removed-by tag]
  (db.i/with-transaction [tx db]
    (tag-links/unlink! tx tag :material material-id removed-by)))

(defn- page
  "The tab, as its GET renders it. Every write answers with it, so the panel
  beside the chips is current too."
  [{:keys [db material-separator resource timezone]}]
  (let [id (:material/id resource)
        accession (accession.i/get-by-id db (:material/accession-id resource))]
    (render :material resource
            :accession accession
            :taxon (taxon.i/get-by-id db (:accession/taxon-id accession))
            :tags (tag.i/get-for-resource db :material id)
            :all-tags (tag.i/list-all db)
            :panel-data (material.panel/fetch-panel-data db resource)
            :timezone timezone
            :separator material-separator)))

(defn get-handler [{:keys [::z/context]}]
  (page context))

(defn post-handler [{:keys [::z/context form-params viewer]}]
  (let [{:keys [db resource]} context]
    (f/attempt-all [data (validation.i/validate-form-values FormParams form-params)
                    _saved (f/try* (add! db (:material/id resource) (:user/id viewer) data))]
      (http/saved (page context))
      (f/when-failed [e]
        (http/not-saved e (tr "The tag could not be added."))))))

(defn row-handler [{:keys [::z/context path-params viewer]}]
  (let [{:keys [db resource]} context
        id (:material/id resource)
        tag-id (parse-long (:tag-id path-params))
        ;; Unlike synonyms.clj's row-handler, this lookup is a bare get-by-id
        ;; rather than a lookup re-scoped through get-for-resource: a tag-id
        ;; that exists globally but isn't linked to this material still
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
