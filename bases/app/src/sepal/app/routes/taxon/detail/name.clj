(ns sepal.app.routes.taxon.detail.name
  (:require [failjure.core :as f]
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
            [sepal.i18n.interface :refer [tr]]
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
         (alert/info (tr "Taxa from the WFO Plantlist are not editable.")))
       (taxon.form/form :action (z/url-for taxon.routes/detail-name {:id (:taxon/id taxon)})
                        :errors errors
                        :read-only read-only?
                        :values values)])))

(defn render [& {:keys [errors taxon values panel-data timezone]}]
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
                                         :activity-count (:activity-count panel-data)
                                         :timezone timezone))
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

(defn- page
  "The tab for `taxon`, as its GET renders it."
  [{:keys [db timezone] :as context} taxon]
  (let [parent (when (:taxon/parent-id taxon)
                 (taxon.i/get-by-id db (:taxon/parent-id taxon)))]
    (render :taxon taxon
            :values {:id (:taxon/id taxon)
                     :name (:taxon/name taxon)
                     :rank (:taxon/rank taxon)
                     :author (:taxon/author taxon)
                     :parent-id (:taxon/id parent)
                     :parent-name (:taxon/name parent)
                     :distribution (:taxon/distribution taxon)
                     :vernacular-names (:taxon/vernacular-names taxon)
                     :parentage (mapv (fn [r]
                                        {:parent-taxon-id (:parentage/parent-taxon-id r)
                                         :parent-name (:parent/name r)
                                         :role (:parentage/role r)})
                                      (taxon.i/list-parentage db (:taxon/id taxon)))}
            :panel-data (taxon.panel/fetch-panel-data context db taxon)
            :timezone timezone)))

(defn get-handler [{:keys [::z/context]}]
  (page context (:resource context)))

(defn post-handler [{:keys [::z/context form-params viewer]}]
  (let [{:keys [db resource]} context
        id (:taxon/id resource)]
    (f/attempt-all [data (validation.i/validate-form-values taxon.form/FormParams form-params)
                    _saved (f/try* (save! db id (:user/id viewer) data))]
      (http/saved (page context (taxon.i/get-by-id db id))
                  (tr "Taxon updated successfully"))
      (f/when-failed [e]
        (http/not-saved e (tr "Could not save the taxon"))))))
