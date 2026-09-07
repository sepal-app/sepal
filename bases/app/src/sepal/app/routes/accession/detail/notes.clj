(ns sepal.app.routes.accession.detail.notes
  (:require [sepal.app.html :as html]
            [sepal.app.http-response :as http]
            [sepal.app.routes.accession.detail.shared :as accession.shared]
            [sepal.app.routes.accession.panel :as accession.panel]
            [sepal.app.routes.accession.routes :as accession.routes]
            [sepal.app.ui.notes :as ui.notes]
            [sepal.app.ui.page :as ui.page]
            [sepal.app.ui.pages.detail :as pages.detail]
            [sepal.database.interface :as db.i]
            [sepal.error.interface :as error.i]
            [sepal.note.interface :as note.i]
            [sepal.note.interface.activity :as note.activity]
            [sepal.taxon.interface :as taxon.i]
            [sepal.validation.interface :as validation.i]
            [zodiac.core :as z]))

(def resource-type :accession)

(def FormParams
  [:map {:closed true}
   [:body [:string {:min 1}]]])

(defn- create-url [accession-id]
  (z/url-for accession.routes/detail-notes {:id accession-id}))

(defn- note-url-fn [accession-id]
  (fn [note-id]
    (z/url-for accession.routes/detail-note {:id accession-id :note-id note-id})))

(defn- write!
  "One transaction: the write and the activity that records it. A note whose
  activity failed to write would leave the changelog lying about the record."
  [db created-by f]
  (try
    (db.i/with-transaction [tx db]
      (f tx created-by))
    (catch Exception ex
      (error.i/ex->error ex))))

(defn render-list
  "The HTMX response every write returns: the list, swapped in place."
  [context db accession]
  (let [id (:accession/id accession)]
    (html/render-partial
      (ui.notes/note-list :notes (note.i/get-for-resource context db resource-type id)
                          :note-url-fn (note-url-fn id)))))

(defn page-content [& {:keys [accession taxon notes errors values]}]
  (let [id (:accession/id accession)]
    (accession.shared/page
      :accession accession
      :taxon taxon
      :active accession.shared/notes-tab
      :body (ui.notes/notes-body :notes notes
                                 :create-url (create-url id)
                                 :note-url-fn (note-url-fn id)
                                 :errors errors
                                 :values values))))

(defn render [& {:keys [accession taxon notes panel-data timezone]}]
  (ui.page/page
    :content (pages.detail/page-content-with-panel
               :content (page-content :accession accession
                                      :taxon taxon
                                      :notes notes)
               :panel-content (accession.panel/panel-content
                                :accession (:accession panel-data)
                                :taxon (:taxon panel-data)
                                :supplier (:supplier panel-data)
                                :intended-location (:intended-location panel-data)
                                :stats (:stats panel-data)
                                :notes (:notes panel-data)
                                :note-count (:note-count panel-data)
                                :activities (:activities panel-data)
                                :activity-count (:activity-count panel-data)
                                :timezone timezone))
    :breadcrumbs (accession.shared/breadcrumbs taxon accession)))

(defn handler
  "GET renders the tab. POST creates a note and returns the swapped list."
  [{:keys [::z/context form-params request-method viewer]}]
  (let [{:keys [db resource timezone]} context
        id (:accession/id resource)]
    (case request-method
      :post
      (let [result (validation.i/validate-form-values FormParams form-params)]
        (if (error.i/error? result)
          (http/validation-errors (validation.i/humanize result))
          (let [saved (write! db (:user/id viewer)
                              (fn [tx created-by]
                                (let [note (note.i/create! tx {:body (:body result)
                                                               :resource-type resource-type
                                                               :resource-id id
                                                               :created-by created-by})]
                                  (note.activity/create! tx note.activity/created created-by note)
                                  note)))]
            (if (error.i/error? saved)
              (http/validation-errors (validation.i/humanize saved))
              (render-list context db resource)))))

      (let [taxon (taxon.i/get-by-id db (:accession/taxon-id resource))
            panel-data (accession.panel/fetch-panel-data context db resource)]
        (render :accession resource
                :taxon taxon
                :notes (note.i/get-for-resource context db resource-type id)
                :panel-data panel-data
                :timezone timezone)))))

(defn note-handler
  "POST updates one note, DELETE removes it. Both answer with the swapped list."
  [{:keys [::z/context form-params path-params request-method viewer]
    :as _request}]
  (let [{:keys [db resource]} context
        note-id (parse-long (str (:note-id path-params)))
        note (when note-id (note.i/get-by-id db note-id))]
    ;; A note reached through the wrong resource's URL does not exist as far as
    ;; this route is concerned. Without this, any note in the garden is
    ;; editable through any accession's URL.
    (if-not (and note
                 (= resource-type (:note/resource-type note))
                 (= (:accession/id resource) (:note/resource-id note)))
      (http/not-found)
      (case request-method
        :post
        (let [result (validation.i/validate-form-values FormParams form-params)]
          (if (error.i/error? result)
            (http/validation-errors (validation.i/humanize result))
            (let [saved (write! db (:user/id viewer)
                                (fn [tx created-by]
                                  (let [updated (note.i/update! tx note-id {:body (:body result)})]
                                    (note.activity/create! tx note.activity/updated created-by updated)
                                    updated)))]
              (if (error.i/error? saved)
                (http/validation-errors (validation.i/humanize saved))
                (render-list context db resource)))))

        :delete
        (let [deleted (write! db (:user/id viewer)
                              (fn [tx created-by]
                                (note.activity/create! tx note.activity/deleted created-by note)
                                (note.i/delete! tx note-id)))]
          (if (error.i/error? deleted)
            (http/validation-errors (validation.i/humanize deleted))
            (render-list context db resource)))

        (http/not-found)))))
