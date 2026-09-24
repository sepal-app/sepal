(ns sepal.app.routes.propagation.detail
  (:require [failjure.core :as f]
            [sepal.app.authorization :as authz]
            [sepal.app.flash :as flash]
            [sepal.app.http-response :as http]
            [sepal.app.routes.propagation.create :as propagation.create]
            [sepal.app.routes.propagation.form :as propagation.form]
            [sepal.app.routes.propagation.panel :as propagation.panel]
            [sepal.app.routes.propagation.product :as propagation.product]
            [sepal.app.routes.propagation.routes :as propagation.routes]
            [sepal.app.routes.propagation.shared :as shared]
            [sepal.app.ui.form :as ui.form]
            [sepal.app.ui.page :as page]
            [sepal.app.ui.pages.detail :as pages.detail]
            [sepal.app.ui.pages.record :as pages.record]
            [sepal.database.interface :as db.i]
            [sepal.propagation.interface :as propagation.i]
            [sepal.propagation.interface.activity :as propagation.activity]
            [sepal.propagation.interface.permission :as propagation.perm]
            [sepal.propagation.interface.spec :as propagation.spec]
            [sepal.validation.interface :as validation.i]
            [zodiac.core :as z]))

(defn- breadcrumbs [panel-data]
  [[:a {:href (z/url-for propagation.routes/index)} "Propagation"]
   (shared/parent-name (:parent panel-data) (:parent-material panel-data))])

(defn- panel [panel-data & {:keys [actions]}]
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
    :actions actions))

(defn- render-panel-page
  "The record as a full page, for a viewer who cannot edit it."
  [panel-data]
  (page/page
    :content [:div {:class "max-w-2xl mx-auto"} (panel panel-data)]
    :breadcrumbs (breadcrumbs panel-data)))

(defn- form-values
  "The edit form's values, with the display text its pickers need."
  [db propagation panel-data]
  (let [{:keys [parent parent-material location rootstock]} panel-data]
    (merge
      (propagation.create/form-values
        db {:parent-accession-id (:propagation/parent-accession-id propagation)
            :parent-material-id (:propagation/parent-material-id propagation)})
      {:type (:propagation/type propagation)
       :status (:propagation/status propagation)
       :accession-code (:accession/code parent)
       :material-code (:material/code parent-material)
       :rootstock-taxon-id (:propagation/rootstock-taxon-id propagation)
       :taxon-name (:taxon/name rootstock)
       :location-id (:location/id location)
       :location-code (:location/code location)
       :location-name (:location/name location)
       :propagated-on (:propagation/propagated-on propagation)
       :succeeded-on (:propagation/succeeded-on propagation)
       :quantity-started (:propagation/quantity-started propagation)
       :quantity-succeeded (:propagation/quantity-succeeded propagation)
       :notes (:propagation/notes propagation)
       :products? (shared/products? panel-data)})))

(defn- render-edit-page [db propagation panel-data]
  (let [values (form-values db propagation panel-data)]
    (page/page
      :page-title-buttons (shared/actions propagation
                                          (propagation.product/default-kind-for db propagation))
      :content (pages.detail/page-content-with-panel
                 :content (pages.record/page
                            :name (shared/parent-name (:parent panel-data)
                                                      (:parent-material panel-data))
                            :body (propagation.form/form
                                    :action (z/url-for propagation.routes/detail
                                                       {:id (:propagation/id propagation)})
                                    :values values
                                    :types (propagation.i/list-types db)
                                    :statuses (propagation.i/list-statuses db)
                                    :material-items (:material-items values)
                                    :parent-locked? (:products? values))
                            :footer (ui.form/footer
                                      :buttons (propagation.form/footer-buttons)))
                 :panel-content (panel panel-data))
      :breadcrumbs (breadcrumbs panel-data))))

(def FormParams
  [:map {:closed true}
   [:type propagation.spec/type]
   [:status propagation.spec/status]
   [:parent-accession-id [:int {:min 1}]]
   [:parent-material-id {:optional true
                         :decode/form validation.i/empty->nil}
    [:maybe :int]]
   [:rootstock-taxon-id {:optional true
                         :decode/form validation.i/empty->nil}
    [:maybe :int]]
   [:location-id {:optional true
                  :decode/form validation.i/empty->nil}
    [:maybe :int]]
   [:propagated-on {:optional true
                    :decode/form validation.i/empty->nil}
    [:maybe validation.i/date]]
   [:succeeded-on {:optional true
                   :decode/form validation.i/empty->nil}
    [:maybe validation.i/date]]
   [:quantity-started {:optional true
                       :decode/form validation.i/empty->nil}
    [:maybe :int]]
   [:quantity-succeeded {:optional true
                         :decode/form validation.i/empty->nil}
    [:maybe :int]]
   [:notes {:optional true
            :decode/form validation.i/empty->nil}
    [:maybe :string]]])

(defn update!
  "Save the batch, and record the activity in the same transaction."
  [db id updated-by data]
  (db.i/with-transaction [tx db]
    (let [propagation (propagation.i/update! tx id data)]
      (propagation.activity/create! tx propagation.activity/updated updated-by propagation)
      propagation)))

(defn- save! [db propagation viewer form-params]
  (let [id (:propagation/id propagation)
        redirect (http/hx-redirect propagation.routes/detail {:id id})]
    (f/attempt-all [data (validation.i/validate-form-values FormParams form-params)]
      (if (propagation.create/counts-invalid? data)
        (http/validation-errors {:quantity-succeeded [(propagation.create/counts-message)]})
        (let [;; A batch with products keeps its parent, whatever the form sent.
              data (cond-> data
                     (shared/products? (propagation.panel/fetch-panel-data db propagation))
                     (dissoc :parent-accession-id :parent-material-id))]
          (f/attempt-all [_saved (f/try* (update! db id (:user/id viewer) data))]
            (flash/success redirect "Propagation updated")
            (f/when-failed [e]
              (http/failure-flash e redirect "Could not save the propagation")))))
      (f/when-failed [e]
        (http/failure-response e redirect)))))

(defn handler [{:keys [::z/context form-params request-method viewer]}]
  (let [{:keys [db resource]} context
        editor? (authz/user-has-permission? viewer propagation.perm/edit)]
    (cond
      (and (= :post request-method) (not editor?))
      (-> (http/hx-redirect propagation.routes/detail {:id (:propagation/id resource)})
          (flash/error "You don't have permission to edit this propagation"))

      (= :post request-method)
      (save! db resource viewer form-params)

      :else
      (let [panel-data (propagation.panel/fetch-panel-data db resource)]
        (if editor?
          (render-edit-page db resource panel-data)
          (render-panel-page panel-data))))))

(def StatusParams
  [:map {:closed true}
   [:status [:enum :complete :failed]]])

(defn status-handler
  "Close a batch out from the actions menu."
  [{:keys [::z/context form-params viewer]}]
  (let [{:keys [db resource]} context
        id (:propagation/id resource)
        redirect (http/hx-redirect propagation.routes/detail {:id id})]
    (f/attempt-all [data (validation.i/validate-form-values StatusParams form-params)
                    _saved (f/try* (update! db id (:user/id viewer) data))]
      (flash/success redirect "Propagation updated")
      (f/when-failed [e]
        (http/failure-flash e redirect "Could not save the propagation")))))
