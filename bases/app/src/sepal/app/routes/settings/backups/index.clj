(ns sepal.app.routes.settings.backups.index
  (:require [clojure.tools.logging :as log]
            [failjure.core :as f]
            [sepal.app.backup.core :as backup]
            [sepal.app.backup.protocols :as backup.p]
            [sepal.app.datetime :as datetime]
            [sepal.app.flash :as flash]
            [sepal.app.http-response :as http]
            [sepal.app.routes.settings.layout :as layout]
            [sepal.app.routes.settings.routes :as settings.routes]
            [sepal.app.ui.form :as form]
            [sepal.app.ui.icons.lucide :as lucide]
            [sepal.app.ui.table :as ui.table]
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

(defn- download-link [store filename]
  [:a {:href (backup.p/download-url store filename)
       :class "spl-btn spl-btn--sm spl-btn--ghost"}
   (lucide/download :class "size-4")
   "Download"])

(defn- table-columns [store timezone]
  [{:name "Filename"
    :type :name
    :priority 1
    :stacked (fn [{:keys [filename size-bytes created-at]}]
               (list [:span {:class "spl-stacked-line"}
                      (format-bytes size-bytes)
                      " · "
                      (datetime/datetime created-at timezone)]
                     (download-link store filename)))
    :cell (fn [{:keys [filename]}] [:span {:class "font-mono text-sm"} filename])}
   {:name "Size"
    :type :number
    :priority 2
    :cell (fn [{:keys [size-bytes]}] (format-bytes size-bytes))}
   {:name "Created"
    :type :datetime
    :priority 2
    :cell (fn [{:keys [created-at]}] (datetime/datetime created-at timezone))}
   {:name "Actions"
    :type :actions
    :priority 1
    :cell (fn [{:keys [filename]}] (download-link store filename))}])

(defn- unreachable-note []
  ;; --danger rather than --info: an empty list here would be a claim that the
  ;; customer has no backups, and this is the page saying it does not know.
  [:div {:class "spl-alert spl-alert--danger"}
   (lucide/triangle-alert :class "size-5 shrink-0")
   [:span "Your backups could not be reached just now. They are not lost — "
    "try again in a few minutes."]])

(defn- backups-table [& {:keys [backups store timezone unreachable?]}]
  [:div {:class "mt-8"}
   [:h3 {:class "text-lg font-medium mb-4"} "Recent Backups"]
   (if unreachable?
     (unreachable-note)
     (ui.table/table :columns (table-columns store timezone)
                     :rows backups
                     :empty-state [:p {:class "text-text-muted"} "No backups yet."]))])

;; -----------------------------------------------------------------------------
;; Render

(defn render [& {:keys [viewer config errors flash backups timezone managed? store unreachable?]}]
  (layout/layout
    :viewer viewer
    :current-route settings.routes/backups
    :category "Organization"
    :title "Backups"
    :flash flash
    :content
    (if managed?
      ;; Nothing to configure, so nothing is offered. No note replaces the
      ;; warning: an empty space makes no claim that has to stay true.
      ;;
      ;; The media warning is hidden here too, deliberately: every shipped
      ;; store that owns the schedule is also one where media is not on this
      ;; disk, so the two questions have never come apart in practice, even
      ;; though manages-schedule? only answers the first one.
      [:div (backups-table :backups backups :store store :timezone timezone
                           :unreachable? unreachable?)]
      [:div
       (alert-note)
       (backup-form :config config :errors errors :timezone timezone)
       (backups-table :backups backups :store store :timezone timezone
                      :unreachable? unreachable?)])))

;; -----------------------------------------------------------------------------
;; Handler

(defn handler [{:keys [::z/context flash form-params request-method viewer]}]
  (let [{:keys [db timezone backup-store]} context
        config (backup/get-config db)
        managed? (backup.p/manages-schedule? backup-store)]
    (case request-method
      :post
      (if managed?
        ;; Not 403: when something else owns the schedule this write does not
        ;; exist at all, which is what the page already shows by offering no
        ;; form.
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
      (let [backups (try
                      {:rows (backup.p/list-backups backup-store)}
                      (catch Exception e
                        (log/error e "Could not list backups")
                        {:unreachable? true}))]
        (render :viewer viewer
                :config config
                :flash flash
                :backups (:rows backups)
                :unreachable? (:unreachable? backups)
                :store backup-store
                :timezone timezone
                :managed? managed?)))))
