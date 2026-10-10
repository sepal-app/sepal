(ns sepal.app.routes.settings.features
  (:require [failjure.core :as f]
            [sepal.app.features :as features]
            [sepal.app.flash :as flash]
            [sepal.app.http-response :as http]
            [sepal.app.routes.settings.layout :as layout]
            [sepal.app.routes.settings.routes :as settings.routes]
            [sepal.app.ui.form :as form]
            [sepal.i18n.interface :refer [tr trc]]
            [sepal.settings.interface :as settings.i]
            [sepal.settings.interface.activity :as settings.activity]
            [sepal.validation.interface :as validation.i]
            [zodiac.core :as z]))

(defn- label-and-help [feature]
  (case feature
    :observations [(tr "Observations")
                   (tr "The Observations section, the Observations tabs on material and locations, and the overdue count on Activity.")]
    :propagation [(trc "navigation" "Propagation")
                  (tr "The Propagation section, and propagations on the accession, material and location panels.")]
    :media [(trc "navigation" "Media")
            (tr "The Media section, and the Media tabs on taxa, accessions, material and locations.")]
    :tags [(tr "Tags")
           (tr "The Tags section, the Tags tabs, and adding or removing tags on lists.")]))

(defn- feature-checkbox
  "A bare wrapping label, like the strict checkboxes on the Codes page."
  [feature on?]
  (let [[label help] (label-and-help feature)
        field (name feature)]
    [:div {:class "spl-field"}
     [:label {:class "flex items-center gap-2 cursor-pointer"}
      [:input {:type "checkbox"
               :class "spl-checkbox"
               :id field
               :name field
               :value "1"
               :checked (boolean on?)}]
      [:span {:class "spl-label"} label]]
     [:span {:class "spl-help"} help]]))

(defn- features-form [disabled]
  (form/form
    {:method "post"
     :action (z/url-for settings.routes/features)}
    (form/anti-forgery-field)
    (form/section
      :title (tr "Sections")
      :hint (tr "Turn off a section your garden does not use. Its records are kept, and a record linked from Activity opens read-only. A record that another record depends on cannot be deleted while its section is off.")
      :children (for [feature features/all]
                  (feature-checkbox feature (not (contains? disabled feature)))))
    [:div {:class "mt-4"}
     (layout/save-button (tr "Save changes"))]))

(defn- render [& {:keys [viewer disabled flash]}]
  (layout/layout
    :viewer viewer
    :current-route settings.routes/features
    :category (tr "Organization")
    :title (tr "Features")
    :flash flash
    :content (features-form disabled)))

(def FormParams
  [:map {:closed true}
   form/AntiForgeryField
   ;; An unticked box posts nothing, so absence means off.
   [:observations {:optional true} [:maybe :string]]
   [:propagation {:optional true} [:maybe :string]]
   [:media {:optional true} [:maybe :string]]
   [:tags {:optional true} [:maybe :string]]])

(defn- ->settings [data]
  (into {} (for [feature features/all]
             [(features/setting-key feature) (if (= "1" (get data feature)) "on" "off")])))

(defn get-handler [{:keys [::z/context flash viewer]}]
  (render :viewer viewer
          :disabled (features/disabled (:db context))
          :flash flash))

(defn post-handler [{:keys [::z/context form-params viewer]}]
  (let [{:keys [db]} context]
    (f/attempt-all [data (validation.i/validate-form-values FormParams form-params)
                    _saved (f/try* (let [new-settings (->settings data)]
                                     (settings.i/set-values! db new-settings)
                                     (settings.activity/create! db
                                                                settings.activity/updated
                                                                (:user/id viewer)
                                                                {:changes new-settings})))]
      (-> (http/see-other settings.routes/features)
          (flash/success (tr "Feature settings updated successfully")))
      (f/when-failed [e]
        (http/failure-flash e (http/see-other settings.routes/features)
                            (tr "Could not save the feature settings"))))))
