(ns sepal.app.routes.propagation.detail
  (:require [failjure.core :as f]
            [sepal.app.authorization :as authz]
            [sepal.app.flash :as flash]
            [sepal.app.http-response :as http]
            [sepal.app.routes.propagation.panel :as propagation.panel]
            [sepal.app.routes.propagation.product :as propagation.product]
            [sepal.app.routes.propagation.routes :as propagation.routes]
            [sepal.app.routes.propagation.shared :as shared]
            [sepal.app.ui.page :as page]
            [sepal.database.interface :as db.i]
            [sepal.propagation.interface :as propagation.i]
            [sepal.propagation.interface.activity :as propagation.activity]
            [sepal.propagation.interface.permission :as propagation.perm]
            [sepal.validation.interface :as validation.i]
            [zodiac.core :as z]))

;; The only write the record itself takes. Products are created from their own
;; action, not by editing this page.
(def FormParams
  [:map {:closed true}
   [:status [:enum :complete :failed]]])

(defn render [& {:keys [panel-data viewer product-kind]}]
  (page/page
    :content [:div {:class "max-w-2xl mx-auto"}
              (propagation.panel/panel-content
                :propagation (:propagation panel-data)
                :parent (:parent panel-data)
                :parent-material (:parent-material panel-data)
                :location (:location panel-data)
                :rootstock (:rootstock panel-data)
                :type-label (:type-label panel-data)
                :status-label (:status-label panel-data)
                :material-products (:material-products panel-data)
                :accession-products (:accession-products panel-data)
                :editable? (authz/user-has-permission? viewer propagation.perm/edit))
              (when (authz/user-has-permission? viewer propagation.perm/edit)
                (propagation.product/product-actions (:propagation panel-data)
                                                     product-kind))]
    :breadcrumbs [[:a {:href (z/url-for propagation.routes/index)} "Propagation"]
                  (shared/parent-name (:parent panel-data) (:parent-material panel-data))]))

(defn update!
  "Close a batch out, and record the activity in the same transaction."
  [db id updated-by data]
  (db.i/with-transaction [tx db]
    (let [propagation (propagation.i/update! tx id data)]
      (propagation.activity/create! tx propagation.activity/updated updated-by propagation)
      propagation)))

(defn handler [{:keys [::z/context form-params request-method viewer]}]
  (let [{:keys [db resource]} context
        id (:propagation/id resource)]
    (case request-method
      :post
      (f/attempt-all [data (validation.i/validate-form-values FormParams form-params)
                      _saved (f/try* (update! db id (:user/id viewer) data))]
        (-> (http/hx-redirect propagation.routes/detail {:id id})
            (flash/success "Propagation updated"))
        (f/when-failed [e]
          (http/failure-flash e
                              (http/hx-redirect propagation.routes/detail {:id id})
                              "Could not save the propagation")))

      (render :panel-data (propagation.panel/fetch-panel-data db resource)
              :viewer viewer
              :product-kind (propagation.product/default-kind-for db resource)))))
