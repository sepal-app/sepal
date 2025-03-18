(ns sepal.app.routes.material.create
  (:require [sepal.app.http-response :refer [found see-other]]
            [sepal.app.params :as params]
            [sepal.app.routes.material.form :as material.form]
            [sepal.app.routes.material.routes :as material.routes]
            [sepal.app.ui.form :as ui.form]
            [sepal.app.ui.page :as page]
            [sepal.database.interface :as db.i]
            [sepal.error.interface :as error.i]
            [sepal.material.interface :as material.i]
            [sepal.material.interface.activity :as material.activity]
            [zodiac.core :as z]))

(defn page-content [& {:keys [errors values]}]
  (material.form/form :action (z/url-for material.routes/new)
                      :errors errors
                      :values values))

(defn footer-buttons []
  [[:button {:class "btn btn-primary"
             :x-on:click "$dispatch('material-form:submit')"}
    "Save"]
   [:button {:class "btn btn-secondary"
             :x-on:click "dirty && confirm('Are you sure you want to lose your changes?') && history.back()"}
    "Cancel"]])

(defn render [& {:keys [errors values]}]
  (page/page :content (page-content :errors errors
                                    :values values)
             :footer (ui.form/footer :buttons (footer-buttons))
             :page-title "Create material"))

(defn create! [db created-by data]
  (try
    (db.i/with-transaction [tx db]
      (let [acc (material.i/create! tx data)]
        (material.activity/create! tx material.activity/created created-by acc)
        acc))
    (catch Exception ex
      (error.i/ex->error ex))))

(def FormParams
  [:map {:closed true}
   [:code :string]
   [:accession-id :int]
   [:location-id [:maybe :int]]
   [:quantity :int]
   [:status :string]
   [:type :string]])

(defn handler [{:keys [::z/context form-params request-method viewer]}]
  (let [{:keys [db]} context]
    (case request-method
      :post
      (let [data (params/decode FormParams form-params)
            result (create! db (:user/id viewer) data)]
        ;; TODO: Better error handling
        (if-not (error.i/error? result)
          (see-other material.routes/detail {:id (:material/id result)})
          (-> (found material.routes/new)
              (assoc :flash {;;:error (error.i/explain result)
                             :values data}))))
      (render))))
