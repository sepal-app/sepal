(ns sepal.app.routes.settings.observation-digest
  (:require [clojure.string :as str]
            [failjure.core :as f]
            [sepal.app.flash :as flash]
            [sepal.app.http-response :as http]
            [sepal.app.observation.digest :as digest]
            [sepal.app.routes.settings.layout :as layout]
            [sepal.app.routes.settings.routes :as settings.routes]
            [sepal.app.ui.form :as form]
            [sepal.error.interface :as error.i]
            [sepal.settings.interface.activity :as settings.activity]
            [sepal.validation.interface :as validation.i]
            [zodiac.core :as z]))

;; -----------------------------------------------------------------------------
;; Form

(def FormParams
  [:map {:closed true}
   form/AntiForgeryField
   ;; A cleared checkbox posts nothing at all, matching every other checkbox
   ;; in the app.
   [:enabled {:optional true} [:maybe :string]]
   [:send_at {:decode/form validation.i/empty->nil}
    [:maybe [:re {:error/message "Must be a valid time"} #"^([01]\d|2[0-3]):[0-5]\d$"]]]
   [:recipient {:decode/form validation.i/empty->nil}
    [:maybe [:re {:error/message "Must be a valid email address"} validation.i/email-re]]]])

(defn- check-config
  "The one rule malli cannot express: an enabled digest needs somewhere to
  send it. Returns humanized field errors, or nil."
  [{:keys [enabled recipient]}]
  (when (and (= "1" enabled) (str/blank? recipient))
    {:recipient ["Set a recipient before enabling the digest"]}))

;; -----------------------------------------------------------------------------
;; UI Components

(defn- enabled-checkbox [& {:keys [checked? errors]}]
  ;; A bare wrapping label, like every other checkbox in the app -- see
  ;; sepal.app.routes.settings.codes's strict-checkbox for why form/field's own
  ;; label is not used here.
  [:div {:class "spl-field"}
   [:label {:class "flex items-center gap-2 cursor-pointer"}
    [:input {:type "checkbox"
             :class "spl-checkbox"
             :id "enabled"
             :name "enabled"
             :value "1"
             :checked (boolean checked?)}]
    [:span {:class "spl-label"} "Send a daily digest email"]]
   (form/error-list "enabled" errors :hx-swap-oob? true)])

(defn- digest-form [& {:keys [config errors]}]
  (form/form
    {:method "post"
     :action (z/url-for settings.routes/observation-digest)}
    (form/anti-forgery-field)

    (form/section
      :title "Observation Digest"
      :hint "One email a day listing every observation whose next check is due."
      :children
      [(enabled-checkbox :checked? (:enabled config) :errors (:enabled errors))
       (form/input-field :label "Send at"
                         :name "send_at"
                         :type "time"
                         :required true
                         :value (:send-at config)
                         :errors (:send_at errors))
       (form/input-field :label "Recipient"
                         :name "recipient"
                         :type "email"
                         :value (:recipient config)
                         :errors (:recipient errors))])

    [:div {:class "mt-4"}
     (layout/save-button "Save changes")]))

;; -----------------------------------------------------------------------------
;; Render

(defn render [& {:keys [viewer config errors flash]}]
  (layout/layout
    :viewer viewer
    :current-route settings.routes/observation-digest
    :category "Organization"
    :title "Observation Digest"
    :flash flash
    :content (digest-form :config config :errors errors)))

;; -----------------------------------------------------------------------------
;; Handler

(defn handler [{:keys [::z/context flash form-params request-method viewer]}]
  (let [{:keys [db mail scheduler invitation-email-from]} context
        config (digest/get-config db)]
    (case request-method
      :post
      (f/attempt-all [data (validation.i/validate-form-values FormParams form-params)]
        (if-let [errors (check-config data)]
          (render :viewer viewer
                  :config {:enabled (= "1" (:enabled data))
                           :send-at (:send_at data)
                           :recipient (:recipient data)}
                  :errors errors
                  :flash flash)
          (f/attempt-all [_saved (f/try* (let [new-config {:enabled (= "1" (:enabled data))
                                                           :send-at (:send_at data)
                                                           :recipient (:recipient data)}]
                                           (digest/set-config! db new-config)
                                           (digest/schedule-digest! {:db db
                                                                     :mail mail
                                                                     :scheduler scheduler
                                                                     :from invitation-email-from})
                                           (settings.activity/create! db
                                                                      settings.activity/updated
                                                                      (:user/id viewer)
                                                                      {:changes {"observation.digest_enabled" (str (:enabled new-config))
                                                                                 "observation.digest_send_at" (:send-at new-config)
                                                                                 "observation.digest_recipient" (:recipient new-config)}})))]
            (-> (http/see-other settings.routes/observation-digest)
                (flash/success "Observation digest settings updated successfully"))
            (f/when-failed [e]
              (http/failure-flash e (http/see-other settings.routes/observation-digest)
                                  "Could not save the observation digest settings"))))
        (f/when-failed [e]
          (render :viewer viewer
                  :config config
                  :errors (error.i/humanize e)
                  :flash flash)))

      ;; GET
      (render :viewer viewer :config config :flash flash))))
