(ns sepal.app.routes.location.detail.observations
  (:require [failjure.core :as f]
            [sepal.app.datetime :as datetime]
            [sepal.app.html :as html]
            [sepal.app.http-response :as http]
            [sepal.app.routes.location.detail.shared :as location.shared]
            [sepal.app.routes.location.panel :as location.panel]
            [sepal.app.routes.location.routes :as location.routes]
            [sepal.app.ui.observations :as ui.observations]
            [sepal.app.ui.page :as ui.page]
            [sepal.app.ui.pages.detail :as pages.detail]
            [sepal.database.interface :as db.i]
            [sepal.error.interface :as error.i]
            [sepal.observation.interface :as observation.i]
            [sepal.observation.interface.activity :as observation.activity]
            [sepal.validation.interface :as validation.i]
            [zodiac.core :as z]))

(def resource-type :location)

(def FormParams
  [:map {:closed true}
   [:type [:string {:min 1}]]
   [:value {:decode/form validation.i/empty->nil} [:maybe :string]]
   [:observed_on [:re #"^\d{4}-\d{2}-\d{2}$"]]
   [:observed_by {:decode/form validation.i/empty->nil} [:maybe :string]]
   [:next_check_on {:decode/form validation.i/empty->nil} [:maybe :string]]
   [:note {:decode/form validation.i/empty->nil} [:maybe :string]]])

(def ^:private future-date-message "Cannot be a future date")

(defn- not-in-the-future
  "An observation records what you saw, so it cannot be dated ahead. A
  next_check_on in the past is fine -- that is how you backfill. `today` is
  the garden's date, so a garden ahead of the server can record today."
  [observed-on today]
  (when (pos? (compare observed-on today))
    (error.i/error ::future-observed-on future-date-message)))

(defn- future-date-error
  "The OOB error swap for a rejected observed_on.

  `id-suffix` has to match the field's own id: the create form's
  observed_on has no suffix, but each observation's inline edit form
  suffixes every field's id with its own observation id so the same field
  name can appear more than once on the page. A bare :observed_on here
  would paint an edit form's error onto the create form's error list, or
  onto another item's hidden one, instead of the field the curator is
  actually looking at."
  [id-suffix]
  (http/validation-errors
    {(keyword (str "observed_on" (when id-suffix (str "-" id-suffix))))
     [future-date-message]}))

(defn- value-options-by-type
  "Every observation_value, grouped by type and shaped for the Value field's
  Alpine data: `{type -> [{:value code :label label}]}`."
  [db]
  (->> (observation.i/list-values db)
       (group-by :observation-value/type)
       (reduce-kv (fn [m t values]
                    (assoc m t (mapv (fn [{:observation-value/keys [code label]}]
                                       {:value code :label label})
                                     values)))
                  {})))

(defn- create-url [location-id]
  (z/url-for location.routes/detail-observations {:id location-id}))

(defn- observation-url-fn [location-id]
  (fn [observation-id]
    (z/url-for location.routes/detail-observation {:id location-id :observation-id observation-id})))

(defn- write! [db created-by f]
  (db.i/with-transaction [tx db]
    (f tx created-by)))

(defn render-list [db location timezone]
  (let [id (:location/id location)]
    (html/render-partial
      (ui.observations/observation-list :observations (observation.i/get-for-resource db resource-type id)
                                        :observation-url-fn (observation-url-fn id)
                                        :type-options (observation.i/list-types db)
                                        :value-options-by-type (value-options-by-type db)
                                        :today (str (datetime/today timezone))))))

(defn page-content [& {:keys [location observations errors values db timezone]}]
  (let [id (:location/id location)]
    (location.shared/page
      :location location
      :active location.shared/observations-tab
      :body (ui.observations/observations-body :observations observations
                                               :create-url (create-url id)
                                               :observation-url-fn (observation-url-fn id)
                                               :errors errors
                                               :values values
                                               :type-options (observation.i/list-types db)
                                               :value-options-by-type (value-options-by-type db)
                                               :today (str (datetime/today timezone))))))

(defn render [& {:keys [db location observations panel-data timezone]}]
  (ui.page/page
    :content (pages.detail/page-content-with-panel
               :content (page-content :db db
                                      :location location
                                      :observations observations
                                      :timezone timezone)
               :panel-content (location.panel/panel-content
                                :location (:location panel-data)
                                :stats (:stats panel-data)
                                :awaiting (:awaiting panel-data)
                                :moved-out (:moved-out panel-data)
                                :activities (:activities panel-data)
                                :activity-count (:activity-count panel-data)
                                :timezone timezone))
    :breadcrumbs (location.shared/breadcrumbs location)))

(defn- observation-data [id data created-by]
  {:resource-type resource-type
   :resource-id id
   :type (:type data)
   :value (:value data)
   :observed-on (:observed_on data)
   :observed-by (:observed_by data)
   :next-check-on (:next_check_on data)
   :note (:note data)
   :created-by created-by})

(defn handler
  [{:keys [::z/context form-params request-method viewer]}]
  (let [{:keys [db resource timezone]} context
        id (:location/id resource)]
    (case request-method
      :post
      (f/attempt-all [data (validation.i/validate-form-values FormParams form-params)
                      _future-check (not-in-the-future (:observed_on data) (str (datetime/today timezone)))
                      _saved (f/try* (write! db (:user/id viewer)
                                             (fn [tx created-by]
                                               (let [observation (observation.i/create! tx (observation-data id data created-by))]
                                                 (observation.activity/create! tx observation.activity/created created-by observation)
                                                 observation))))]
        (render-list db resource timezone)
        (f/when-failed [e]
          ;; The one failure this route classifies itself: a future date is a
          ;; field error, not a generic save failure.
          (if (error.i/error? e ::future-observed-on)
            (future-date-error nil)
            (http/failure-partial e "The observation could not be saved."))))

      (let [panel-data (location.panel/fetch-panel-data db resource)]
        (render :db db
                :location resource
                :observations (observation.i/get-for-resource db resource-type id)
                :panel-data panel-data
                :timezone timezone)))))

(defn observation-handler
  [{:keys [::z/context form-params path-params request-method viewer]}]
  (let [{:keys [db resource timezone]} context
        observation-id (parse-long (str (:observation-id path-params)))
        observation (when observation-id (observation.i/get-by-id db observation-id))]
    (if-not (and observation
                 (= resource-type (:observation/resource-type observation))
                 (= (:location/id resource) (:observation/resource-id observation)))
      (http/not-found)
      (case request-method
        :post
        (f/attempt-all [data (validation.i/validate-form-values FormParams form-params)
                        _future-check (not-in-the-future (:observed_on data) (str (datetime/today timezone)))
                        _saved (f/try* (write! db (:user/id viewer)
                                               (fn [tx created-by]
                                                 (let [updated (observation.i/update! tx observation-id
                                                                                      {:type (:type data)
                                                                                       :value (:value data)
                                                                                       :observed-on (:observed_on data)
                                                                                       :observed-by (:observed_by data)
                                                                                       :next-check-on (:next_check_on data)
                                                                                       :note (:note data)})]
                                                   (observation.activity/create! tx observation.activity/updated created-by updated)
                                                   updated))))]
          (render-list db resource timezone)
          (f/when-failed [e]
            (if (error.i/error? e ::future-observed-on)
              (future-date-error observation-id)
              (http/failure-partial e "The observation could not be saved."
                                    :id-suffix observation-id))))

        :delete
        (f/attempt-all [_deleted (f/try* (write! db (:user/id viewer)
                                                 (fn [tx created-by]
                                                   (observation.activity/create! tx observation.activity/deleted created-by observation)
                                                   (observation.i/delete! tx observation-id))))]
          (render-list db resource timezone)
          (f/when-failed [e]
            (http/failure-partial e "The observation could not be deleted.")))

        (http/not-found)))))
