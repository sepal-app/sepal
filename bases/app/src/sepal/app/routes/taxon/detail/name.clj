(ns sepal.app.routes.taxon.detail.name
  (:require [failjure.core :as f]
            [sepal.app.flash :as flash]
            [sepal.app.http-response :as http]
            [sepal.app.routes.taxon.detail.shared :as taxon.shared]
            [sepal.app.routes.taxon.form :as taxon.form]
            [sepal.app.routes.taxon.panel :as taxon.panel]
            [sepal.app.routes.taxon.routes :as taxon.routes]
            [sepal.app.ui.alert :as alert]
            [sepal.app.ui.form :as ui.form]
            [sepal.app.ui.page :as page]
            [sepal.app.ui.pages.detail :as pages.detail]
            [sepal.database.interface :as db.i]
            [sepal.taxon.interface :as taxon.i]
            [sepal.taxon.interface.activity :as taxon.activity]
            [sepal.validation.interface :as validation.i]
            [zodiac.core :as z]))

(defn page-content [& {:keys [errors taxon values footer]}]
  (taxon.shared/page
    :taxon taxon
    :active taxon.shared/name-tab
    :footer footer
    :body
    ;; TODO: Re-enable read-only for WFO-imported taxa when we decide how to handle them
    (let [read-only? false]
      [:div
       (when read-only?
         (alert/info "Taxa from the WFO Plantlist are not editable."))
       (taxon.form/form :action (z/url-for taxon.routes/detail-name {:id (:taxon/id taxon)})
                        :errors errors
                        :read-only read-only?
                        :values values)])))

(defn render [& {:keys [errors taxon values panel-data]}]
  (page/page :content (pages.detail/page-content-with-panel
                        :content (page-content :footer (ui.form/footer :buttons (taxon.form/footer-buttons))
                                               :errors errors
                                               :taxon taxon
                                               :values values)
                        :panel-content (taxon.panel/panel-content
                                         :taxon (:taxon panel-data)
                                         :parent (:parent panel-data)
                                         :stats (:stats panel-data)
                                         :synonyms (:synonyms panel-data)
                                         :notes (:notes panel-data)
                                         :note-count (:note-count panel-data)
                                         :activities (:activities panel-data)
                                         :activity-count (:activity-count panel-data)))
             :breadcrumbs (taxon.shared/breadcrumbs taxon)
             :page-title-buttons (taxon.shared/actions :taxon taxon)))

(defn save! [db taxon-id updated-by data]
  ;; See create.clj: parentage is not a taxon column and UpdateTaxon is
  ;; closed. Always written, not only when non-empty, because clearing every
  ;; slot is how a cross is removed.
  (let [parentage (:parentage data)]
    (db.i/with-transaction [tx db]
      (let [taxon (taxon.i/update! tx taxon-id (dissoc data :parentage))]
        (taxon.i/set-parentage! tx taxon-id parentage :created-by updated-by)
        (taxon.activity/create! tx taxon.activity/updated updated-by taxon)
        taxon))))

(defn handler [{:keys [::z/context form-params request-method viewer]}]
  (let [{:keys [db resource]} context]
    (case request-method
      :post
      (f/attempt-all [data (validation.i/validate-form-values taxon.form/FormParams form-params)
                      saved (f/try* (save! db (:taxon/id resource) (:user/id viewer) data))]
        (-> (http/hx-redirect (z/url-for taxon.routes/detail {:id (:taxon/id saved)}))
            (flash/success "Taxon updated successfully"))
        (f/when-failed [e]
          (http/failure-flash e (http/hx-redirect taxon.routes/detail {:id (:taxon/id resource)}) "Could not save the taxon")))

      :get
      (let [parent (when (:taxon/parent-id resource)
                     (taxon.i/get-by-id db (:taxon/parent-id resource)))
            values {:id (:taxon/id resource)
                    :name (:taxon/name resource)
                    :rank (:taxon/rank resource)
                    :author (:taxon/author resource)
                    :parent-id (:taxon/id parent)
                    :parent-name (:taxon/name parent)
                    :distribution (:taxon/distribution resource)
                    :vernacular-names (:taxon/vernacular-names resource)
                    :parentage (mapv (fn [r]
                                       {:parent-taxon-id (:parentage/parent-taxon-id r)
                                        :parent-name (:parent/name r)
                                        :role (:parentage/role r)})
                                     (taxon.i/list-parentage db (:taxon/id resource)))}
            panel-data (taxon.panel/fetch-panel-data context db resource)]
        (render :taxon resource
                :values values
                :panel-data panel-data)))))
