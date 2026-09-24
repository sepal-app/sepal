(ns sepal.app.routes.propagation.create
  (:require [failjure.core :as f]
            [sepal.accession.interface :as accession.i]
            [sepal.app.datetime :as datetime]
            [sepal.app.flash :as flash]
            [sepal.app.html :as html]
            [sepal.app.http-response :as http]
            [sepal.app.params :as params]
            [sepal.app.routes.propagation.form :as propagation.form]
            [sepal.app.routes.propagation.routes :as propagation.routes]
            [sepal.app.ui.form :as ui.form]
            [sepal.app.ui.page :as page]
            [sepal.database.interface :as db.i]
            [sepal.material.interface :as material.i]
            [sepal.propagation.interface :as propagation.i]
            [sepal.propagation.interface.activity :as propagation.activity]
            [sepal.propagation.interface.spec :as propagation.spec]
            [sepal.validation.interface :as validation.i]
            [zodiac.core :as z]))

(defn page-content [& {:keys [errors values types material-items today]}]
  (propagation.form/form :action (z/url-for propagation.routes/new)
                         :errors errors
                         :values values
                         :types types
                         :material-items material-items
                         :today today
                         :parent-locked? (some? (:parent-accession-id values))))

(defn render [& {:keys [errors values types material-items today]}]
  (page/page
    :content (page-content :errors errors
                           :today today
                           :values values
                           :types types
                           :material-items material-items)
    :footer (ui.form/footer
              :buttons (propagation.form/footer-buttons))
    :breadcrumbs [[:a {:href (z/url-for propagation.routes/index)} "Propagation"]
                  "New propagation"]))

(defn- counts-message
  "The spec's own message, so the rule and its wording have one definition."
  []
  (:error/message (second propagation.spec/counts-consistent)))

(defn- counts-invalid? [{:keys [quantity-started quantity-succeeded]}]
  (and (some? quantity-started)
       (some? quantity-succeeded)
       (> quantity-succeeded quantity-started)))

(defn form-errors
  "The field errors the form's own rules raise, or nil. Checked here rather
  than left to the database so a propagator entering a germination count in
  May gets a message on the field, not a constraint violation. `today` is the
  garden's date."
  [{:keys [propagated-on succeeded-on] :as data} today]
  (not-empty
    (merge
      (when (counts-invalid? data)
        {:quantity-succeeded [(counts-message)]})
      (when (and propagated-on succeeded-on
                 (neg? (compare succeeded-on propagated-on)))
        {:succeeded-on ["Cannot be before the propagated date"]})
      (validation.i/future-date-errors data [:propagated-on :succeeded-on] today))))

(defn create!
  "The propagation, its activity event and any parent-quantity change are one
  transaction: a failed material update must not leave a propagation behind.

  Nothing is written for the parent automatically. Taking cuttings, seed,
  grafts and divisions by layer removes nothing, and even for a division the
  delta is not derivable -- so the form carries the number and a person fills
  it in."
  [db created-by data]
  (db.i/with-transaction [tx db]
    (let [propagation (propagation.i/create! tx (dissoc data :parent-quantity))]
      (propagation.activity/create! tx propagation.activity/created created-by propagation)
      (when (and (:parent-quantity data)
                 (:parent-material-id data))
        (material.i/update! tx
                            (:parent-material-id data)
                            {:quantity (:parent-quantity data)
                             :reason "divided"}))
      propagation)))

(def FormParams
  [:map {:closed true}
   [:type propagation.spec/type]
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
   [:parent-quantity {:optional true
                      :decode/form validation.i/empty->nil}
    [:maybe :int]]
   [:notes {:optional true
            :decode/form validation.i/empty->nil}
    [:maybe :string]]])

(def ^:private PrefillParams
  "The parent can arrive in the query string, so a create started from a
  plant's screen opens with the accession and the plant already filled in."
  [:map
   [:parent-accession-id {:optional true} :int]
   [:parent-material-id {:optional true} :int]])

(defn material-items
  "The parent plant picker's rows: the accession's material."
  [db accession]
  (when accession
    (for [m (material.i/list-by-accession-id db (:accession/id accession))]
      {:id (:material/id m)
       :text (str (:accession/code accession) "." (:material/code m))})))

(defn form-values
  "The values the form renders, including the display fields the pickers need
  for what is already chosen."
  [db {:keys [parent-accession-id parent-material-id] :as decoded}]
  (let [material (when parent-material-id
                   (material.i/get-by-id db parent-material-id))
        accession-id (or parent-accession-id (:material/accession-id material))
        accession (when accession-id (accession.i/get-by-id db accession-id))
        material-items (material-items db accession)]
    (merge decoded
           {:parent-accession-id accession-id
            :parent-material-id (or (:material/id material) parent-material-id)
            :accession-code (:accession/code accession)
            :material-code (:material/code material)}
           (when material-items {:material-items material-items}))))

(defn handler [{:keys [::z/context form-params query-params request-method viewer]}]
  (let [{:keys [db timezone]} context
        types (propagation.i/list-types db)]
    (case request-method
      :post
      (f/attempt-all [data (validation.i/validate-form-values FormParams form-params)]
        (if-let [errors (form-errors data (str (datetime/today timezone)))]
          (http/validation-errors errors)
          (f/attempt-all [saved (f/try* (create! db (:user/id viewer) data))]
            (-> (http/hx-redirect propagation.routes/detail {:id (:propagation/id saved)})
                (flash/success "Propagation created"))
            (f/when-failed [e]
              (http/failure-flash e
                                  (http/hx-redirect propagation.routes/new)
                                  "Could not create the propagation"))))
        (f/when-failed [e]
          (http/failure-response
            e
            (http/hx-redirect propagation.routes/new))))
      (let [values (form-values db (params/decode PrefillParams query-params))]
        (render :values values
                :today (str (datetime/today timezone))
                :types types
                :material-items (:material-items values))))))

(def ^:private ParentPlantParams
  "A string, because a cleared picker submits an empty one."
  [:map [:parent-accession-id {:optional true} [:maybe :string]]])

(defn parent-plant-handler
  "The parent plant picker for the accession just chosen, swapped in by the
  form."
  [{:keys [::z/context query-params]}]
  (let [{:keys [db]} context
        {:keys [parent-accession-id]} (params/decode ParentPlantParams query-params)
        accession (some->> (some-> parent-accession-id parse-long)
                           (accession.i/get-by-id db))]
    (html/render-partial
      (propagation.form/parent-material-field
        :values {:accession-code (:accession/code accession)}
        :material-items (material-items db accession)))))
