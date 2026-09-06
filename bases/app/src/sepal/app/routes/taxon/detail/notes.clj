(ns sepal.app.routes.taxon.detail.notes
  (:require [sepal.app.html :as html]
            [sepal.app.http-response :as http]
            [sepal.app.routes.taxon.detail.shared :as taxon.shared]
            [sepal.app.routes.taxon.panel :as taxon.panel]
            [sepal.app.routes.taxon.routes :as taxon.routes]
            [sepal.app.ui.notes :as ui.notes]
            [sepal.app.ui.page :as ui.page]
            [sepal.app.ui.pages.detail :as pages.detail]
            [sepal.database.interface :as db.i]
            [sepal.error.interface :as error.i]
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
  (try
    (db.i/with-transaction [tx db]
      (f tx created-by))
    (catch Exception ex
      (error.i/ex->error ex))))

(defn render-list [context db taxon]
  (let [id (:taxon/id taxon)]
    (html/render-partial
      (ui.notes/note-list :notes (note.i/get-for-resource context db resource-type id)
                          :note-url-fn (note-url-fn id)))))

(defn page-content [& {:keys [taxon notes errors values]}]
  (let [id (:taxon/id taxon)]
    (taxon.shared/page
      :taxon taxon
      :active taxon.shared/notes-tab
      :body (ui.notes/notes-body :notes notes
                                 :create-url (create-url id)
                                 :note-url-fn (note-url-fn id)
                                 :errors errors
                                 :values values))))

(defn render [& {:keys [taxon notes panel-data timezone]}]
  (ui.page/page
    :content (pages.detail/page-content-with-panel
               :content (page-content :taxon taxon :notes notes)
               :panel-content (taxon.panel/panel-content
                                :taxon (:taxon panel-data)
                                :parent (:parent panel-data)
                                :stats (:stats panel-data)
                                :notes (:notes panel-data)
                                :note-count (:note-count panel-data)
                                :activities (:activities panel-data)
                                :activity-count (:activity-count panel-data)
                                :timezone timezone))
    :breadcrumbs (taxon.shared/breadcrumbs taxon)))

(defn handler
  [{:keys [::z/context form-params request-method viewer]}]
  (let [{:keys [db resource timezone]} context
        id (:taxon/id resource)]
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

      (let [panel-data (taxon.panel/fetch-panel-data context db resource)]
        (render :taxon resource
                :notes (note.i/get-for-resource context db resource-type id)
                :panel-data panel-data
                :timezone timezone)))))

(defn note-handler
  [{:keys [::z/context form-params path-params request-method viewer]}]
  (let [{:keys [db resource]} context
        note-id (parse-long (str (:note-id path-params)))
        note (when note-id (note.i/get-by-id db note-id))]
    (if-not (and note
                 (= resource-type (:note/resource-type note))
                 (= (:taxon/id resource) (:note/resource-id note)))
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
