(ns sepal.app.ui.page
  (:require [sepal.app.flash :as flash]
            [sepal.app.globals :as g]
            [sepal.app.html :as html]
            [sepal.app.routes.accession.routes :as accession.routes]
            [sepal.app.routes.activity.routes :as activity.routes]
            [sepal.app.routes.location.routes :as location.routes]
            [sepal.app.routes.material.routes :as material.routes]
            [sepal.app.routes.media.routes :as media.routes]
            [sepal.app.routes.taxon.routes :as taxon.routes]
            [sepal.app.ui.base :as base]
            [sepal.app.ui.icons.bootstrap :as bootstrap]
            [sepal.app.ui.icons.heroicons :as heroicons]
            [zodiac.core :as z]))

(defn sidebar-item [& {:keys [href icon label] :as props}]
  [:li
   [:a
    {:class "is-drawer-close:tooltip is-drawer-close:tooltip-right",
     :href href
     :data-tip label}
    icon
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

(defn page-wrapper [& {:keys [content flash footer] :as props}]
  [:div {:class "drawer lg:drawer-open"}
   [:input {:id "sidebar-drawer-toggle"
            :type "checkbox"
            :class "drawer-toggle"
            ;; Setting checked makes the sidebar open by default. To have this follow page
            ;; navigation we would probably need to store it in locale storage.
            :checked "checked"}]
   [:div {:class "drawer-content"}
    ;; (comment "Navbar")
    [:nav {:class "navbar w-full bg-base-100"}
     [:div {:class "w-full flex flex-row justify-between items-center"}
      [:label {:for "sidebar-drawer-toggle",
               :aria-label "open sidebar",
               :class "btn btn-square btn-ghost"}
       ;; (comment "Sidebar toggle icon")
       (sidebar-toggle-icon)]

      #_[:a {:href "#"}
         [:span {:class "sr-only"} "Your profile"]
         (if (:avatar-s3-key g/*viewer*)
           [:img {:class "h-8 w-8 rounded-full"
                  :src "{{ current_user.avatar_url(50)|default('', true)}}"
                  :alt ""}]
           (heroicons/user-circle :size 38))]
      ;; [:div "hi"]
      ]
     #_[:div {:class "px-4"} "Navbar Title"]]
    ;; (comment "Page content here")
    [:div ;; {:class "p-4"}
     content
     (flash/banner (:messages flash))

     (when footer
       [:div {:id "page-footer"}
        footer])

     [:script {:type "module"
               :src (html/static-url "app/ui/page.ts")}]]]

   [:div {:class "drawer-side is-drawer-close:overflow-visible"}
    [:label {:for "sidebar-drawer-toggle",
             :aria-label "close sidebar",
             :class "drawer-overlay"}]
    [:div {:class
           "flex min-h-full flex-col items-start bg-base-200 is-drawer-close:w-14 is-drawer-open:w-64"}
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
                     :current? false)]

      [:div {:class ""}
       (sidebar-item :label "Profile"
                     :href  "#"
                     :icon (if (:avatar-s3-key g/*viewer*)
                             [:img {:class "h-8 w-8 rounded-full"
                                    :src "{{ current_user.avatar_url(50)|default('', true)}}"
                                    :alt ""}]
                             (heroicons/user-circle :size 38))
                     :current? false)]]]]])

(defn page [& {:keys [content flash footer page-title page-title-buttons attrs]}]
  (-> [:div (merge {} attrs)
       (page-wrapper :content [:div {:class "px-4 sm:px-6 lg:px-8 md:py-8"}
                               [:div {:class "sm:flex sm:items-center h-10"}
                                [:div {:class "sm:flex-auto"}
                                 [:h1 {:class "text-xl font-semibold text-gray-900"}
                                  page-title]]

                                [:div {:class "mt-4 sm:mt-0 sm:ml-16 sm:flex flex-row gap-2"}
                                 page-title-buttons]]
                               [:div {:class "mt-8"}
                                content]]
                     :flash flash
                     :footer footer)]
      (base/html)))
