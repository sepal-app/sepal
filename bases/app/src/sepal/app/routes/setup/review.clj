(ns sepal.app.routes.setup.review
  (:require [ring.middleware.anti-forgery :refer [*anti-forgery-token*]]
            [sepal.app.flash :as flash]
            [sepal.app.html :as html]
            [sepal.app.http-response :as http]
            [sepal.app.routes.dashboard.routes :as dashboard.routes]
            [sepal.app.routes.setup.activity :as setup.activity]
            [sepal.app.routes.setup.layout :as layout]
            [sepal.app.routes.setup.routes :as setup.routes]
            [sepal.app.routes.setup.shared :as setup.shared]
            [sepal.database.interface :as db.i]
            [sepal.settings.interface :as settings.i]
            [sepal.user.interface :as user.i]
            [zodiac.core :as z]))

(defn summary-section [title items]
  [:div {:class "mb-6"}
   [:h3 {:class "font-medium text-lg mb-2"} title]
   [:div {:class "bg-base-200 rounded-lg p-4"}
    [:dl {:class "space-y-2"}
     (for [[label value] items
           :when value]
       [:div {:class "flex"}
        [:dt {:class "w-1/3 text-base-content/70"} label]
        [:dd {:class "w-2/3 font-medium"} value]])]]])

(defn render [& {:keys [admin org-settings timezone taxa-count wfo-version flash-messages]}]
  (layout/layout
    :current-step 6
    :flash-messages flash-messages
    :content
    [:div {:class "card bg-base-100 border border-base-300 shadow-sm w-full max-w-2xl"}
     [:div {:class "card-body"}
      [:h2 {:class "card-title text-2xl mb-4"} "Review & Complete"]

      [:p {:class "text-base-content/70 mb-6"}
       "Review your settings below. You can change these later in the Settings pages."]

      ;; Admin account
      (summary-section "Admin Account"
                       [["Name" (:user/full-name admin)]
                        ["Email" (:user/email admin)]])

      ;; Organization
      (when (seq (filter second org-settings))
        (summary-section "Organization"
                         [["Name" (get org-settings "organization.long_name")]
                          ["Short name" (get org-settings "organization.short_name")]
                          ["Abbreviation" (get org-settings "organization.abbreviation")]
                          ["Email" (get org-settings "organization.email")]
                          ["Phone" (get org-settings "organization.phone")]]))

      ;; Regional
      (summary-section "Regional Settings"
                       [["Timezone" timezone]])

      ;; Taxonomy
      (summary-section "Taxonomy"
                       [["Taxa count" (when (pos? taxa-count)
                                        (format "%,d taxa" taxa-count))]
                        ["WFO Plant List" (or wfo-version "Not imported")]])

      ;; Complete button
      [:div {:class "card-actions justify-between mt-6"}
       [:a {:href (z/url-for setup.routes/taxonomy)
            :class "btn btn-ghost"}
        "← Back"]
       [:form {:method "post"
               :action (z/url-for setup.routes/review)
               :hx-boost "false"}
        [:input {:type "hidden"
                 :name "__anti-forgery-token"
                 :value (force *anti-forgery-token*)}]
        [:button {:type "submit"
                  :class "btn btn-primary btn-lg"}
         "Complete Setup"]]]]]))

(defn handler [{:keys [::z/context flash request-method session]}]
  (let [{:keys [db]} context
        user-id (:user/id session)
        admin (when user-id (user.i/get-by-id db user-id))
        org-settings (settings.i/get-values db "organization")
        timezone (or (get org-settings "organization.timezone") "UTC")
        taxa-count (db.i/count db {:select [:id] :from [:taxon]})
        wfo-version (settings.i/get-value db "setup.wfo_plant_list_version")]

    (case request-method
      :post
      (do
        ;; Mark setup as complete
        (setup.shared/complete-setup! db)

        ;; Create activity entry for setup completion
        (setup.activity/create! db
                                setup.activity/completed
                                user-id
                                {:completed-by (:user/email admin)})

        ;; Redirect to dashboard
        (-> (http/see-other dashboard.routes/index)
            (flash/success "Setup complete! Welcome to Sepal.")))

      ;; GET
      (do
        (setup.shared/set-current-step! db 6)
        (html/render-page (render :admin admin
                                  :org-settings org-settings
                                  :timezone timezone
                                  :taxa-count taxa-count
                                  :wfo-version wfo-version
                                  :flash-messages (:messages flash)))))))
