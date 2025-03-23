(ns sepal.app.routes.taxon.detail.name
  (:require [sepal.app.flash :as flash]
            [sepal.app.http-response :as http]
            [sepal.app.params :as params]
            [sepal.app.routes.taxon.form :as taxon.form]
            [sepal.app.routes.taxon.routes :as taxon.routes]
            [sepal.app.ui.alert :as alert]
            [sepal.app.ui.dropdown :as dropdown]
            [sepal.app.ui.form :as ui.form]
            [sepal.app.ui.page :as page]
            [sepal.app.ui.tabs :as tabs]
            [sepal.database.interface :as db.i]
            [sepal.error.interface :as error.i]
            [sepal.taxon.interface :as taxon.i]
            [sepal.taxon.interface.activity :as taxon.activity]
            [sepal.validation.interface :as validation.i]
            [zodiac.core :as z]))

(defn page-title-buttons [& {:keys []}]
  (dropdown/dropdown "Actions"
                     (dropdown/item (z/url-for taxon.routes/new) "Add a taxon")))

(defn tab-items [& {:keys [taxon]}]
  [{:label "Name"
    :key :name
    :href (z/url-for taxon.routes/detail-name {:id (:taxon/id taxon)})}
   {:label "Media"
    :key :media
    :href (z/url-for taxon.routes/detail-media {:id (:taxon/id taxon)})}])

(defn page-content [& {:keys [errors taxon values]}]
  [:div {:class "flex flex-col gap-2"}
   (tabs/tabs :active :name
              :items (tab-items :taxon taxon))
   (let [read-only? (:taxon/read-only taxon)]
     [:div
      (when read-only?
        (alert/info "Taxa from the WFO Plantlist are not editable."))
      (taxon.form/form :action (z/url-for taxon.routes/detail-name {:id (:taxon/id taxon)})
                       :errors errors
                       :read-only read-only?
                       :values values)])])

(defn render [& {:keys [errors taxon values]}]
  (page/page :content (page-content :errors errors
                                    :taxon taxon
                                    :values values)
             :footer (ui.form/footer :buttons (taxon.form/footer-buttons))
             :page-title (:taxon/name taxon)
             :page-title-buttons (page-title-buttons)))

(defn save! [db taxon-id updated-by data]
  (try
    (db.i/with-transaction [tx db]
      (let [taxon (taxon.i/update! tx taxon-id data)]
        (taxon.activity/create! tx taxon.activity/updated updated-by taxon)
        taxon))
    (catch Exception ex
      (error.i/ex->error ex))))

(defn handler [{:keys [::z/context _flash form-params request-method viewer]}]
  (let [{:keys [db resource]} context]
    (case request-method
      :post
      (let [data (params/decode taxon.form/FormParams form-params)
            result (save! db (:taxon/id resource) (:user/id viewer) data)]
        (if-not (error.i/error? result)
          (http/found taxon.routes/detail {:id (:taxon/id result)})
          (do
            ;; TODO: better error handling
            (tap> (str "ERROR: " (validation.i/humanize result)))
            (-> (http/found taxon.routes/detail {:id (:taxon/id resource)})
                (flash/set-field-errors (validation.i/humanize result))))))

      :get
      (let [parent (when (:taxon/parent-id resource)
                     (taxon.i/get-by-id db (:taxon/parent-id resource)))
            values {:id (:taxon/id resource)
                    :name (:taxon/name resource)
                    :rank (:taxon/rank resource)
                    :author (:taxon/author resource)
                    :parent-id (:taxon/id parent)
                    :parent-name (:taxon/name parent)
                    :vernacular-names (:taxon/vernacular-names resource)}]
        (render :taxon resource
                :values values)))))
