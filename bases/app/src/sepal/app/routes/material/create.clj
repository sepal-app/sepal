(ns sepal.app.routes.material.create
  (:require [failjure.core :as f]
            [sepal.accession.interface :as accession.i]
            [sepal.app.codes :as codes]
            [sepal.app.flash :as flash]
            [sepal.app.http-response :as http]
            [sepal.app.routes.material.form :as material.form]
            [sepal.app.routes.material.routes :as material.routes]
            [sepal.app.ui.form :as ui.form]
            [sepal.app.ui.page :as page]
            [sepal.database.interface :as db.i]
            [sepal.location.interface :as location.i]
            [sepal.material.interface :as material.i]
            [sepal.material.interface.activity :as material.activity]
            [sepal.validation.interface :as validation.i]
            [zodiac.core :as z]))

(defn page-content [& {:keys [errors values]}]
  (material.form/form :action (z/url-for material.routes/new)
                      :errors errors
                      :values values))

(defn footer-buttons []
  ;; This one also had Save before Cancel, the opposite order from every other
  ;; form in the app.
  (ui.form/footer-buttons :form-event "material-form" :on-cancel :back))

(defn render [& {:keys [errors values]}]
  ;; Breadcrumbs rather than a page title: the top bar already answers "where
  ;; am I", and a heading repeating it pushed the first field down the page.
  (page/page :content (page-content :errors errors
                                    :values values)
             :footer (ui.form/footer :buttons (footer-buttons))
             :breadcrumbs [[:a {:href (z/url-for material.routes/index)} "Material"]
                           "New material"]))

(defn create! [db created-by data]
  (db.i/with-transaction [tx db]
    (let [acc (material.i/create! tx data)]
      (material.activity/create! tx material.activity/created created-by acc)
      acc)))

(def FormParams
  [:map {:closed true}
   [:code [:string {:min 1}]]
   [:accession-id [:int {:min 1}]]
   [:location-id [:maybe :int]]
   [:quantity [:int {:min 1}]]
   [:status [:string {:min 1}]]
   [:type [:string {:min 1}]]])

(defn handler [{:keys [::z/context form-params query-params request-method viewer]}]
  (let [{:keys [db]} context]
    (case request-method
      :post
      (f/attempt-all [data (validation.i/validate-form-values FormParams form-params)]
        ;; Create is a wall, as it is for accessions.
        (if (codes/rejects? (codes/material db) (:code data))
          (http/validation-errors
            (codes/shape-error (material.i/next-code db
                                                     (:template (codes/material db))
                                                     (:accession-id data))))
          (f/attempt-all [saved (f/try* (create! db (:user/id viewer) data))]
            (-> (http/hx-redirect material.routes/detail {:id (:material/id saved)})
                (flash/success "Material created successfully"))
            (f/when-failed [e]
              (if (codes/unique-violation? e)
                (let [suggestion (material.i/next-code db
                                                       (:template (codes/material db))
                                                       (:accession-id data))]
                  (codes/taken-response (:code data)
                                        suggestion
                                        (material.form/code-input
                                          :value suggestion
                                          :accession-id (:accession-id data))))
                (http/failure-flash e (http/hx-redirect material.routes/new)
                                    "Could not create the material")))))
        (f/when-failed [e]
          (http/failure-flash e (http/hx-redirect material.routes/new)
                              "Could not create the material")))

      ;; The location panel's "Plant here" link names the accession, which is
      ;; the only way this form knows one: the select is searched client-side.
      (let [accession (some->> (get query-params "accession-id")
                               (parse-long)
                               (accession.i/get-by-id db))
            intended (some->> (:accession/intended-location-id accession)
                              (location.i/get-by-id db))]
        (render :values (cond-> {}
                          accession
                          (assoc :accession-id (:accession/id accession)
                                 :accession-code (:accession/code accession)
                                 ;; Arriving from "Plant here" the accession is
                                 ;; known, so the code is prefilled on render.
                                 :code (material.i/next-code
                                         db
                                         (:template (codes/material db))
                                         (:accession/id accession)))

                          intended
                          (assoc :intended-location-label
                                 (format "%s (%s)"
                                         (:location/code intended)
                                         (:location/name intended)))))))))
