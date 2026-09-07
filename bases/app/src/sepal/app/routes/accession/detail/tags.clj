(ns sepal.app.routes.accession.detail.tags
  (:require [sepal.app.http-response :as http]
            [sepal.app.routes.accession.detail.shared :as accession.shared]
            [sepal.app.routes.accession.panel :as accession.panel]
            [sepal.app.routes.accession.routes :as accession.routes]
            [sepal.app.ui.page :as ui.page]
            [sepal.app.ui.pages.detail :as pages.detail]
            [sepal.app.ui.tag :as tag.ui]
            [sepal.database.interface :as db.i]
            [sepal.error.interface :as error.i]
            [sepal.tag.interface :as tag.i]
            [sepal.tag.interface.activity :as tag.activity]
            [sepal.taxon.interface :as taxon.i]
            [sepal.validation.interface :as validation.i]
            [zodiac.core :as z]))

(def FormParams
  [:map {:closed true}
   [:tag-name [:string {:min 1}]]])

(defn page-content [& {:keys [accession taxon tags all-tags can-add?]}]
  (accession.shared/page
    :accession accession
    :taxon taxon
    :active accession.shared/tags-tab
    :body
    (tag.ui/section :tags tags
                    :all-tags all-tags
                    :can-add? can-add?
                    :action (z/url-for accession.routes/detail-tags {:id (:accession/id accession)})
                    :remove-url-fn (fn [tag] (z/url-for accession.routes/detail-tag
                                                        {:id (:accession/id accession)
                                                         :tag-id (:tag/id tag)})))))

(defn render [& {:keys [accession taxon tags all-tags panel-data timezone can-add?]}]
  (ui.page/page :content (pages.detail/page-content-with-panel
                           :content (page-content :accession accession :taxon taxon
                                                  :tags tags :all-tags all-tags
                                                  :can-add? can-add?)
                           :panel-content (accession.panel/panel-content
                                            :accession (:accession panel-data)
                                            :taxon (:taxon panel-data)
                                            :supplier (:supplier panel-data)
                                            :stats (:stats panel-data)
                                            :notes (:notes panel-data)
                                            :note-count (:note-count panel-data)
                                            :activities (:activities panel-data)
                                            :activity-count (:activity-count panel-data)
                                            :timezone timezone))
                :breadcrumbs (accession.shared/breadcrumbs taxon accession)))

(defn resolve-or-create-tag!
  "The one text field handles both 'pick an existing tag' and 'make a new
  one': a name that matches (case-insensitively, via tag.name's own
  collation) links the existing row; a name that matches nothing creates it.
  This is a lookup-then-maybe-create, not a get-or-create query, because
  `tag!`'s own unique-constraint swallow (core.clj) already makes the create
  path idempotent under a race -- a second create attempt on the same name
  fails its own unique constraint and that failure surfaces as a normal
  validation error, which is an acceptable, rare race to leave uncaught here."
  [ctx db name created-by]
  (or (tag.i/get-by-name ctx db name)
      (let [created (tag.i/create! db {:name name})]
        (when-not (error.i/error? created)
          (tag.activity/create! db tag.activity/created created-by created))
        created)))

(defn add! [ctx db accession-id created-by data]
  (try
    (db.i/with-transaction [tx db]
      (let [tag (resolve-or-create-tag! ctx tx (:tag-name data) created-by)]
        (if (error.i/error? tag)
          tag
          (let [linked? (tag.i/tag! tx (:tag/id tag) accession-id :accession)]
            (if (error.i/error? linked?)
              linked?
              (do
                ;; tag! returns false when the link already existed (a true
                ;; no-op) -- only a real state change gets an activity event.
                (when linked?
                  (tag.activity/create-link! tx tag.activity/linked created-by tag :accession accession-id))
                tag))))))
    (catch Exception ex
      (error.i/ex->error ex))))

(defn remove! [db accession-id removed-by tag]
  (try
    (db.i/with-transaction [tx db]
      ;; untag! returns false when there was no such link to remove (a stale
      ;; id, a double-DELETE) -- only a real state change gets an activity
      ;; event.
      (when (tag.i/untag! tx (:tag/id tag) accession-id :accession)
        (tag.activity/create-link! tx tag.activity/unlinked removed-by tag :accession accession-id)))
    (catch Exception ex
      (error.i/ex->error ex))))

(defn handler [{:keys [::z/context form-params request-method viewer]}]
  (let [{:keys [db resource timezone]} context
        id (:accession/id resource)
        ;; Below the migration that added the tag tables there is nowhere to
        ;; put the link. The form is not rendered, and the POST is refused
        ;; rather than left reachable by a direct request.
        can-add? (tag.i/available? context)]
    (case request-method
      :post
      (if-not can-add?
        (http/not-found)
        (let [result (validation.i/validate-form-values FormParams form-params)]
          (if (error.i/error? result)
            (http/validation-errors (validation.i/humanize result))
            (let [saved (add! context db id (:user/id viewer) result)]
              (if (error.i/error? saved)
                (http/validation-errors (validation.i/humanize saved))
                (http/hx-redirect (z/url-for accession.routes/detail-tags {:id id})))))))

      (let [taxon (taxon.i/get-by-id db (:accession/taxon-id resource))
            tags (tag.i/get-for-resource context db :accession id)
            all-tags (tag.i/list-all context db)
            panel-data (accession.panel/fetch-panel-data context db resource)]
        (render :accession resource :taxon taxon :tags tags :all-tags all-tags
                :panel-data panel-data :timezone timezone :can-add? can-add?)))))

(defn row-handler [{:keys [::z/context path-params viewer]}]
  (let [{:keys [db resource]} context
        id (:accession/id resource)
        tag-id (parse-long (:tag-id path-params))
        ;; Unlike synonyms.clj's row-handler, this lookup is a bare get-by-id
        ;; rather than a lookup re-scoped through get-for-resource: a tag-id
        ;; that exists globally but isn't linked to this accession still
        ;; resolves here, but remove!'s untag! call is itself scoped by
        ;; resource-id/resource-type and reports back whether it actually
        ;; deleted a row, so a stale or foreign id is a true no-op -- no
        ;; exception, and no `unlinked` activity event gets written for it.
        tag (when tag-id (tag.i/get-by-id context db tag-id))]
    (when tag
      (remove! db id (:user/id viewer) tag))
    (http/hx-redirect (z/url-for accession.routes/detail-tags {:id id}))))
