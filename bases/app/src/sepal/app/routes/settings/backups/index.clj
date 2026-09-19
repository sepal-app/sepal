(ns sepal.app.routes.settings.backups.index
  (:require [failjure.core :as f]
            [sepal.app.backup.core :as backup]
            [sepal.app.datetime :as datetime]
            [sepal.app.flash :as flash]
            [sepal.app.http-response :as http]
            [sepal.app.routes.settings.layout :as layout]
            [sepal.app.routes.settings.routes :as settings.routes]
            [sepal.app.ui.form :as form]
            [sepal.app.ui.icons.lucide :as lucide]
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
   [:frequency {:decode/form validation.i/empty->nil}
    [:maybe [:enum "daily" "weekly" "monthly"]]]])

;; -----------------------------------------------------------------------------
;; UI Components

(defn- alert-note []
  ;; Informational, not a warning. --warning and --danger resolve to the same
  ;; red tokens on purpose — one attention colour, not two — so a note that is
  ;; merely telling you where your media lives reads as something broken.
  [:div {:class "spl-alert spl-alert--info mb-6"}
   (lucide/info :class "size-5 shrink-0")
   [:span "Backups include the database only. Media files are stored separately and must be backed up manually."]])

(defn- frequency-select [value]
  [:select {:name "frequency"
            :id "frequency"
            :class "spl-input spl-select w-full max-w-xs"}
   (for [[val label] [["" "Disabled"]
                      ["daily" "Daily"]
                      ["weekly" "Weekly"]
                      ["monthly" "Monthly"]]]
     [:option {:value val
               :selected (when (= val (some-> value name)) "selected")}
      label])])

(defn- backup-form [& {:keys [config errors timezone]}]
  (let [next-backup (when (:frequency config)
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
          [:p {:class "text-sm text-text-muted -mt-2"}
           "Next backup: " (datetime/datetime next-backup timezone)])]

       (when (:last-run-at config)
         [:div {:class "text-sm text-text-muted"}
          [:p "Last backup: " (datetime/datetime (:last-run-at config) timezone)]])]

      [:div {:class "mt-4"}
       (layout/save-button "Save changes")])))

(defn- backups-table [backups timezone]
  [:div {:class "mt-8"}
   [:h3 {:class "text-lg font-medium mb-4"} "Recent Backups"]
   (if (seq backups)
     [:div {:class "overflow-x-auto"}
      [:table {:class "spl-table"}
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
                 :class "spl-btn spl-btn--sm spl-btn--ghost"}
             (lucide/download :class "size-4")
             "Download"]]])]]]
     [:p {:class "text-text-muted"} "No backups yet."])])

;; -----------------------------------------------------------------------------
;; Render

(defn render [& {:keys [viewer config errors flash backups timezone managed-backups?]}]
  (layout/layout
    :viewer viewer
    :current-route settings.routes/backups
    :category "Organization"
    :title "Backups"
    :flash flash
    :content
    (if managed-backups?
      ;; Nothing to configure, so nothing is offered. No note replaces the
      ;; warning: an empty space makes no claim that has to stay true as the
      ;; surrounding storage work lands.
      [:div (backups-table backups timezone)]
      [:div
       (alert-note)
       (backup-form :config config :errors errors :timezone timezone)
       (backups-table backups timezone)])))

;; -----------------------------------------------------------------------------
;; Handler

(defn handler [{:keys [::z/context flash form-params request-method viewer]}]
  (let [{:keys [db timezone backup-dir managed-backups?]} context
        config (backup/get-config db backup-dir)]
    (case request-method
      :post
      (if managed-backups?
        ;; Not 403: on a managed garden this write does not exist at all, which
        ;; is what the page already shows by offering no form.
        (http/not-found)
        (f/attempt-all [data (validation.i/validate-form-values FormParams form-params)
                        _saved (f/try* (let [frequency (some-> (:frequency data) keyword)]
                                         (backup/set-config! db {:frequency frequency})
                                         (settings.activity/create! db
                                                                    settings.activity/updated
                                                                    (:user/id viewer)
                                                                    {:changes {"backup.frequency" (or (:frequency data) "disabled")}})))]
          (-> (http/see-other settings.routes/backups)
              (flash/success "Backup settings updated successfully"))
          (f/when-failed [e]
            (http/failure-flash e (http/see-other settings.routes/backups) "Could not save the backup settings"))))

      ;; GET
      (let [backups (backup/list-backups (:path config))]
        (render :viewer viewer
                :config config
                :flash flash
                :backups backups
                :timezone timezone
                :managed-backups? managed-backups?)))))
