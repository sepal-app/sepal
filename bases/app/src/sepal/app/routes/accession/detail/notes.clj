(ns sepal.app.routes.accession.detail.notes
  (:require [failjure.core :as f]
            [sepal.app.html :as html]
            [sepal.app.http-response :as http]
            [sepal.app.routes.accession.detail.shared :as accession.shared]
            [sepal.app.routes.accession.panel :as accession.panel]
            [sepal.app.routes.accession.routes :as accession.routes]
            [sepal.app.ui.notes :as ui.notes]
            [sepal.app.ui.page :as ui.page]
            [sepal.app.ui.pages.detail :as pages.detail]
            [sepal.database.interface :as db.i]
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
  (db.i/with-transaction [tx db]
    (f tx created-by)))

(defn render-list
  "The HTMX response every write returns: the list, swapped in place."
  [db accession timezone]
  (let [id (:accession/id accession)]
    (html/render-partial
      (ui.notes/note-list :notes (note.i/get-for-resource db resource-type id)
                          :note-url-fn (note-url-fn id)
                          :timezone timezone))))

(defn page-content [& {:keys [accession taxon notes errors values timezone]}]
  (let [id (:accession/id accession)]
    (accession.shared/page
      :accession accession
      :taxon taxon
      :active accession.shared/notes-tab
      :body (ui.notes/notes-body :notes notes
                                 :create-url (create-url id)
                                 :note-url-fn (note-url-fn id)
                                 :errors errors
                                 :values values
                                 :timezone timezone))))

(defn render [& {:keys [accession taxon notes panel-data timezone]}]
  (ui.page/page
    :content (pages.detail/page-content-with-panel
               :content (page-content :accession accession
                                      :taxon taxon
                                      :notes notes
                                      :timezone timezone)
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
      (f/attempt-all [data (validation.i/validate-form-values FormParams form-params)
                      _saved (f/try* (write! db (:user/id viewer)
                                             (fn [tx created-by]
                                               (let [note (note.i/create! tx {:body (:body data)
                                                                              :resource-type resource-type
                                                                              :resource-id id
                                                                              :created-by created-by})]
                                                 (note.activity/create! tx note.activity/created created-by note)
                                                 note))))]
        (render-list db resource timezone)
        (f/when-failed [e]
          (http/failure-partial e "The note could not be saved.")))

      (let [taxon (taxon.i/get-by-id db (:accession/taxon-id resource))
            panel-data (accession.panel/fetch-panel-data db resource)]
        (render :accession resource
                :taxon taxon
                :notes (note.i/get-for-resource db resource-type id)
                :panel-data panel-data
                :timezone timezone)))))

(defn note-handler
  "POST updates one note, DELETE removes it. Both answer with the swapped list."
  [{:keys [::z/context form-params path-params request-method viewer]
    :as _request}]
  (let [{:keys [db resource timezone]} context
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
        (f/attempt-all [data (validation.i/validate-form-values FormParams form-params)
                        _saved (f/try* (write! db (:user/id viewer)
                                               (fn [tx created-by]
                                                 (let [updated (note.i/update! tx note-id {:body (:body data)})]
                                                   (note.activity/create! tx note.activity/updated created-by updated)
                                                   updated))))]
          (render-list db resource timezone)
          (f/when-failed [e]
            (http/failure-partial e "The note could not be saved.")))

        :delete
        (f/attempt-all [_deleted (f/try* (write! db (:user/id viewer)
                                                 (fn [tx created-by]
                                                   (note.activity/create! tx note.activity/deleted created-by note)
                                                   (note.i/delete! tx note-id))))]
          (render-list db resource timezone)
          (f/when-failed [e]
            (http/failure-partial e "The note could not be deleted.")))

        (http/not-found)))))
