(ns sepal.app.ui.page
  (:require [sepal.app.flash :as flash]
            [sepal.app.html :as html]
            [sepal.app.routes.accession.routes :as accession.routes]
            [sepal.app.routes.activity.routes :as activity.routes]
            [sepal.app.routes.contact.routes :as contact.routes]
            [sepal.app.routes.location.routes :as location.routes]
            [sepal.app.routes.material.routes :as material.routes]
            [sepal.app.routes.media.routes :as media.routes]
            [sepal.app.routes.settings.routes :as settings.routes]
            [sepal.app.routes.taxon.routes :as taxon.routes]
            [sepal.app.ui.base :as base]
            [sepal.app.ui.icons.bootstrap :as bootstrap]
            [sepal.app.ui.icons.heroicons :as heroicons]
            [sepal.app.ui.icons.lucide :as lucide]
            [zodiac.core :as z]))

(defn sidebar-item [& {:keys [href icon label] :as props}]
  [:li
   [:a
    {:class "is-drawer-close:tooltip is-drawer-close:tooltip-right",
     :href href
     :data-tip label}
    [:div {:class "w-6 h-6"}
     icon]
    [:span {:class "is-drawer-close:hidden"}
     label]]])

(defn sidebar-toggle-icon []
  [:svg {:xmlns "http://www.w3.org/2000/svg",
         :viewBox "0 0 24 24",
         :stroke-linejoin "round",
         :stroke-linecap "round",
         :stroke-width "2",
         :fill "none",
         :stroke "currentColor",
         :class "my-1.5 inline-block size-4"}
   [:path {:d "M4 4m0 2a2 2 0 0 1 2 -2h12a2 2 0 0 1 2 2v12a2 2 0 0 1 -2 2h-12a2 2 0 0 1 -2 -2z"}]
   [:path {:d "M9 4v16"}]
   [:path {:d "M14 10l2 2l-2 2"}]])

;; TODO: We need a page-inner component so that we have consistent margins on
;; horizontal pages edges, e.g. the form footer lines up with the form fields

(defn page-inner [& children]
  [:div {:class "px-4 sm:px-6 lg:px-8 w-full"}
   children])

(defn navbar [& {:keys [breadcrumbs page-title-buttons]}]
  [:nav {:class "navbar w-full bg-base-100"}
   [:div {:class "w-full flex flex-row justify-between items-center"}
    [:div {:class "flex flex-row items-center"}
     [:label {:for "sidebar-drawer-toggle",
              :aria-label "open sidebar",
              :class "btn btn-square btn-ghost"}
      ;; (comment "Sidebar toggle icon")
      (sidebar-toggle-icon)]
     (when breadcrumbs
       [:div {:class "breadcrumbs t-[-3px] ml-4"}
        [:ul
         (for [item (butlast breadcrumbs)]
           [:li item])
         [:li [:span {:class "font-semibold text-xl"}
               (last breadcrumbs)]]]])]

    [:div {:class "mt-4 sm:mt-0 sm:ml-16 sm:flex flex-row gap-2"}
     page-title-buttons]]])

(defn sidebar []
  [:div {:class "drawer-side is-drawer-close:overflow-visible"}
   [:label {:for "sidebar-drawer-toggle",
            :aria-label "close sidebar",
            :class "drawer-overlay"}]
   [:div {:class
          "flex min-h-full flex-col items-start bg-base-200 is-drawer-close:w-16 is-drawer-open:w-64"}
    ;; (comment "Sidebar content here")
    [:ul {:class "menu w-full grow flex flex-col justify-between"}
     ;; (comment "List item")
     [:div
      (sidebar-item ;;"Activity"
        :label "Activity"
        :href (z/url-for activity.routes/index)
        :icon (heroicons/outline-clock)
        :current? false)
      (sidebar-item :label "Accessions"
                    :href (z/url-for accession.routes/index)
                    :icon (heroicons/outline-rectangle-group)
                    :current? false)
      (sidebar-item :label "Material"
                    :href (z/url-for material.routes/index)
                    :icon (heroicons/outline-tag)
                    :current? false)
      (sidebar-item :label "Taxa"
                    :href (z/url-for taxon.routes/index)
                    :icon (bootstrap/flower1)
                    :current? false)
      (sidebar-item :label "Locations"
                    :href (z/url-for location.routes/index)
                    :icon (heroicons/outline-map-pin)
                    :current? false)
      (sidebar-item :label "Media"
                    :href (z/url-for media.routes/index)
                    :icon (heroicons/outline-photo)
                    :current? false)
      (sidebar-item :label "Contacts"
                    :href (z/url-for contact.routes/index)
                    :icon (lucide/contact-round)
                    :current? false)]

     [:div {:class ""}
      (sidebar-item :label "Settings"
                    :href (z/url-for settings.routes/profile)
                    :icon (lucide/settings)
                    :current? false)]]]])

(defn page [& {:keys [breadcrumbs content flash footer page-title page-title-buttons attrs]}]
  (base/html
    [:div (merge {:x-data true} attrs)
     [:div {:class "drawer lg:drawer-open"}
      [:input {:id "sidebar-drawer-toggle"
               :type "checkbox"
               :class "drawer-toggle"
               ;; Setting checked makes the sidebar open by default. To have this follow page
               ;; navigation we would probably need to store it in locale storage.
               ;; :checked "checked"
               }]
      [:div {:class "drawer-content"}
       (navbar :breadcrumbs breadcrumbs
               :page-title-buttons page-title-buttons)
       [:main
        (page-inner
          [:div {:class "mt-8"}
           (when page-title
             [:h1 {:class "text-3xl font-semibold text-gray-900 mb-8"}
              page-title]
             #_[:div {:class "sm:flex sm:items-center h-10"}
                [:div {:class "sm:flex-auto"}
                 [:h1 {:class "text-xl font-semibold text-gray-900"}
                  page-title]]])])

        [:div {:class "mb-32"} ;; mb to leave room for the footer
         (page-inner content)]
        [:div {:id "flash-container"}
         (flash/banner (:messages flash))]

        (when footer
          [:div {:id "page-footer"}
           footer])

        [:script {:type "module"
                  :src (html/static-url "app/ui/page.ts")}]]]

      (sidebar)]]))
