(ns sepal.app.routes.accession.detail.general
  (:require [sepal.accession.interface :as accession.i]
            [sepal.accession.interface.activity :as accession.activity]
            [sepal.app.http-response :as http]
            [sepal.app.params :as params]
            [sepal.app.routes.accession.detail.tabs :as accession.tabs]
            [sepal.app.routes.accession.form :as accession.form]
            [sepal.app.routes.accession.routes :as accession.routes]
            [sepal.app.ui.form :as ui.form]
            [sepal.app.ui.page :as page]
            [sepal.app.ui.tabs :as tabs]
            [sepal.database.interface :as db.i]
            [sepal.error.interface :as error.i]
            [sepal.taxon.interface :as taxon.i]
            [zodiac.core :as z]))

(defn page-content [& {:keys [errors org accession values]}]
  [:div {:class "flex flex-col gap-2"}
   (tabs/tabs (accession.tabs/items :accession accession
                                    :active :general))
   (accession.form/form :action (z/url-for accession.routes/detail-general {:id (:accession/id accession)})
                        :errors errors
                        :org org
                        :values values)])

(defn footer-buttons []
  [[:button {:class "btn btn-primary"
             :x-on:click "$dispatch('accession-form:submit')"}
    "Save"]
   [:button {:class "btn btn-secondary"
             ;; TODO: form.reset() would be better but it doesn't reset the TomSelect of the rank field
             ;; :x-on:click "dirty && confirm('Are you sure you want to lose your changes?') && $refs.taxonForm.reset()"
             :x-on:click "confirm('Are you sure you want to lose your changes?') && location.reload()"}
    "Cancel"]])

(defn render [& {:keys [errors org accession taxon values]}]
  (page/page :content (page-content :errors errors
                                    :org org
                                    :accession accession
                                    :values values
                                    :taxon taxon)
             :footer (ui.form/footer :buttons (footer-buttons))
             :page-title (str (:accession/code accession) " - " (:taxon/name taxon))))

(defn save! [db accession-id updated-by data]
  (try
    (db.i/with-transaction [tx db]
      (let [accession (accession.i/update! tx accession-id data)]
        (accession.activity/create! tx accession.activity/updated updated-by accession)
        accession))
    (catch Exception ex
      (error.i/ex->error ex))))

(def FormParams
  [:map {:closed true}
   [:code :string]
   [:taxon-id :int]])

(defn handler [{:keys [::z/context form-params request-method viewer]}]
  (let [{:keys [db organization resource]} context
        taxon (taxon.i/get-by-id db (:accession/taxon-id resource))
        values (merge {:id (:accession/id resource)
                       :code (:accession/code resource)
                       :taxon-id (:accession/taxon-id resource)
                       :taxon-name (:taxon/name taxon)}
                      (params/decode FormParams form-params))]

    (case request-method
      :post
      (let [result (save! db (:accession/id resource) (:user/id viewer) values)]
        ;; TODO: handle errors
        (if-not (error.i/error? result)
          (http/found accession.routes/detail {:id (:accession/id resource)})
          (-> (http/found accession.routes/detail {:id (:accession/id resource)})
              ;; TODO: The errors needs to be parsed here and return a message
              (assoc :flash {:error result
                             :values form-params}))))

      (render :org organization
              :accession resource
              :taxon taxon
              :values values))))
