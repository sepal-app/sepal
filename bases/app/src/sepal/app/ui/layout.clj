(ns sepal.app.ui.layout
  (:require [clojure.string :as str]
            [sepal.app.ui.icons :as icons]
            [sepal.app.http-response :refer [->path]]))

(defn sepal-logo []
  "Sepal")


(defn mobile-sidebar [& {:keys [items]}]
  [:div {:class "fixed inset-0 flex z-40 md:hidden"
         :role "dialog"
         :x-show "showMobileSidebar"
         :aria-modal "true"}
   ;; Off-canvas menu overlay, show/hide based on off-canvas menu state.
   [:div {:class "fixed inset-0 bg-gray-600 bg-opacity-75"
          :x-show "showMobileSidebar"
          :x-transition:enter "transition-opacity ease-in-out duration-300"
          :x-transition:enter-start "opacity-0"
          :x-transition:enter-end "opacity-100"
          :x-transition:leave "transition-opacity ease-in-out duration-300"
          :x-transition:leave-start "opacity-100"
          :x-transition:leave-end "opacity-0"
          :aria-hidden "true"}]
   ;; Off-canvas menu, show/hide based on off-canvas menu state.
   [:div {:class "relative flex-1 flex flex-col max-w-xs w-full bg-white"
          :x-show "showMobileSidebar"
          :x-transition:enter "transition-opacity ease-in-out duration-300 transform"
          :x-transition:enter-start "-translate-x-full"
          :x-transition:enter-end "-translate-x-0"
          :x-transition:leave "transition-opacity ease-in-out duration-300 transform"
          :x-transition:leave-start "translate-x-0"
          :x-transition:leave-end "-translate-x-full"}
    ;; Close button, show/hide based on off-canvas menu state.
    [:div {:class "absolute top-0 right-0 -mr-12 pt-2"
           :x-show "showMobileSidebar"
           :x-transition:enter "ease-in-out duration-300"
           :x-transition:enter-start "opacity-0"
           :x-transition:enter-end "opacity-100"
           :x-transition:leave "transition-opacity ease-in-out duration-300"
           :x-transition:leave-start "opacity-100"
           :x-transition:leave-end "opacity-0"
           }
     [:button {:type "button"
               :class "ml-1 flex items-center justify-center h-10 w-10 rounded-full focus:outline-none focus:ring-2 focus:ring-inset focus:ring-white"
               :x-on:click "showMobileSidebar = ! showMobileSidebar"}
      [:span {:class "sr-only"}
       "Close sidebar"]
      ;; Heroicon name: outline/x
      [:svg {:class "h-6 w-6 text-white", :xmlns "http://www.w3.org/2000/svg", :fill "none", :viewbox "0 0 24 24", :stroke "currentColor", :aria-hidden "true"}
       [:path {:stroke-linecap "round", :stroke-linejoin "round", :stroke-width "2", :d "M6 18L18 6M6 6l12 12"}]]]]

    [:div {:class "flex-1 h-0 pt-5 pb-4 overflow-y-auto"}
     [:div {:class "flex-shrink-0 flex items-center px-4"}
      ;; [:img {:class "h-8 w-auto"
      ;;        :src "https://tailwindui.com/img/logos/workflow-logo-indigo-600-mark-gray-800-text.svg"
      ;;        :alt "Workflow"}]
      (sepal-logo)]
     [:nav {:class "mt-5 px-2 space-y-1"}
      (seq items)]]

    [:div {:class "flex-shrink-0 flex border-t border-gray-200 p-4"}
     [:a {:href "/profile" :class "flex-shrink-0 group block"}
      [:div {:class "flex items-center"}
       [:div
        [:img {:class "inline-block h-10 w-10 rounded-full",
               :src "https://images.unsplash.com/photo-1472099645785-5658abf4ff4e?ixlib=rb-1.2.1&ixid=eyJhcHBfaWQiOjEyMDd9&auto=format&fit=facearea&facepad=2&w=256&h=256&q=80",
               :alt "" }]]
       [:div {:class "ml-3"}
        [:p {:class "text-base font-medium text-gray-700 group-hover:text-gray-900"} "Tom Cook"]
        [:p {:class "text-sm font-medium text-gray-500 group-hover:text-gray-700"} "View profile"]]]]]]
   ;; Force sidebar to shrink to fit close icon
   [:div {:class "flex-shrink-0 w-14"}]])

