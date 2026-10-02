(ns sepal.app.routes.taxon.detail.notes
  (:require [failjure.core :as f]
            [sepal.app.http-response :as http]
            [sepal.app.routes.taxon.detail.shared :as taxon.shared]
            [sepal.app.routes.taxon.panel :as taxon.panel]
            [sepal.app.routes.taxon.routes :as taxon.routes]
            [sepal.app.ui.notes :as ui.notes]
            [sepal.app.ui.page :as ui.page]
            [sepal.app.ui.pages.detail :as pages.detail]
            [sepal.database.interface :as db.i]
            [sepal.i18n.interface :refer [tr]]
            [sepal.note.interface :as note.i]
            [sepal.note.interface.activity :as note.activity]
            [sepal.validation.interface :as validation.i]
            [zodiac.core :as z]))

(def resource-type :taxon)

(def FormParams
  [:map {:closed true}
   [:body [:string {:min 1}]]])

(defn- create-url [taxon-id]
  (z/url-for taxon.routes/detail-notes {:id taxon-id}))

(defn- note-url-fn [taxon-id]
  (fn [note-id]
    (z/url-for taxon.routes/detail-note {:id taxon-id :note-id note-id})))

(defn- write! [db created-by f]
  (db.i/with-transaction [tx db]
    (f tx created-by)))

(defn page-content [& {:keys [taxon notes errors values timezone]}]
  (let [id (:taxon/id taxon)]
    (taxon.shared/page
      :taxon taxon
      :active taxon.shared/notes-tab
      :body (ui.notes/notes-body :notes notes
                                 :create-url (create-url id)
                                 :note-url-fn (note-url-fn id)
                                 :errors errors
                                 :values values
                                 :timezone timezone))))

(defn render [& {:keys [taxon notes panel-data timezone]}]
  (ui.page/page
    :content (pages.detail/page-content-with-panel
               :content (page-content :taxon taxon :notes notes :timezone timezone)
               :panel-content (taxon.panel/panel-content
                                :taxon (:taxon panel-data)
                                :parent (:parent panel-data)
                                :stats (:stats panel-data)
                                :synonyms (:synonyms panel-data)
                                :notes (:notes panel-data)
                                :note-count (:note-count panel-data)
                                :activities (:activities panel-data)
                                :activity-count (:activity-count panel-data)
                                :timezone timezone))
    :breadcrumbs (taxon.shared/breadcrumbs taxon)))

(defn- page
  "The tab, as its GET renders it. Every write answers with it, so the panel
  beside the list is current too."
  [{:keys [db resource timezone] :as context}]
  (let [panel-data (taxon.panel/fetch-panel-data context db resource)]
    (render :taxon resource
            :notes (note.i/get-for-resource db resource-type (:taxon/id resource))
            :panel-data panel-data
            :timezone timezone)))

(defn get-handler
  "Renders the tab."
  [{:keys [::z/context]}]
  (page context))

(defn create-handler
  "Creates a note and answers with the page."
  [{:keys [::z/context form-params viewer]}]
  (let [{:keys [db resource]} context
        id (:taxon/id resource)]
    (f/attempt-all [data (validation.i/validate-form-values FormParams form-params)
                    _saved (f/try* (write! db (:user/id viewer)
                                           (fn [tx created-by]
                                             (let [note (note.i/create! tx {:body (:body data)
                                                                            :resource-type resource-type
                                                                            :resource-id id
                                                                            :created-by created-by})]
                                               (note.activity/create! tx note.activity/created created-by note)
                                               note))))]
      (http/saved (page context))
      (f/when-failed [e]
        (http/not-saved e (tr "The note could not be saved."))))))

(defn- resource-note
  "The note in the path, or nil when it belongs to another record."
  [db resource path-params]
  (let [note-id (parse-long (str (:note-id path-params)))
        note (when note-id (note.i/get-by-id db note-id))]
    (when (and note
               (= resource-type (:note/resource-type note))
               (= (:taxon/id resource) (:note/resource-id note)))
      note)))

(defn update-handler
  "Updates one note and answers with the page."
  [{:keys [::z/context form-params path-params viewer]}]
  (let [{:keys [db resource]} context]
    (if-let [note (resource-note db resource path-params)]
      (f/attempt-all [data (validation.i/validate-form-values FormParams form-params)
                      _saved (f/try* (write! db (:user/id viewer)
                                             (fn [tx created-by]
                                               (let [updated (note.i/update! tx (:note/id note) {:body (:body data)})]
                                                 (note.activity/create! tx note.activity/updated created-by updated)
                                                 updated))))]
        (http/saved (page context))
        (f/when-failed [e]
          (http/not-saved e (tr "The note could not be saved."))))
      (http/not-found))))

(defn delete-handler
  "Removes one note and answers with the page."
  [{:keys [::z/context path-params viewer]}]
  (let [{:keys [db resource]} context]
    (if-let [note (resource-note db resource path-params)]
      (f/attempt-all [_deleted (f/try* (write! db (:user/id viewer)
                                               (fn [tx created-by]
                                                 (note.activity/create! tx note.activity/deleted created-by note)
                                                 (note.i/delete! tx (:note/id note)))))]
        (http/saved (page context))
        (f/when-failed [e]
          (http/not-saved e (tr "The note could not be deleted."))))
      (http/not-found))))
