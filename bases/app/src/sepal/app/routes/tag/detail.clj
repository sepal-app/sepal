(ns sepal.app.routes.tag.detail
  (:require [ring.middleware.anti-forgery :refer [*anti-forgery-token*]]
            [sepal.app.flash :as flash]
            [sepal.app.http-response :as http]
            [sepal.app.json :as json]
            [sepal.app.routes.tag.form :as tag.form]
            [sepal.app.routes.tag.routes :as tag.routes]
            [sepal.app.ui.form :as ui.form]
            [sepal.app.ui.icons.heroicons :as heroicons]
            [sepal.app.ui.page :as page]
            [sepal.app.ui.tooltip :as tooltip]
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

(defn- delete-button [tag]
  (tooltip/wrap
    [:button {:type "button"
              :class "spl-btn spl-btn--sm spl-btn--icon spl-btn--danger"
              :aria-label "Delete tag"
              :hx-headers (json/js {"X-CSRF-Token" *anti-forgery-token*})
              :hx-delete (z/url-for tag.routes/detail {:id (:tag/id tag)})
              :hx-confirm (str "Delete tag \"" (:tag/name tag) "\"? This removes it from every resource it's linked to.")}
     (heroicons/outline-trash :class "size-4")]
    "Delete tag"
    :side "left"))

(defn render [& {:keys [errors tag values]}]
  (page/page
    :content [:div {:class "max-w-2xl mx-auto"}
              (tag.form/form :action (z/url-for tag.routes/detail {:id (:tag/id tag)})
                             :errors errors
                             :values values)
              (delete-button tag)]
    :footer (ui.form/footer :buttons (tag.form/footer-buttons))
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

(defn delete!
  "Delete the tag and its links, and record the activity in the same
  transaction. tag.i/delete! joins this transaction rather than opening one
  of its own, so a failed activity write rolls the deletes back with it."
  [db id deleted-by tag]
  (try
    (db.i/with-transaction [tx db]
      (tag.i/delete! tx id)
      (tag.activity/create! tx tag.activity/deleted deleted-by tag))
    (catch Exception ex
      (error.i/ex->error ex))))

(defn handler [{:keys [::z/context form-params request-method viewer]}]
  (let [{:keys [db resource]} context
        id (:tag/id resource)
        values {:name (:tag/name resource) :description (:tag/description resource)}]
    (case request-method
      :post
      (let [result (validation.i/validate-form-values FormParams form-params)]
        (if (error.i/error? result)
          (http/validation-errors (validation.i/humanize result))
          (let [saved (update! db id (:user/id viewer) result)]
            (if (error.i/error? saved)
              (http/validation-errors (if (= ::name-taken (error.i/type saved))
                                        {:name [name-taken-message]}
                                        (validation.i/humanize saved)))
              (-> (http/hx-redirect tag.routes/index)
                  (flash/success "Tag updated successfully"))))))

      :delete
      (do (delete! db id (:user/id viewer) resource)
          (http/hx-redirect tag.routes/index))

      (render :tag resource :values values))))
