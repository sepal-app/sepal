(ns sepal.app.routes.taxon.detail.name
  (:require [sepal.app.flash :as flash]
            [sepal.app.http-response :as http]
            [sepal.app.params :as params]
            [sepal.app.routes.taxon.form :as taxon.form]
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

(defn page-title-buttons [& {:keys [org]}]
  (dropdown/dropdown "Actions"
                     (dropdown/item (z/url-for
                                      :org/taxa-new
                                      {:org-id (:organization/id org)})
                                    "Add a taxon")))

(defn tab-items [& {:keys [taxon]}]
  [{:label "Name"
    :key :name
    :href (z/url-for :taxon/detail-name {:id (:taxon/id taxon)})}
   {:label "Media"
    :key :media
    :href (z/url-for :taxon/detail-media {:id (:taxon/id taxon)})}])

(defn page-content [& {:keys [errors org taxon values]}]
  [:div {:class "flex flex-col gap-2"}
   (tabs/tabs :active :name
              :items (tab-items :taxon taxon))

   (let [read-only? (nil? (:taxon/organization-id taxon))]
     [:div
      (when read-only?
        (alert/info "Taxa from the WFO Plantlist are not editable."))
      (taxon.form/form :action (z/url-for :taxon/detail-name {:id (:taxon/id taxon)})
                       :errors errors
                       :org org
                       :read-only read-only?
                       :values values)])])

(defn footer-buttons []
  [[:button {:class "btn btn-primary"
             :x-on:click "$dispatch('taxon-form:submit')"}
    "Save"]
   [:button {:class "btn btn-secondary"
             ;; TODO: form.reset() would be better but it doesn't reset the TomSelect of the rank field
             ;; :x-on:click "dirty && confirm('Are you sure you want to lose your changes?') && $refs.taxonForm.reset()"
             :x-on:click "confirm('Are you sure you want to lose your changes?') && location.reload()"}
    "Cancel"]])

(defn render [& {:keys [errors org taxon values]}]
  (page/page :attrs {:x-data "taxonFormData"}
             :content (page-content :errors errors
                                    :org org
                                    :taxon taxon
                                    :values values)
             :footer (ui.form/footer :buttons (footer-buttons))
             :page-title (:taxon/name taxon)
             :page-title-buttons (page-title-buttons :org org)))

(defn save! [db taxon-id updated-by data]
  (try
    (db.i/with-transaction [tx db]
      (let [taxon (taxon.i/update! tx taxon-id data)]
        (taxon.activity/create! tx taxon.activity/updated updated-by taxon)
        taxon))
    (catch Exception ex
      (error.i/ex->error ex))))

(def FormParams
  [:map {:closed true}
   [:id :string]
   [:name :string]
   [:author :string]
   [:rank :string]
   [:parent-id [:maybe :string]]])

(defn handler [{:keys [::z/context _flash form-params request-method viewer]}]
  (let [{:keys [db organization resource]} context]
    (case request-method
      :post
      (let [data (params/decode FormParams form-params)
            result (save! db (:taxon/id resource) (:user/id viewer) data)]
        (if-not (error.i/error? result)
          (http/found :taxon/detail {:id (:taxon/id result)})
          (do
            ;; TODO: better error handling
            (tap> (str "ERROR: " (validation.i/humanize result)))
            (-> (http/found :taxon/detail {:id (:taxon/id resource)})
                (flash/set-field-errors (validation.i/humanize result))))))

      :get
      (let [parent (when (:taxon/parent-id resource)
                     (taxon.i/get-by-id db (:taxon/parent-id resource)))
            values {:id (:taxon/id resource)
                    :name (:taxon/name resource)
                    :rank (:taxon/rank resource)
                    :author (:taxon/author resource)
                    :organization-id (or (:taxon/organization-id resource)
                                         (:organization/id organization))
                    :parent-id (:taxon/id parent)
                    :parent-name (:taxon/name parent)}]
        ;; (tap> (str "FLASH:" flash))
        (render :org organization
                :taxon resource
                :values values)))))
