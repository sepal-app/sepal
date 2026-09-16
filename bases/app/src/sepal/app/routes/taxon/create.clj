(ns sepal.app.routes.taxon.create
  (:require [failjure.core :as f]
            [sepal.app.http-response :as http]
            [sepal.app.routes.taxon.form :as taxon.form]
            [sepal.app.routes.taxon.routes :as taxon.routes]
            [sepal.app.ui.form :as form]
            [sepal.app.ui.page :as page]
            [sepal.database.interface :as db.i]
            [sepal.taxon.interface :as taxon.i]
            [sepal.taxon.interface.activity :as taxon.activity]
            [sepal.validation.interface :as validation.i]
            [zodiac.core :as z]))

(defn page-content [& {:keys [errors values]}]
  [:div
   (taxon.form/form :action (z/url-for taxon.routes/new)
                    :errors errors
                    :values values
                    ;; Create only. The edit form must not re-rank a taxon
                    ;; you are only renaming.
                    :guess-rank-url (z/url-for taxon.routes/rank-guess)
                    :parent-suggestion-url (z/url-for taxon.routes/parent-suggestion))])

(defn render [& {:keys [errors flash values]}]
  (page/page :content (page-content :errors errors
                                    :values values)
             :flash flash
             :footer (form/footer :buttons (taxon.form/footer-buttons :on-cancel :back))
             :breadcrumbs [[:a {:href (z/url-for taxon.routes/index)} "Taxa"]
                           "New taxon"]))

(defn create! [db created-by data]
  (db.i/with-transaction [tx db]
    (let [taxon (taxon.i/create! tx data)]
      (taxon.activity/create! tx taxon.activity/created created-by taxon)
      taxon)))

(defn get-handler [{:keys [::z/context params query-params flash]}]
  (let [{:keys [db]} context
        {:keys [field-errors]} flash
        values (merge params (-> flash :values))
        ;; A taxon's "Add a child taxon" names the parent, which is the only
        ;; way this form knows one: the select is searched client-side, so the
        ;; parent's name has to arrive as its option. Same shape as the
        ;; material form's accession-id.
        parent (some->> (get query-params "parent-id")
                        (parse-long)
                        (taxon.i/get-by-id db))
        values (cond-> values
                 parent (assoc :parent-id (:taxon/id parent)
                               :parent-name (:taxon/name parent)))]
    (render :errors field-errors
            :flash flash
            :values values)))

(defn post-handler [{:keys [::z/context form-params viewer]}]
  (let [{:keys [db]} context]
    (f/attempt-all [data (validation.i/validate-form-values taxon.form/FormParams form-params)
                    saved (f/try* (create! db (:user/id viewer) data))]
      (http/hx-redirect (z/url-for taxon.routes/detail {:id (:taxon/id saved)}))
      (f/when-failed [e]
        (http/failure-flash e (http/hx-redirect taxon.routes/new) "Could not create the taxon")))))
