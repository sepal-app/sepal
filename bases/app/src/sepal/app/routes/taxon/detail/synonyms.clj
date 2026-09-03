(ns sepal.app.routes.taxon.detail.synonyms
  (:require [ring.middleware.anti-forgery :refer [*anti-forgery-token*]]
            [sepal.app.http-response :as http]
            [sepal.app.json :as json]
            [sepal.app.routes.taxon.detail.shared :as taxon.shared]
            [sepal.app.routes.taxon.panel :as taxon.panel]
            [sepal.app.routes.taxon.routes :as taxon.routes]
            [sepal.app.ui.empty :as ui.empty]
            [sepal.app.ui.form :as ui.form]
            [sepal.app.ui.icons.heroicons :as heroicons]
            [sepal.app.ui.page :as ui.page]
            [sepal.app.ui.pages.detail :as pages.detail]
            [sepal.app.ui.taxon-name :as taxon-name]
            [sepal.database.interface :as db.i]
            [sepal.error.interface :as error.i]
            [sepal.synonym.interface :as synonym.i]
            [sepal.synonym.interface.activity :as synonym.activity]
            [sepal.validation.interface :as validation.i]
            [zodiac.core :as z]))

(def FormParams
  [:map {:closed true}
   [:synonym-name [:string {:min 1}]]])

(defn- delete-button [& {:keys [taxon synonym]}]
  ;; A WFO-sourced row isn't a row in this table at all (task 8 resolves it
  ;; from the read-only reference file), but the guard stays here anyway so
  ;; that task doesn't have to revisit this file.
  (when (not= "wfo" (:synonym/source synonym))
    [:button {:type "button"
              :class "spl-btn spl-btn--sm spl-btn--icon spl-btn--danger"
              :aria-label "Remove synonym"
              :hx-headers (json/js {"X-CSRF-Token" *anti-forgery-token*})
              :hx-delete (z/url-for taxon.routes/detail-synonym
                                    {:id (:taxon/id taxon)
                                     :synonym-id (:synonym/id synonym)})
              :hx-confirm "Remove this synonym?"}
     (heroicons/outline-trash :class "size-4")]))

(defn- synonym-row [& {:keys [taxon synonym]}]
  [:tr
   [:td (taxon-name/render (:synonym/synonym-name synonym))]
   [:td (:synonym/source synonym)]
   [:td (delete-button :taxon taxon :synonym synonym)]])

(defn- synonyms-table [& {:keys [taxon synonyms]}]
  (if (seq synonyms)
    [:table {:class "spl-table"}
     [:thead
      [:tr
       [:th "Name"]
       [:th "Source"]
       [:th ""]]]
     [:tbody
      (for [synonym synonyms]
        (synonym-row :taxon taxon :synonym synonym))]]
    (ui.empty/empty-state
      :title "No synonyms yet"
      :body "Other names this garden uses for this taxon show up here.")))

(defn- add-form [& {:keys [taxon errors]}]
  (ui.form/form
    {:hx-post (z/url-for taxon.routes/detail-synonyms {:id (:taxon/id taxon)})
     :hx-swap "none"
     :class "flex items-end gap-2"}
    (ui.form/anti-forgery-field)
    (ui.form/input-field :label "Synonym name"
                         :name "synonym-name"
                         :required true
                         :errors (:synonym-name errors))
    [:button {:type "submit" :class "spl-btn spl-btn--primary"} "Add"]))

(defn page-content [& {:keys [errors synonyms taxon]}]
  (taxon.shared/page
    :taxon taxon
    :active taxon.shared/synonyms-tab
    :body
    [:div {:class "grid gap-4"}
     (add-form :taxon taxon :errors errors)
     (synonyms-table :taxon taxon :synonyms synonyms)]))

(defn render [& {:keys [errors synonyms taxon panel-data]}]
  (ui.page/page :content (pages.detail/page-content-with-panel
                           :content (page-content :errors errors
                                                  :synonyms synonyms
                                                  :taxon taxon)
                           :panel-content (taxon.panel/panel-content
                                            :taxon (:taxon panel-data)
                                            :parent (:parent panel-data)
                                            :stats (:stats panel-data)
                                            :activities (:activities panel-data)
                                            :activity-count (:activity-count panel-data)))
                :breadcrumbs (taxon.shared/breadcrumbs taxon)))

(defn add! [db taxon-id created-by data]
  (try
    (db.i/with-transaction [tx db]
      (let [synonym (synonym.i/add-synonym! tx (assoc data
                                                      :taxon-id taxon-id
                                                      :created-by created-by))]
        (synonym.activity/create! tx synonym.activity/created created-by synonym)
        synonym))
    (catch Exception ex
      (error.i/ex->error ex))))

(defn remove! [db removed-by synonym]
  (db.i/with-transaction [tx db]
    (synonym.i/remove-synonym! tx (:synonym/id synonym))
    (synonym.activity/create! tx synonym.activity/deleted removed-by synonym)))

(defn handler [{:keys [::z/context form-params request-method viewer]}]
  (let [{:keys [db resource]} context]
    (case request-method
      :post
      (let [result (validation.i/validate-form-values FormParams form-params)]
        (if (error.i/error? result)
          (http/validation-errors (validation.i/humanize result))
          (let [saved (add! db (:taxon/id resource) (:user/id viewer) result)]
            (if-not (error.i/error? saved)
              (http/hx-redirect (z/url-for taxon.routes/detail-synonyms {:id (:taxon/id resource)}))
              (http/validation-errors (validation.i/humanize saved))))))

      :get
      (let [synonyms (synonym.i/list-for-taxon context db (:taxon/id resource))
            panel-data (taxon.panel/fetch-panel-data db resource)]
        (render :taxon resource
                :synonyms synonyms
                :panel-data panel-data)))))

(defn row-handler [{:keys [::z/context path-params viewer]}]
  (let [{:keys [db resource]} context
        synonym-id (parse-long (:synonym-id path-params))
        synonym (some #(when (= synonym-id (:synonym/id %)) %)
                      (synonym.i/list-for-taxon context db (:taxon/id resource)))]
    (when synonym
      (remove! db (:user/id viewer) synonym))
    (http/hx-redirect (z/url-for taxon.routes/detail-synonyms {:id (:taxon/id resource)}))))
