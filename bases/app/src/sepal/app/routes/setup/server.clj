(ns sepal.app.routes.setup.server
  (:require [sepal.app.html :as html]
            [sepal.app.routes.setup.layout :as layout]
            [sepal.app.routes.setup.routes :as setup.routes]
            [sepal.app.routes.setup.shared :as setup.shared]
            [zodiac.core :as z]))

(defn status-icon [status]
  (case status
    :ok [:span {:class "text-success text-xl"} "✓"]
    :warning [:span {:class "text-warning text-xl"} "⚠"]
    :error [:span {:class "text-error text-xl"} "✗"]))

(defn check-item [{:keys [name status message]}]
  [:div {:class "flex items-start gap-3 p-3 rounded-lg bg-base-200"}
   (status-icon status)
   [:div
    [:p {:class "font-medium"} name]
    [:p {:class (html/attr "text-sm"
                           (case status
                             :ok "text-base-content/70"
                             :warning "text-warning"
                             :error "text-error"))}
     message]]])

(defn checks-list [checks]
  [:div {:class "space-y-3"}
   (check-item {:name "Email (SMTP)"
                :status (get-in checks [:smtp :status])
                :message (get-in checks [:smtp :message])})
   (check-item {:name "Media Storage (S3)"
                :status (get-in checks [:s3 :status])
                :message (get-in checks [:s3 :message])})
   (check-item {:name "App Domain"
                :status (get-in checks [:app-domain :status])
                :message (get-in checks [:app-domain :message])})
   (check-item {:name "SpatiaLite (Geo-coordinates)"
                :status (get-in checks [:spatialite :status])
                :message (get-in checks [:spatialite :message])})])

(defn has-warnings? [checks]
  (some #(= :warning (:status %)) (vals checks)))

(defn render [& {:keys [checks flash-messages]}]
  (layout/layout
    :current-step 2
    :flash-messages flash-messages
    :content
    (layout/step-card
      :title "Server Configuration"
      :back-url (z/url-for setup.routes/admin)
      :content
      [:div {:class "space-y-4"}
       [:p {:class "text-base-content/70"}
        "Checking your server configuration. These features are optional but recommended for full functionality."]

       (checks-list checks)

       (when (has-warnings? checks)
         [:div {:class "alert alert-warning mt-4"}
          [:span "Some features are not configured. You can continue and configure them later, but the affected features won't work until then."]])

       [:div {:class "flex gap-2 mt-4"}
        [:a {:href (z/url-for setup.routes/server)
             :class "btn btn-outline btn-sm"}
         "Re-run Checks"]]]
      :next-button
      [:a {:href (z/url-for setup.routes/organization)
           :class "btn btn-primary"
           :hx-get (z/url-for setup.routes/organization)
           :hx-push-url "true"
           :hx-target "body"}
       "Continue →"])))

(defn handler [{:keys [::z/context flash]}]
  (let [{:keys [db]} context
        checks (setup.shared/check-server-config db)]
    (setup.shared/set-current-step! db 2)
    (html/render-page (render :checks checks
                              :flash-messages (:messages flash)))))
