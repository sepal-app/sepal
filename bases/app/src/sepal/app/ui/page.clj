(ns sepal.app.ui.page
  (:require [sepal.app.globals :as g]
            [sepal.app.html :as html]
            [sepal.app.ui.base :as base]
            [sepal.app.ui.icons.heroicons :as heroicons]
            [sepal.app.ui.sidebar.mobile :as sidebar.mobile]
            [sepal.app.ui.sidebar.static :as sidebar.static]))

(defn mobile-top-bar [& {:keys [title]}]
  [:div {:class "sticky top-0 z-40 flex items-center gap-x-6 md:hidden pl-1 pt-1 sm:pl-3 sm:pt-3 bg-white shadow-sm"}
   (sidebar.mobile/sidebar-button)

   [:div {:class "flex-1 text-sm font-semibold leading-6 text-gray-900"}
    title]

   ;; Profile link
   [:a {:href "#"}
    [:span {:class "sr-only"} "Your profile"]
    (if (:avatar-s3-key g/*viewer*)
      [:img {:class "h-8 w-8 rounded-full"
             :src "{{ current_user.avatar_url(50)|default('', true)}}"
             :alt ""}]
      (heroicons/user-circle :size 38))]])

(defn page-wrapper [& {:keys [content footer router]}]
  [:div
   [:div {:x-data "{showMobileSidebar: false}"
          :x-cloak true}
    [:div {:hx-boost "true"}
     (sidebar.mobile/sidebar)
     (sidebar.static/sidebar :router router)]
    [:div
     [:div {:class "md:pl-64 flex flex-col flex-1"}
      (mobile-top-bar)

      [:main {:id "page-main"
              :class "flex-1"}
       [:div {:class "py-6"}
        [:div {:id "page-wrapper-content"
               :class "max-w-7xl mx-auto px-4 sm:px-6 md:px-8"}
         content]]]
      (when footer
        [:div {:id "page-footer"}
         footer])]]]

   [:script {:type "module"
             :src (html/static-url "js/page.ts")}]])

(defn page [& {:keys [content footer page-title page-title-buttons router attrs]}]
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
                     :footer footer
                     :router router)]
      (base/html)))