(defn user-icon []
  [:svg {:class "text-gray-400 group-hover:text-gray-500 mr-3 flex-shrink-0 h-6 w-6"
         :xmlns "http://www.w3.org/2000/svg"
         :fill "none"
         :viewbox "0 0 24 24"
         :stroke "currentColor"
         :aria-hidden "true"}
   [:path {:stroke-linecap "round"
           :stroke-linejoin "round"
           :stroke-width "2"
           :d "M12 4.354a4 4 0 110 5.292M15 21H3v-1a6 6 0 0112 0v1zm0 0h6v-1a6 6 0 00-9-5.197M13 7a4 4 0 11-8 0 4 4 0 018 0z"}]])

(defn sidebar-item [& {:keys [active? href icon label] :or {href "#"}}]
  (let [base-class "group flex items-center px-2 py-2 text-sm font-medium rounded-md"]
    [:a {:href href
         :class (if active?
                  (str/join [base-class " bg-gray-100 text-gray-900"])
                  (str/join [base-class " text-gray-600 hover:bg-gray-50 hover:text-gray-900"]))}
     icon
     label]))

(defn default-sidebar-items [& {:keys [router org]}]
  [(sidebar-item :href  "#" :label "Activity" :icon (user-icon))
   (sidebar-item :href (->path router :taxon {:id (:organization/id org)})
                 :label "Taxa" :icon (user-icon))
   (sidebar-item :href (->path router :accession {:id (:organization/id org)})
                 :label "Accessions" :icon (user-icon))
   (sidebar-item :href (->path router :location {:id (:organization/id org)})
                 :label "Locations" :icon (user-icon))
   (sidebar-item :href (->path router :media {:id (:organization/id org)})
                 :label "Media" :icon (user-icon))])


(defn static-sidebar [& {:keys [items router user]}]
  [:div {:class "hidden md:flex md:w-64 md:flex-col md:fixed md:inset-y-0"}
   ;; Sidebar component, swap this element with another sidebar if you like
   [:div {:class "flex-1 flex flex-col min-h-0 border-r border-gray-200 bg-white"}
    [:div {:class "flex-1 flex flex-col pt-5 pb-4 overflow-y-auto"}
     [:div {:class "flex items-center flex-shrink-0 px-4"}
      ;; [:img {:class "h-8 w-auto"
      ;;        :src "https://tailwindui.com/img/logos/workflow-logo-indigo-600-mark-gray-800-text.svg"
      ;;        :alt "Workflow"}]
      (sepal-logo)]
     [:nav {:class "mt-5 flex-1 px-2 bg-white space-y-1"}
      (seq items)]]
    [:div {:class "flex-shrink-0 flex border-t border-gray-200 p-4"}
     [:a {:href "#"
          :class "flex-shrink-0 w-full group block"}
      [:div {:class "flex items-center"}
       [:div
        [:img {:class "inline-block h-9 w-9 rounded-full"
               :src "https://images.unsplash.com/photo-1472099645785-5658abf4ff4e?ixlib=rb-1.2.1&ixid=eyJhcHBfaWQiOjEyMDd9&auto=format&fit=facearea&facepad=2&w=256&h=256&q=80",
               :alt ""}]]
       [:div {:class "ml-3"}
        [:p {:class "text-sm font-medium text-gray-700 group-hover:text-gray-900"} "Tom Cook"]
        [:p {:class "text-xs font-medium text-gray-500 group-hover:text-gray-700"} "View profile"]]]]]]])

(defn page-layout [& {:keys [content org router user]}]
  [:div {:x-data "{ showMobileSidebar: false }"}
   (mobile-sidebar :items (default-sidebar-items :router router :org org)
                   :user user
                   :router router)
   (static-sidebar :items (default-sidebar-items :router router :org org)
                   :user user
                   :router router)
   [:div {:class "md:pl-64 flex flex-col flex-1"}
    [:div {:class "sticky top-0 z-10 md:hidden pl-1 pt-1 sm:pl-3 sm:pt-3 bg-gray-100"}
     [:button {:type "button"
               :class "-ml-0.5 -mt-0.5 h-12 w-12 inline-flex items-center justify-center rounded-md text-gray-500 hover:text-gray-900 focus:outline-none focus:ring-2 focus:ring-inset focus:ring-indigo-500"
               :x-on:click "showMobileSidebar = ! showMobileSidebar"}
      [:span {:class "sr-only"} "Open sidebar"]
      (icons/outline-menu)]]
    [:main {:class "flex-1"}
     [:div {:class "py-6"}
      [:div {:class "max-w-7xl mx-auto px-4 sm:px-6 md:px-8"}
       [:h1 {:class "text-2xl font-semibold text-gray-900"} "Dashboard"]]
      [:div {:class "max-w-7xl mx-auto px-4 sm:px-6 md:px-8"}
       content]]]]])
