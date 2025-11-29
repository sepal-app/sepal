(ns sepal.app.routes.contact.create
  (:require [sepal.app.flash :as flash]
            [sepal.app.http-response :as http]
            [sepal.app.routes.contact.form :as contact.form]
            [sepal.app.routes.contact.routes :as contact.routes]
            [sepal.app.ui.form :as ui.form]
            [sepal.app.ui.page :as page]
            [sepal.contact.interface :as contact.i]
            [sepal.contact.interface.activity :as contact.activity]
            [sepal.database.interface :as db.i]
            [sepal.error.interface :as error.i]
            [sepal.validation.interface :as validation.i]
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

(def FormParams
  [:map {:closed true}
   [:name [:string {:min 1}]]
   [:email {:decode/form validation.i/empty->nil} [:maybe :string]]
   [:address {:decode/form validation.i/empty->nil} [:maybe :string]]
   [:province {:decode/form validation.i/empty->nil} [:maybe :string]]
   [:postal-code {:decode/form validation.i/empty->nil} [:maybe :string]]
   [:country {:decode/form validation.i/empty->nil} [:maybe :string]]
   [:phone {:decode/form validation.i/empty->nil} [:maybe :string]]
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
      (let [result (validation.i/validate-form-values FormParams form-params)]
        (if (error.i/error? result)
          (http/validation-errors (validation.i/humanize result))
          (let [saved (create! db (:user/id viewer) result)]
            (if (error.i/error? saved)
              (http/validation-errors (validation.i/humanize saved))
              (-> (http/hx-redirect contact.routes/detail {:id (:contact/id saved)})
                  (flash/success "Contact created successfully"))))))

      (render :values form-params))))
