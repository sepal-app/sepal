(ns sepal.app.routes.settings.layout
  (:require [sepal.app.authorization :as authz]
            [sepal.app.html :as html]
            [sepal.app.routes.settings.routes :as settings.routes]
            [sepal.app.ui.form :as ui.form]
            [sepal.app.ui.page :as page]
            [zodiac.core :as z]))

(defn sidebar-item [& {:keys [href label current?]}]
  [:a {:href href
       :class (html/attr "block" "px-3" "py-2" "rounded-md" "text-sm"
                         (if current?
                           "bg-base-300 font-medium"
                           "hover:bg-base-200"))}
   label])

(defn sidebar-section [& {:keys [title children]}]
  [:div {:class "mb-6"}
   [:h3 {:class "px-3 text-xs font-semibold text-gray-500 uppercase tracking-wider mb-2"}
    title]
   [:nav {:class "space-y-1"}
    children]])

(defn settings-sidebar [& {:keys [current-route viewer]}]
  [:aside {:class "w-64 shrink-0"}
   (sidebar-section
     :title "Account"
     :children
     (list
       (sidebar-item :href (z/url-for settings.routes/profile)
                     :label "Profile"
                     :current? (= current-route settings.routes/profile))
       (sidebar-item :href (z/url-for settings.routes/security)
                     :label "Security"
                     :current? (= current-route settings.routes/security))))
   (when (authz/user-has-permission? viewer authz/organization-view)
     (sidebar-section
       :title "Organization"
       :children
       (list
         (sidebar-item :href (z/url-for settings.routes/organization)
                       :label "General"
                       :current? (= current-route settings.routes/organization)))))])

(defn layout [& {:keys [viewer current-route category title content flash]}]
  (page/page
    :breadcrumbs [[:a {:href (z/url-for settings.routes/profile)} "Settings"]
                  category
                  title]
    :flash flash
    :content
    [:div {:class "flex gap-8 ml-4"}
     (settings-sidebar :current-route current-route :viewer viewer)
     [:div {:class "flex-1 max-w-2xl"}
      content]]))

(defn save-button [label]
  ;; Handle the btn-disabled and btn-primary classes explicitly so that when the page
  ;; first loads the button doesn't flash the primary color before becoming disabled
  (ui.form/submit-button {:class "btn btn-disabled btn-primary"
                          :x-bind:disabled "!dirty || !valid"
                          :x-bind:class "{'btn-disabled' : !dirty && !valid }"}
                         label))
