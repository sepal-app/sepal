(ns sepal.app.routes.material.bulk
  "Acting on many materials at once from the material list."
  (:require [failjure.core :as f]
            [sepal.app.bulk :as bulk]
            [sepal.app.http-response :as http]
            [sepal.app.json :as json]
            [sepal.app.routes.location.routes :as location.routes]
            [sepal.app.routes.material.routes :as material.routes]
            [sepal.app.ui.bulk :as ui.bulk]
            [sepal.app.ui.combobox :as combobox]
            [sepal.app.ui.form :as ui.form]
            [sepal.database.interface :as db.i]
            [sepal.i18n.interface :refer [tr trc trn]]
            [sepal.location.interface :as location.i]
            [sepal.material.interface :as material.i]
            [sepal.material.interface.activity :as material.activity]
            [sepal.material.interface.spec :as material.spec]
            [sepal.validation.interface :as validation.i]
            [zodiac.core :as z]))

(def StatusParams
  [:map {:closed true}
   [:ids bulk/Ids]
   [:status (into [:enum {:decode/form keyword}] (rest material.spec/status))]
   [:reason {:optional true :decode/form validation.i/empty->nil} [:maybe :string]]])

(def ^:private status-suffix "bulk-status")

(defn- reason-required [{:keys [status reason]}]
  (when (and (not (material.spec/living? status)) (nil? reason))
    (http/field-errors {:reason [(tr "Choose a reason. It is recorded in each material's history.")]})))

(defn- set-status!
  "Writes `status` to each material not already at it. A non-living status
  sets the quantity to 0, which records the change with `reason`."
  [db user-id ids status reason]
  (db.i/with-transaction [tx db]
    (let [materials (material.i/get-by-ids tx ids)
          targets (remove #(= status (:material/status %)) materials)
          data (cond-> {:status status}
                 (not (material.spec/living? status)) (assoc :quantity 0 :reason reason))]
      (doseq [m targets]
        (material.activity/create! tx material.activity/updated user-id
                                   (material.i/update! tx (:material/id m) data)))
      {:changed (count targets) :skipped (- (count materials) (count targets))})))

(defn status-handler [{:keys [::z/context form-params viewer]}]
  (let [{:keys [db]} context]
    (f/attempt-all [_ids (bulk/check-ids form-params)
                    data (validation.i/validate-form-values StatusParams form-params)
                    _reason (reason-required data)
                    _all (bulk/require-all db :material (:ids data))
                    {:keys [changed skipped]} (f/try* (set-status! db (:user/id viewer) (:ids data)
                                                                   (:status data) (:reason data)))]
      (bulk/applied (bulk/result-message
                      (trn "Changed %1 material." "Changed %1 materials." changed changed)
                      (trn "%1 already had that status." "%1 already had that status." skipped skipped)
                      skipped))
      (f/when-failed [e]
        (http/not-saved e (tr "Nothing was changed.") :id-suffix status-suffix)))))

(defn- status-dialog [reasons]
  (ui.bulk/dialog
    :id "bulk-material-status"
    :title (tr "Change status")
    :action (z/url-for material.routes/bulk-status)
    :confirm-one (tr "Change %1 material")
    :confirm-other (tr "Change %1 materials")
    :body
    [:div {:class "spl-form-fields"
           :x-data (str "{ status: 'alive', reasons: " (json/js material.spec/default-reasons) " }")
           :x-init "$watch('status', s => $refs.reason.value = reasons[s] || '')"}
     (ui.form/field :label (tr "Status")
                    :name (str "status-" status-suffix)
                    :input [:select {:name "status"
                                     :id (str "status-" status-suffix)
                                     :class "spl-input spl-select"
                                     :x-model "status"}
                            (for [s (rest material.spec/status)]
                              [:option {:value (name s)} (tr (material.spec/status-labels s))])])
     ;; Shown for the statuses with a default reason: the non-living ones.
     [:div {:x-show "reasons.hasOwnProperty(status)"}
      (ui.form/field :label (tr "Reason for change")
                     :name (str "reason-" status-suffix)
                     :help (tr "Recorded in each material's history. Quantity is set to 0.")
                     :input [:select {:name "reason"
                                      :id (str "reason-" status-suffix)
                                      :x-ref "reason"
                                      :aria-describedby (ui.form/description-id (str "reason-" status-suffix))
                                      :class "spl-input spl-select w-full"}
                             [:option {:value ""} (tr "None")]
                             (for [{:material-change-reason/keys [code label]} reasons]
                               [:option {:value code} (trc "material_change_reason" label)])])]]))

(def MoveParams
  [:map {:closed true}
   [:ids bulk/Ids]
   [:location-id [:int {:min 1}]]
   [:reason {:optional true :decode/form validation.i/empty->nil} [:maybe :string]]])

(def ^:private move-suffix "bulk-move")

;; A flash rather than a field error: the combobox's error list is keyed by
;; its field name, which the material form's location field shares.
(defn- location-exists [db location-id]
  (when-not (location.i/get-by-id db location-id)
    (bulk/refused (tr "Choose a location."))))

(defn- move! [db user-id ids location-id reason]
  (db.i/with-transaction [tx db]
    (let [materials (material.i/get-by-ids tx ids)
          targets (remove #(= location-id (:material/location-id %)) materials)]
      (doseq [m targets]
        (material.activity/create! tx material.activity/updated user-id
                                   (material.i/update! tx (:material/id m)
                                                       {:location-id location-id :reason reason})))
      {:changed (count targets) :skipped (- (count materials) (count targets))})))

(defn move-handler [{:keys [::z/context form-params viewer]}]
  (let [{:keys [db]} context]
    (f/attempt-all [_ids (bulk/check-ids form-params)
                    data (validation.i/validate-form-values MoveParams form-params)
                    _location (location-exists db (:location-id data))
                    _all (bulk/require-all db :material (:ids data))
                    {:keys [changed skipped]} (f/try* (move! db (:user/id viewer) (:ids data)
                                                             (:location-id data) (:reason data)))]
      (bulk/applied (bulk/result-message
                      (trn "Moved %1 material." "Moved %1 materials." changed changed)
                      (trn "%1 was already there." "%1 were already there." skipped skipped)
                      skipped))
      (f/when-failed [e]
        (http/not-saved e (tr "Nothing was moved.") :id-suffix move-suffix)))))

(defn- move-dialog [reasons]
  (ui.bulk/dialog
    :id "bulk-material-move"
    :title (tr "Move")
    :action (z/url-for material.routes/bulk-move)
    :confirm-one (tr "Move %1 material")
    :confirm-other (tr "Move %1 materials")
    :body
    [:div {:class "spl-form-fields"}
     (combobox/combobox :name "location-id"
                        :label (tr "Location")
                        :url (z/url-for location.routes/index)
                        :required true)
     (ui.form/field :label (tr "Reason for change")
                    :name (str "reason-" move-suffix)
                    :input [:select {:name "reason"
                                     :id (str "reason-" move-suffix)
                                     :class "spl-input spl-select w-full"}
                            [:option {:value ""} (tr "None")]
                            (for [{:material-change-reason/keys [code label]} reasons]
                              [:option {:value code} (trc "material_change_reason" label)])])]))

(defn action-bar
  "The bar and dialogs the material list shows to someone who can edit."
  [& {:keys [reasons]}]
  (list
    (ui.bulk/action-bar
      :actions (list (ui.bulk/action-button :label (tr "Change status")
                                            :dialog-id "bulk-material-status")
                     (ui.bulk/action-button :label (tr "Move")
                                            :dialog-id "bulk-material-move")))
    (status-dialog reasons)
    (move-dialog reasons)))
