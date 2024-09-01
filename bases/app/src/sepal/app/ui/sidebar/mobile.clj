(ns sepal.app.ui.sidebar.mobile
  (:require [sepal.app.globals :as g]
            [sepal.app.router :refer [url-for]]
            [sepal.app.routes.org.routes :as org.routes]
            [sepal.app.ui.icons.bootstrap :as bootstrap]
            [sepal.app.ui.icons.heroicons :as heroicons]
            [sepal.app.ui.sidebar.mobile :as sidebar.mobile]))

(defn sidebar-button
  "The hamburger menu to show the mobile menu"
  []
  [:button {:type "button"
            :class "-ml-0.5 -mt-0.5 h-12 w-12 inline-flex items-center justify-center rounded-md text-gray-500 hover:text-gray-900 focus:outline-none focus:ring-2 focus:ring-inset focus:ring-indigo-500"
            :x-on:click "showMobileSidebar=true"}
   [:span {:class "sr-only"} "Open sidebar"]
   [:svg {:class "h-6 w-6"
          :xmlns "http://www.w3.org/2000/svg"
          :fill "none"
          :viewBox "0 0 24 24"
          :stroke-width "2"
          :stroke "currentColor"
          :aria-hidden "true"}
    [:path {:stroke-linecap "round"
            :stroke-linejoin "round"
            :d "M4 6h16M4 12h16M4 18h16"}]]])

(defn sidebar-item [text & {:keys [href icon #_current]}]
  [:li
   [:a {:href href
        :class "group flex gap-x-3 rounded-md p-2 text-sm font-semibold leading-6 text-gray-700 hover:bg-gray-50 hover:text-indigo-600"}
    (when icon
      icon)
    text]])

(defn sidebar []
  [:div {:class "relative z-50 lg:hidden"
         :role "dialog"
         :aria-modal "true"
         :x-show "showMobileSidebar"}
   ;; Off-canvas menu backdrop, show/hide based on off-canvas menu state.
   [:div {:class "fixed inset-0 bg-slate-50"
          :aria-hidden "true"
          :x-show "showMobileSidebar"
          :x-transition:enter "transition-opacity ease-linear duration-300"
          :x-transition:enter-start "opacity-0"
          :x-transition:enter-end "opacity-100"
          :x-transition:leave "transition-opacity ease-linear duration-300"
          :x-transition:leave-start "opacity-100"
          :x-transition:leave-end "opacity-0"}]
   [:div {:class "fixed inset-0 flex"}
    ;; Off-canvas menu, show/hide based on off-canvas menu state.
    [:div {:class "relative mr-16 flex w-full max-w-xs flex-1"
           :x-show "showMobileSidebar"
           :x-transition:enter "transition ease-in-out duration-300 transform"
           :x-transition:enter-start "-translate-x-full"
           :x-transition:enter-end "translate-x-0"
           :x-transition:leave "transition ease-in-out duration-300 transform"
           :x-transition:leave-start "translate-x-0"
           :x-transition:leave-end "-translate-x-full"}
     ;; Close button, show/hide based on off-canvas menu state.
     [:div {:class "absolute left-full top-0 flex w-16 justify-center pt-5"
            :x-show "showMobileSidebar"
            :x-transition:enter "ease-in-out duration-300"
            :x-transition:enter-start "opacity-0"
            :x-transition:enter-end "opacity-100"
            :x-transition:leave "ease-in-out duration-300"
            :x-transition:leave-start "opacity-100"
            :x-transition:leave-end "opacity-0"}
      [:button {:type "button"
                :class "-m-2.5 p-2.5"
                :x-on:click "showMobileSidebar=false"}
       [:span {:class "sr-only"} "Close sidebar"]
       [:svg {:class "h-6 w-6 text-black"
              :fill "none"
              :viewBox "0 0 24 24"
              :stroke-width "1.5"
              :stroke "currentColor"
              :aria-hidden "true"}
        [:path {:stroke-linecap "round"
                :stroke-linejoin "round"
                :d "M6 18L18 6M6 6l12 12"}]]]]
     ;; Sidebar component
     [:div {:class "flex grow flex-col gap-y-5 overflow-y-auto bg-white px-6 pb-2"}
      [:div {:class "flex h-16 shrink-0 items-center"}
       [:h1 {:class "text-2xl p-2"}
        "Sepal"]
       [:span {:class "ml-2"}
        "[beta]"]]
      [:nav {:class "flex flex-1 flex-col"}
       [:ul {:role "list"
             :class "flex flex-1 flex-col gap-y-7"}
        (sidebar-item "Activity"
                      :href (url-for g/*router* org.routes/activity {:org-id (:organization/id g/*organization*)})
                      :icon (heroicons/outline-clock)
                      :current? false)
        (sidebar-item "Accessions"
                      :href (url-for g/*router* org.routes/accessions {:org-id (:organization/id g/*organization*)})
                      :icon (heroicons/outline-rectangle-group)
                      :current? false)
        (sidebar-item "Material"
                      :href (url-for g/*router* org.routes/materials {:org-id (:organization/id g/*organization*)})
                      :icon (heroicons/outline-tag)
                      :current? false)
        (sidebar-item "Taxa"
                      :href (url-for g/*router* org.routes/taxa {:org-id (:organization/id g/*organization*)})
                      :icon (bootstrap/flower1)
                      :current? false)
        (sidebar-item "Locations"
                      :href (url-for g/*router* org.routes/locations {:org-id (:organization/id g/*organization*)})
                      :icon (heroicons/outline-map-pin)
                      :current? false)
        (sidebar-item "Media"
                      :href (url-for g/*router* org.routes/media {:org-id (:organization/id g/*organization*)})
                      :icon (heroicons/outline-photo)
                      :current? false)]]]]]])
