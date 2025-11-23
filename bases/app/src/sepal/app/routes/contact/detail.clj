(ns sepal.app.routes.contact.detail
  (:require [sepal.app.http-response :as http]
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

(defn page-content [& {:keys [errors contact values]}]
  (contact.form/form :action (z/url-for contact.routes/detail {:id (:contact/id contact)})
                     :errors errors
                     :values values))

(defn render [& {:keys [errors contact values]}]
  (page/page :content (page-content :errors errors
                                    :contact contact
                                    :values values)
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
   [:name :string]
   [:email [:maybe :string]]
   [:address [:maybe :string]]
   [:province [:maybe :string]]
   [:postal-code [:maybe :string]]
   [:country [:maybe :string]]
   [:phone [:maybe :string]]
   [:business [:maybe :string]]
   [:notes [:maybe :string]]])

(defn handler [{:keys [::z/context form-params request-method viewer]}]
  (let [{:keys [db resource]} context
        error nil
        values (merge {:id (:contact/id resource)
                       :name (:contact/name resource)
                       :email (:contact/email resource)
                       :address (:contact/address resource)
                       :province (:contact/province resource)
                       :postal-code (:contact/postal-code resource)
                       :country (:contact/country resource)
                       :phone (:contact/phone resource)
                       :business (:contact/business resource)
                       :notes (:contact/notes resource)}
                      (params/decode FormParams form-params))]

    (case request-method
      :post
      (let [result (update! db (:contact/id resource) (:user/id viewer) values)]
        ;; TODO: handle errors
        (if-not (error.i/error? result)
          (http/found contact.routes/detail {:id (:contact/id resource)})
          (-> (http/found contact.routes/detail)
              (assoc :flash {:error error
                             :values values}))))

      (render :contact resource
              :values values))))
