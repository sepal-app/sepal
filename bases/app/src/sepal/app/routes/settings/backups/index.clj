(ns sepal.app.routes.settings.backups.index
  (:require [sepal.app.backup.core :as backup]
            [sepal.app.flash :as flash]
            [sepal.app.http-response :as http]
            [sepal.app.routes.settings.layout :as layout]
            [sepal.app.routes.settings.routes :as settings.routes]
            [sepal.app.ui.datetime :as datetime]
            [sepal.app.ui.form :as form]
            [sepal.app.ui.icons.lucide :as lucide]
            [sepal.error.interface :as error.i]
            [sepal.settings.interface.activity :as settings.activity]
            [sepal.validation.interface :as validation.i]
            [zodiac.core :as z]))

;; -----------------------------------------------------------------------------
;; Helpers

(defn- format-bytes
  "Format bytes as human-readable string (KB, MB, GB)."
  [bytes]
  (cond
    (nil? bytes) "0 B"
    (< bytes 1024) (str bytes " B")
    (< bytes (* 1024 1024)) (format "%.1f KB" (/ bytes 1024.0))
    (< bytes (* 1024 1024 1024)) (format "%.1f MB" (/ bytes (* 1024.0 1024)))
    :else (format "%.1f GB" (/ bytes (* 1024.0 1024 1024)))))

;; -----------------------------------------------------------------------------
;; Form

(def FormParams
  [:map {:closed true}
   form/AntiForgeryField
   [:frequency [:enum "disabled" "daily" "weekly" "monthly"]]])

;; -----------------------------------------------------------------------------
;; UI Components

(defn- alert-note []
  [:div {:class "alert alert-warning mb-6"}
   (lucide/triangle-alert :class "size-5 shrink-0")
   [:span "Backups include the database only. Media files are stored separately and must be backed up manually."]])

(defn- frequency-select [value]
  [:select {:name "frequency"
            :id "frequency"
            :class "select select-bordered w-full max-w-xs"}
   (for [[val label] [["disabled" "Disabled"]
                      ["daily" "Daily"]
                      ["weekly" "Weekly"]
                      ["monthly" "Monthly"]]]
     [:option {:value val
               :selected (when (= val (some-> value name)) "selected")}
      label])])

(defn- backup-form [& {:keys [config errors timezone]}]
  (let [next-backup (when (and (:frequency config)
                               (not= :disabled (:frequency config)))
                      (backup/get-next-backup-time (:frequency config)))]
    (form/form
      {:method "post"
       :action (z/url-for settings.routes/backups)}
      (form/anti-forgery-field)

      [:div {:class "space-y-6"}
       [:div
        [:h3 {:class "text-lg font-medium mb-4"} "Backup Schedule"]

        (form/field
          :name "frequency"
          :label "Frequency"
          :errors (:frequency errors)
          :input (frequency-select (:frequency config)))

        (when next-backup
          [:p {:class "text-sm text-base-content/70 -mt-2"}
           "Next backup: " (datetime/datetime next-backup timezone)])]

       (when (:last-run-at config)
         [:div {:class "text-sm text-base-content/70"}
          [:p "Last backup: " (datetime/datetime (:last-run-at config) timezone)]])]

      [:div {:class "mt-4"}
       (layout/save-button "Save changes")])))

(defn- backups-table [backups timezone]
  [:div {:class "mt-8"}
   [:h3 {:class "text-lg font-medium mb-4"} "Recent Backups"]
   (if (seq backups)
     [:div {:class "overflow-x-auto"}
      [:table {:class "table table-zebra"}
       [:thead
        [:tr
         [:th "Filename"]
         [:th "Size"]
         [:th "Created"]
         [:th "Actions"]]]
       [:tbody
        (for [{:keys [filename size-bytes created-at]} backups]
          [:tr
           [:td {:class "font-mono text-sm"} filename]
           [:td (format-bytes size-bytes)]
           [:td (datetime/datetime created-at timezone)]
           [:td
            [:a {:href (z/url-for settings.routes/backup-download {:filename filename})
                 :class "btn btn-sm btn-ghost"}
             (lucide/download :class "size-4")
             "Download"]]])]]]
     [:p {:class "text-base-content/70"} "No backups yet."])])

;; -----------------------------------------------------------------------------
;; Render

(defn render [& {:keys [viewer config errors flash backups timezone]}]
  (layout/layout
    :viewer viewer
    :current-route settings.routes/backups
    :category "Organization"
    :title "Backups"
    :flash flash
    :content
    [:div
     (alert-note)
     (backup-form :config config :errors errors :timezone timezone)
     (backups-table backups timezone)]))

;; -----------------------------------------------------------------------------
;; Handler

(defn handler [{:keys [::z/context flash form-params request-method viewer]}]
  (let [{:keys [db timezone]} context
        config (backup/get-config db)]
    (case request-method
      :post
      (let [result (validation.i/validate-form-values FormParams form-params)]
        (if (error.i/error? result)
          (http/validation-errors (validation.i/humanize result))
          (do
            (backup/set-config! db {:frequency (keyword (:frequency result))})
            (settings.activity/create! db
                                       settings.activity/updated
                                       (:user/id viewer)
                                       {:changes {"backup.frequency" (:frequency result)}})
            (-> (http/see-other settings.routes/backups)
                (flash/success "Backup settings updated successfully")))))

      ;; GET
      (let [backups (backup/list-backups (:path config) :limit 5)]
        (render :viewer viewer
                :config config
                :flash flash
                :backups backups
                :timezone timezone)))))
