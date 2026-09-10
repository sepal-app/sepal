(ns sepal.app.routes.material.detail.notes
  (:require [sepal.activity.interface :as activity.i]
            [sepal.app.html :as html]
            [sepal.app.http-response :as http]
            [sepal.app.routes.material.detail.shared :as material.shared]
            [sepal.app.routes.material.panel :as material.panel]
            [sepal.app.routes.material.routes :as material.routes]
            [sepal.app.ui.notes :as ui.notes]
            [sepal.app.ui.page :as ui.page]
            [sepal.app.ui.pages.detail :as pages.detail]
            [sepal.database.interface :as db.i]
            [sepal.error.interface :as error.i]
            [sepal.note.interface :as note.i]
            [sepal.note.interface.activity :as note.activity]
            [sepal.validation.interface :as validation.i]
            [zodiac.core :as z]))

(def resource-type :material)

(def FormParams
  [:map {:closed true}
   [:body [:string {:min 1}]]])

(defn- create-url [material-id]
  (z/url-for material.routes/detail-notes {:id material-id}))

(defn- note-url-fn [material-id]
  (fn [note-id]
    (z/url-for material.routes/detail-note {:id material-id :note-id note-id})))

(defn- write! [db created-by f]
  (try
    (db.i/with-transaction [tx db]
      (f tx created-by))
    (catch Exception ex
      (error.i/ex->error ex))))

(defn render-list [db material]
  (let [id (:material/id material)]
    (html/render-partial
      (ui.notes/note-list :notes (note.i/get-for-resource db resource-type id)
                          :note-url-fn (note-url-fn id)))))

(defn page-content [& {:keys [material accession taxon notes errors values]}]
  (let [id (:material/id material)]
    (material.shared/page
      :material material
      :accession accession
      :taxon taxon
      :active material.shared/notes-tab
      :body (ui.notes/notes-body :notes notes
                                 :create-url (create-url id)
                                 :note-url-fn (note-url-fn id)
                                 :errors errors
                                 :values values))))

(defn render [& {:keys [material accession taxon notes panel-data timezone]}]
  (ui.page/page
    :content (pages.detail/page-content-with-panel
               :content (page-content :material material
                                      :accession accession
                                      :taxon taxon
                                      :notes notes)
               :panel-content (material.panel/panel-content
                                :material (:material panel-data)
                                :accession (:accession panel-data)
                                :taxon (:taxon panel-data)
                                :location (:location panel-data)
                                :history (:history panel-data)
                                :notes (:notes panel-data)
                                :note-count (:note-count panel-data)
                                :activities (:activities panel-data)
                                :activity-count (:activity-count panel-data)
                                :timezone timezone))
    :breadcrumbs (material.shared/breadcrumbs :accession accession
                                              :material material
                                              :taxon taxon)))

(defn handler
  [{:keys [::z/context form-params request-method viewer]}]
  (let [{:keys [db resource timezone]} context
        id (:material/id resource)]
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
              (render-list db resource)))))

      (let [panel-data (material.panel/fetch-panel-data db resource)]
        (render :material resource
                :accession (:accession panel-data)
                :taxon (:taxon panel-data)
                :notes (note.i/get-for-resource db resource-type id)
                :panel-data panel-data
                :timezone timezone)))))

(defn note-handler
  [{:keys [::z/context form-params path-params request-method viewer]}]
  (let [{:keys [db resource]} context
        note-id (parse-long (str (:note-id path-params)))
        note (when note-id (note.i/get-by-id db note-id))]
    (if-not (and note
                 (= resource-type (:note/resource-type note))
                 (= (:material/id resource) (:note/resource-id note)))
      (http/not-found)
      (case request-method
        :post
        (let [result (validation.i/validate-form-values FormParams form-params)]
          (if (error.i/error? result)
            (http/validation-errors (validation.i/humanize result))
            (let [saved (write! db (:user/id viewer)
                                (fn [tx created-by]
                                  (let [updated (note.i/update! tx note-id {:body (:body result)})]
                                    (note.activity/create! tx note.activity/updated created-by updated
                                                           (activity.i/changed-fields note updated))
                                    updated)))]
              (if (error.i/error? saved)
                (http/validation-errors (validation.i/humanize saved))
                (render-list db resource)))))

        :delete
        (let [deleted (write! db (:user/id viewer)
                              (fn [tx created-by]
                                (note.activity/create! tx note.activity/deleted created-by note)
                                (note.i/delete! tx note-id)))]
          (if (error.i/error? deleted)
            (http/validation-errors (validation.i/humanize deleted))
            (render-list db resource)))

        (http/not-found)))))
