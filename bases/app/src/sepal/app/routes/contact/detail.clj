(ns sepal.app.routes.contact.detail
  (:require [sepal.app.flash :as flash]
            [sepal.app.http-response :as http]
            [sepal.app.routes.contact.form :as contact.form]
            [sepal.app.routes.contact.panel :as contact.panel]
            [sepal.app.routes.contact.routes :as contact.routes]
            [sepal.app.ui.form :as ui.form]
            [sepal.app.ui.page :as page]
            [sepal.app.ui.pages.detail :as pages.detail]
            [sepal.contact.interface :as contact.i]
            [sepal.contact.interface.activity :as contact.activity]
            [sepal.database.interface :as db.i]
            [sepal.error.interface :as error.i]
            [sepal.validation.interface :as validation.i]
            [zodiac.core :as z]))

(defn page-content [& {:keys [errors contact values]}]
  (contact.form/form :action (z/url-for contact.routes/detail {:id (:contact/id contact)})
                     :errors errors
                     :values values))

(defn render [& {:keys [errors contact values panel-data]}]
  (page/page :content (pages.detail/page-content-with-panel
                        :content (page-content :errors errors
                                               :contact contact
                                               :values values)
                        :panel (contact.panel/panel-content
                                 :contact (:contact panel-data)
                                 :stats (:stats panel-data)
                                 :activities (:activities panel-data)
                                 :activity-count (:activity-count panel-data)))
             :footer (ui.form/footer :buttons (contact.form/footer-buttons))
             :breadcrumbs [[:a {:href (z/url-for contact.routes/index)} "Contacts"]
                           (:contact/name contact)]))

(defn update! [db contact-id updated-by data]
  (try
    (db.i/with-transaction [tx db]
      (let [contact (contact.i/update! tx contact-id data)]
        (contact.activity/create! tx contact.activity/updated updated-by contact)
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

(defn handler [{:keys [::z/context form-params request-method viewer]}]
  (let [{:keys [db resource]} context
        values {:id (:contact/id resource)
                :name (:contact/name resource)
                :email (:contact/email resource)
                :address (:contact/address resource)
                :province (:contact/province resource)
                :postal-code (:contact/postal-code resource)
                :country (:contact/country resource)
                :phone (:contact/phone resource)
                :business (:contact/business resource)
                :notes (:contact/notes resource)}]
    (case request-method
      :post
      (let [result (validation.i/validate-form-values FormParams form-params)]
        (if (error.i/error? result)
          (http/validation-errors (validation.i/humanize result))
          (let [saved (update! db (:contact/id resource) (:user/id viewer) result)]
            (-> (http/hx-redirect contact.routes/detail {:id (:contact/id saved)})
                (flash/success "Contact updated successfully")))))

      (let [panel-data (contact.panel/fetch-panel-data db resource)]
        (render :contact resource
                :values values
                :panel-data panel-data)))))
