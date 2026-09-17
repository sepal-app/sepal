(ns sepal.app.routes.contact.detail
  (:require [failjure.core :as f]
            [sepal.app.authorization :as authz]
            [sepal.app.flash :as flash]
            [sepal.app.http-response :as http]
            [sepal.app.routes.contact.form :as contact.form]
            [sepal.app.routes.contact.panel :as contact.panel]
            [sepal.app.routes.contact.routes :as contact.routes]
            [sepal.app.ui.actions :as ui.actions]
            [sepal.app.ui.form :as ui.form]
            [sepal.app.ui.page :as page]
            [sepal.app.ui.pages.detail :as pages.detail]
            [sepal.app.ui.pages.record :as pages.record]
            [sepal.contact.interface :as contact.i]
            [sepal.contact.interface.activity :as contact.activity]
            [sepal.contact.interface.permission :as contact.perm]
            [sepal.contact.interface.spec :as contact.spec]
            [sepal.database.interface :as db.i]
            [sepal.validation.interface :as validation.i]
            [zodiac.core :as z]))

(defn page-content [& {:keys [errors contact values footer]}]
  (pages.record/page
    :name (:contact/name contact)
    :footer footer
    :body
    (contact.form/form :action (z/url-for contact.routes/detail {:id (:contact/id contact)})
                       :errors errors
                       :values values)))

(defn render [& {:keys [errors contact values panel-data]}]
  (page/page :page-title-buttons (ui.actions/menu
                                   :delete-url (z/url-for contact.routes/delete
                                                          {:id (:contact/id contact)}))
             :content (pages.detail/page-content-with-panel
                        :content (page-content :footer (ui.form/footer :buttons (contact.form/footer-buttons))
                                               :errors errors
                                               :contact contact
                                               :values values)
                        :panel-content (contact.panel/panel-content
                                         :contact (:contact panel-data)
                                         :stats (:stats panel-data)
                                         :activities (:activities panel-data)
                                         :activity-count (:activity-count panel-data)))
             :breadcrumbs [[:a {:href (z/url-for contact.routes/index)} "Contacts"]
                           (:contact/name contact)]))

(defn update! [db contact-id updated-by data]
  (db.i/with-transaction [tx db]
    (let [contact (contact.i/update! tx contact-id data)]
      (contact.activity/create! tx contact.activity/updated updated-by contact)
      contact)))

(def FormParams
  [:map {:closed true}
   [:name [:string {:min 1}]]
   [:email {:decode/form validation.i/empty->nil} [:maybe :string]]
   ;; Optional, unlike the rest: the form only renders it for a contact that
   ;; already has one, so a new contact posts no `address` field at all and a
   ;; closed map would reject the create.
   [:address {:optional true :decode/form validation.i/empty->nil} [:maybe :string]]
   [:address1 {:decode/form validation.i/empty->nil} [:maybe :string]]
   [:address2 {:decode/form validation.i/empty->nil} [:maybe :string]]
   [:city {:decode/form validation.i/empty->nil} [:maybe :string]]
   [:province {:decode/form validation.i/empty->nil} [:maybe :string]]
   [:postal-code {:decode/form validation.i/empty->nil} [:maybe :string]]
   [:country {:decode/form validation.i/empty->nil} [:maybe :string]]
   [:phone {:decode/form validation.i/empty->nil} [:maybe :string]]
   [:business [:maybe :string]]
   [:type {:decode/form validation.i/empty->nil} [:maybe contact.spec/type]]
   [:notes [:maybe :string]]])

(defn render-panel-page
  "Render the panel view as a full page for read-only users."
  [& {:keys [contact panel-data]}]
  (page/page
    :breadcrumbs [[:a {:href (z/url-for contact.routes/index)} "Contacts"]
                  (:contact/name contact)]
    :content [:div {:class "max-w-2xl mx-auto"}
              (contact.panel/panel-content
                :contact (:contact panel-data)
                :stats (:stats panel-data)
                :activities (:activities panel-data)
                :activity-count (:activity-count panel-data))]))

(defn handler [{:keys [::z/context form-params request-method viewer]}]
  (let [{:keys [db resource]} context
        id (:contact/id resource)]
    ;; Readers see panel view as full page
    (if (not (authz/user-has-permission? viewer contact.perm/edit))
      (let [panel-data (contact.panel/fetch-panel-data db resource)]
        (render-panel-page :contact resource :panel-data panel-data))
      ;; Editors/Admins see the form
      (let [values {:id id
                    :name (:contact/name resource)
                    :email (:contact/email resource)
                    :address (:contact/address resource)
                    :address1 (:contact/address1 resource)
                    :address2 (:contact/address2 resource)
                    :city (:contact/city resource)
                    :province (:contact/province resource)
                    :postal-code (:contact/postal-code resource)
                    :country (:contact/country resource)
                    :phone (:contact/phone resource)
                    :business (:contact/business resource)
                    :type (:contact/type resource)
                    :notes (:contact/notes resource)}]
        (case request-method
          :post
          (f/attempt-all [data (validation.i/validate-form-values FormParams form-params)
                          saved (f/try* (update! db id (:user/id viewer) data))]
            (-> (http/hx-redirect contact.routes/detail {:id (:contact/id saved)})
                (flash/success "Contact updated successfully"))
            (f/when-failed [e]
              (http/failure-flash e (http/hx-redirect contact.routes/detail {:id id}) "Could not save the contact")))

          (let [panel-data (contact.panel/fetch-panel-data db resource)]
            (render :contact resource
                    :values values
                    :panel-data panel-data)))))))
