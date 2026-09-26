(ns sepal.app.routes.tag.detail
  (:require [failjure.core :as f]
            [sepal.app.flash :as flash]
            [sepal.app.http-response :as http]
            [sepal.app.routes.tag.form :as tag.form]
            [sepal.app.routes.tag.panel :as tag.panel]
            [sepal.app.routes.tag.routes :as tag.routes]
            [sepal.app.ui.actions :as ui.actions]
            [sepal.app.ui.form :as ui.form]
            [sepal.app.ui.page :as page]
            [sepal.app.ui.pages.detail :as pages.detail]
            [sepal.app.ui.pages.record :as pages.record]
            [sepal.database.interface :as db.i]
            [sepal.error.interface :as error.i]
            [sepal.tag.interface :as tag.i]
            [sepal.tag.interface.activity :as tag.activity]
            [sepal.validation.interface :as validation.i]
            [zodiac.core :as z]))

(def FormParams
  [:map {:closed true}
   [:name [:string {:min 1}]]
   [:description {:decode/form validation.i/empty->nil} [:maybe :string]]])

(defn page-content [& {:keys [errors tag values footer]}]
  (pages.record/page
    :name (:tag/name tag)
    :footer footer
    :body
    (tag.form/form :action (z/url-for tag.routes/detail {:id (:tag/id tag)})
                   :errors errors
                   :values values)))

(defn render [& {:keys [errors tag values panel-data timezone]}]
  (page/page :page-title-buttons (ui.actions/menu
                                   :delete-url (z/url-for tag.routes/delete {:id (:tag/id tag)}))
             :content (pages.detail/page-content-with-panel
                        :content (page-content :footer (ui.form/footer :buttons (tag.form/footer-buttons))
                                               :errors errors
                                               :tag tag
                                               :values values)
                        :panel-content (tag.panel/panel-content
                                         :tag (:tag panel-data)
                                         :stats (:stats panel-data)
                                         :activities (:activities panel-data)
                                         :activity-count (:activity-count panel-data)
                                         :timezone timezone))
             :breadcrumbs [[:a {:href (z/url-for tag.routes/index)} "Tags"] (:tag/name tag)]))

(def ^:private name-taken-message
  "A tag with this name already exists.")

(defn update!
  "Rename or redescribe, and record the activity in the same transaction, the
  way routes/location/detail does.

  The try/catch is not defensive padding. `tag.name` is `unique collate
  nocase` and store.i/update! does not catch SQLiteException, so renaming a
  tag to a name another tag already holds threw straight out of the handler --
  a 500 on the very page that lists both names. The constraint failure is
  reported as an error on the field that caused it rather than as the empty
  422 a bare ex->error humanizes to."
  [db id updated-by data]
  (try
    (db.i/with-transaction [tx db]
      (let [tag (tag.i/update! tx id data)]
        (tag.activity/create! tx tag.activity/updated updated-by tag)
        tag))
    (catch org.sqlite.SQLiteException ex
      (if (re-find #"UNIQUE constraint failed" (ex-message ex))
        (error.i/error ::name-taken name-taken-message)
        (error.i/ex->error ex)))
    (catch Exception ex
      (error.i/ex->error ex))))

(defn handler [{:keys [::z/context form-params request-method viewer]}]
  (let [{:keys [db resource timezone]} context
        id (:tag/id resource)
        values {:name (:tag/name resource) :description (:tag/description resource)}]
    (case request-method
      :post
      (f/attempt-all [data (validation.i/validate-form-values FormParams form-params)
                      _saved (f/try* (update! db id (:user/id viewer) data))]
        (-> (http/hx-redirect tag.routes/index)
            (flash/success "Tag updated successfully"))
        (f/when-failed [e]
          ;; The one failure this route classifies itself: a duplicate name is a
          ;; field error, not a generic save failure.
          (if (error.i/error? e ::name-taken)
            (http/validation-errors {:name [name-taken-message]})
            (http/failure-flash e (http/hx-redirect tag.routes/index) "Could not save the tag"))))

      (render :tag resource
              :values values
              :panel-data (tag.panel/fetch-panel-data db resource)
              :timezone timezone))))
