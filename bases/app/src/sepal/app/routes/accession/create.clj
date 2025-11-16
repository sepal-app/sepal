(ns sepal.app.routes.accession.create
  (:require [sepal.accession.interface :as accession.i]
            [sepal.accession.interface.activity :as accession.activity]
            [sepal.app.http-response :refer [found see-other]]
            [sepal.app.params :as params]
            [sepal.app.routes.accession.form :as accession.form]
            [sepal.app.routes.accession.routes :as accession.routes]
            [sepal.app.ui.form :as ui.form]
            [sepal.app.ui.page :as ui.page]
            [sepal.database.interface :as db.i]
            [sepal.error.interface :as error.i]
            [zodiac.core :as z]))

(defn page-content [& {:keys [errors values]}]
  (accession.form/form :action (z/url-for accession.routes/new)
                       :errors errors
                       :values values))

(defn footer-buttons []
  [[:button {:class "btn btn-primary"
             :x-on:click "$dispatch('accession-form:submit')"}
    "Save"]
   [:button {:class "btn btn-secondary"
             :x-on:click "dirty && confirm('Are you sure you want to lose your changes?') && history.back()"}
    "Cancel"]])

(defn render [& {:keys [errors values]}]
  (ui.page/page :content (page-content :errors errors
                                       :values values)
                :footer (ui.form/footer :buttons (footer-buttons))
                :page-title "Create Accession"))

(defn create! [db created-by data]
  (try
    (db.i/with-transaction [tx db]
      (let [acc (accession.i/create! tx data)]
        (accession.activity/create! tx accession.activity/created created-by acc)
        acc))
    (catch Exception ex
      (error.i/ex->error ex))))

(def FormParams
  [:map {:closed true}
   [:code :string]
   [:taxon-id :int]])

(defn handler [{:keys [::z/context form-params request-method viewer]}]
  (let [{:keys [db]} context]
    (case request-method
      :post
      (let [data (params/decode FormParams form-params)
            result (create! db (:user/id viewer) data)]
        (if-not (error.i/error? result)
          ;; TODO: Add a success message
          (see-other accession.routes/detail {:id (:accession/id result)})
          (-> (found accession.routes/new)
              (assoc :flash {;;:error (error.i/explain result)
                             :values data}))))

      (render :values form-params))))
