(ns sepal.app.routes.contact.create
  (:require [sepal.app.http-response :refer [found see-other]]
            [sepal.app.params :as params]
            [sepal.app.routes.contact.form :as contact.form]
            [sepal.app.routes.contact.routes :as contact.routes]
            [sepal.app.ui.form :as ui.form]
            [sepal.app.ui.page :as page]
            [sepal.contact.interface :as contact.i]
            [sepal.contact.interface.activity :as contact.activity]
            [sepal.database.interface :as db.i]
            [sepal.error.interface :as error.i]
            [zodiac.core :as z]))

(defn page-content [& {:keys [errors values]}]
  (contact.form/form :action (z/url-for contact.routes/new)
                     :errors errors
                     :values values))

(defn render [& {:keys [errors values]}]
  (page/page :content (page-content :errors errors
                                    :values values)
             :footer (ui.form/footer :buttons (contact.form/footer-buttons))
             :page-title "Create Contact"))

(defn create! [db created-by data]
  (try
    (db.i/with-transaction [tx db]
      (let [contact (contact.i/create! tx data)]
        (tap> (str "contact: " contact))
        (contact.activity/create! tx contact.activity/created created-by contact)
        contact))
    (catch Exception ex
      (error.i/ex->error ex))))

(defn empty-to-nil [v]
  (when (seq v) v))

(def FormParams
  [:map {:closed true}
   [:name :string]
   [:email {:decode/form empty-to-nil} [:maybe :string]]
   [:address [:maybe :string]]
   [:province [:maybe :string]]
   [:postal-code [:maybe :string]]
   [:country [:maybe :string]]
   [:phone [:maybe :string]]
   [:business [:maybe :string]]
   [:notes [:maybe :string]]])

(comment
  (require '[sepal.store.interface :as store.i])
  (require '[sepal.contact.interface.spec :as contact.spec])
  (let [data {:address ""
              :email nil
              :phone ""
              :name "asdas"
              :postal-code ""
              :notes ""
              :business ""
              :province ""
              :country ""}]
    (store.i/coerce contact.spec/CreateContact data))

  ())

(defn handler [{:keys [::z/context form-params request-method viewer]}]
  (let [{:keys [db]} context]
    (case request-method
      :post
      (let [data (params/decode FormParams form-params)
            result (create! db (:user/id viewer) data)]
        (tap> (str "data: " data))
        (tap> (str "result: " result))
        (if-not (error.i/error? result)
          ;; TODO: Add a success message
          (see-other contact.routes/detail {:id (:contact/id result)})
          (-> (found contact.routes/new)
              (assoc :flash {;;:error (error.i/explain result)
                             :values data}))))

      (render :values form-params))))
