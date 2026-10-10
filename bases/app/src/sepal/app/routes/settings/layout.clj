(ns sepal.app.routes.settings.layout
  (:require [sepal.app.authorization :as authz]
            [sepal.app.routes.settings.routes :as settings.routes]
            [sepal.app.ui.form :as ui.form]
            [sepal.app.ui.page :as page]
            [sepal.i18n.interface :refer [tr]]
            [zodiac.core :as z]))

(defn sidebar-item [& {:keys [href label current?]}]
  [:a (cond-> {:href href
               :class (cond-> ["spl-tab spl-subnav-item"]
                        current? (conj "spl-tab--current"))}
        current? (assoc :aria-current "page"))
   label])

(defn sidebar-section [& {:keys [title children]}]
  [:div {:class "spl-subnav-group"}
   [:h2 {:class "spl-subnav-heading"} title]
   children])

(defn settings-sidebar [& {:keys [current-route viewer]}]
  ;; A third navigation surface, after the section rail and the breadcrumb, so
  ;; it needs its own accessible name. Below 1024px it collapses into the same
  ;; horizontal strip the accession edit tabs use — it cannot become a second
  ;; drawer, because the rail already is one at that width.
  [:nav {:class "spl-subnav" :aria-label (tr "Settings sections")}
   (sidebar-section
     :title (tr "Account")
     :children
     (list
       (sidebar-item :href (z/url-for settings.routes/profile)
                     :label (tr "Profile")
                     :current? (= current-route settings.routes/profile))
       (sidebar-item :href (z/url-for settings.routes/security)
                     :label (tr "Security")
                     :current? (= current-route settings.routes/security))))
   (when (authz/user-has-permission? viewer authz/organization-view)
     (sidebar-section
       :title (tr "Organization")
       :children
       (list
         (sidebar-item :href (z/url-for settings.routes/organization)
                       :label (tr "General")
                       :current? (= current-route settings.routes/organization))
         (sidebar-item :href (z/url-for settings.routes/codes)
                       :label (tr "Codes")
                       :current? (= current-route settings.routes/codes))
         (sidebar-item :href (z/url-for settings.routes/features)
                       :label (tr "Features")
                       :current? (= current-route settings.routes/features))
         (sidebar-item :href (z/url-for settings.routes/users)
                       :label (tr "Users")
                       :current? (= current-route settings.routes/users))
         (sidebar-item :href (z/url-for settings.routes/backups)
                       :label (tr "Backups")
                       :current? (= current-route settings.routes/backups)))))])

(defn layout [& {:keys [viewer current-route category title content flash content-class]}]
  (page/page
    :breadcrumbs [[:a {:href (z/url-for settings.routes/profile)} (tr "Settings")]
                  category
                  title]
    :flash flash
    :content
    [:div {:class "spl-settings-layout"}
     (settings-sidebar :current-route current-route :viewer viewer)
     ;; Same 576px column every other form uses, so a settings field is not a
     ;; different width from an accession field for no reason.
     [:div {:class (or content-class "spl-settings-content")}
      content]]))

(defn save-button [label]
  (ui.form/submit-button {:class "spl-btn spl-btn--primary"
                          :x-bind:disabled "!dirty || !valid"}
                         label))
