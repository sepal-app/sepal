(ns sepal.app.routes.bulk-tags
  "Adding a tag to, or removing one from, every selected record. Material,
  accessions and taxa call these with their resource type."
  (:require [failjure.core :as f]
            [sepal.app.bulk :as bulk]
            [sepal.app.html :as html]
            [sepal.app.http-response :as http]
            [sepal.app.tag-links :as tag-links]
            [sepal.app.ui.bulk :as ui.bulk]
            [sepal.app.ui.form :as ui.form]
            [sepal.database.interface :as db.i]
            [sepal.error.interface :as error.i]
            [sepal.i18n.interface :refer [tr trn]]
            [sepal.tag.interface :as tag.i]
            [sepal.validation.interface :as validation.i]
            [zodiac.core :as z]))

(def AddParams
  [:map {:closed true}
   [:ids bulk/Ids]
   [:tag-name [:string {:min 1}]]])

(def RemoveParams
  [:map {:closed true}
   [:ids bulk/Ids]
   [:tag-id [:int {:min 1}]]])

(def ^:private add-suffix "bulk-tag-add")
(def ^:private remove-suffix "bulk-tag-remove")

(defn- throw-on-error [v]
  ;; Inside the transaction, so the whole action rolls back.
  (when (error.i/error? v)
    (throw (ex-info "tag write failed" {:error v})))
  v)

(defn- link-all! [db user-id resource-type ids tag-name]
  (db.i/with-transaction [tx db]
    (let [tag (throw-on-error (tag-links/resolve-or-create! tx tag-name user-id))
          linked (count (filter #(true? (throw-on-error (tag-links/link! tx tag resource-type % user-id))) ids))]
      {:changed linked :skipped (- (count ids) linked)})))

(defn- unlink-all! [db user-id resource-type ids tag]
  (db.i/with-transaction [tx db]
    (let [unlinked (count (filter #(tag-links/unlink! tx tag resource-type % user-id) ids))]
      {:changed unlinked :skipped (- (count ids) unlinked)})))

(defn add-tag [{:keys [::z/context form-params viewer]} resource-type]
  (let [{:keys [db]} context]
    (f/attempt-all [_ids (bulk/check-ids form-params)
                    data (validation.i/validate-form-values AddParams form-params)
                    _all (bulk/require-all db resource-type (:ids data))
                    {:keys [changed skipped]} (f/try* (link-all! db (:user/id viewer) resource-type
                                                                 (:ids data) (:tag-name data)))]
      (bulk/applied (bulk/result-message
                      (trn "Tagged %1 record." "Tagged %1 records." changed changed)
                      (trn "%1 already had it." "%1 already had it." skipped skipped)
                      skipped))
      (f/when-failed [e]
        (http/not-saved e (tr "No tags were added.") :id-suffix add-suffix)))))

(defn remove-tag [{:keys [::z/context form-params viewer]} resource-type]
  (let [{:keys [db]} context]
    (f/attempt-all [_ids (bulk/check-ids form-params)
                    data (validation.i/validate-form-values RemoveParams form-params)
                    _all (bulk/require-all db resource-type (:ids data))
                    tag (or (tag.i/get-by-id db (:tag-id data))
                            (http/field-errors {:tag-id [(tr "Choose a tag.")]}))
                    {:keys [changed skipped]} (f/try* (unlink-all! db (:user/id viewer) resource-type
                                                                   (:ids data) tag))]
      (bulk/applied (bulk/result-message
                      (trn "Removed the tag from %1 record." "Removed the tag from %1 records." changed changed)
                      (trn "%1 didn't have it." "%1 didn't have it." skipped skipped)
                      skipped))
      (f/when-failed [e]
        (http/not-saved e (tr "No tags were removed.") :id-suffix remove-suffix)))))

(defn- tag-choices [tags]
  (let [control-id (str "tag-id-" remove-suffix)]
    (if (seq tags)
      (ui.form/field :label (tr "Tag")
                     :name control-id
                     :input [:select {:name "tag-id" :id control-id
                                      :class "spl-input spl-select w-full" :required true}
                             (for [{:tag/keys [id name]} tags]
                               [:option {:value id} name])])
      [:p (tr "None of the selected rows has a tag.")])))

(defn remove-form
  "The Remove tag dialog's body for the ids in the query: a choice of the
  tags any of them carry."
  [{:keys [::z/context query-params]} resource-type]
  (let [{:keys [db]} context]
    (f/attempt-all [_ids (bulk/check-ids query-params)
                    {:keys [ids]} (validation.i/validate-form-values [:map [:ids bulk/Ids]] query-params)]
      (html/render-partial (tag-choices (tag.i/get-for-resources db resource-type ids)))
      (f/when-failed [e]
        (http/not-saved e (tr "No tags were removed.") :id-suffix remove-suffix)))))

(defn add-dialog [& {:keys [id action]}]
  (ui.bulk/dialog
    :id id
    :title (tr "Add tag")
    :action action
    :confirm-one (tr "Tag %1 row")
    :confirm-other (tr "Tag %1 rows")
    :body (ui.form/input-field :id (str "tag-name-" add-suffix)
                               :name "tag-name"
                               :label (tr "Tag")
                               :required true)))

(defn remove-dialog [& {:keys [id action]}]
  (ui.bulk/dialog
    :id id
    :title (tr "Remove tag")
    :action action
    :confirm-one (tr "Remove from %1 row")
    :confirm-other (tr "Remove from %1 rows")
    :body [:div {:id (str id "-body")}]))

(defn remove-button
  "Loads the dialog's choices for the current selection, then opens it. A
  refused selection answers 422, which htmx here counts as successful, so the
  check is on the status."
  [& {:keys [form-url dialog-id]}]
  [:form {:hx-get form-url
          :hx-target (str "#" dialog-id "-body")
          :hx-swap "innerHTML"
          :x-on:htmx:after-request (str "if ($event.detail.xhr.status === 200) document.getElementById('" dialog-id "').showModal()")}
   (ui.bulk/ids-inputs)
   [:button {:type "submit" :class "spl-btn spl-btn--sm"} (tr "Remove tag")]])
