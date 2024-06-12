(ns sepal.app.ui.page
  (:require [sepal.app.html :as html]
            [sepal.app.ui.base :as base]
            [sepal.app.ui.sidebar :as sidebar]))

(defn page-wrapper [& {:keys [router content]}]
  [:div
   [:div {:x-data "{showMobileSidebar: false}"
          :x-cloak true}
    (sidebar/static :router router)
    [:div
     [:div {:class "md:pl-64 flex flex-col flex-1"}
      [:div {:class "sticky top-0 z-10 md:hidden pl-1 pt-1 sm:pl-3 sm:pt-3 bg-gray-100"}
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
                 :d "M4 6h16M4 12h16M4 18h16"}]]]]
      [:main {:id "page-main"
              :class "flex-1"}
       [:div {:class "py-6"}
        [:div {:id "page-wrapper-content"
               :class "max-w-7xl mx-auto px-4 sm:px-6 md:px-8"}
         content]]]]]]
   [:script {:type "module"
             :src (html/static-url "js/alpine.js")}]
   [:script {:type "module"
             :src (html/static-url "js/htmx.js")}]])

(defn page [& {:keys [content page-title page-title-buttons router wrapper-attrs]}]
  (-> (page-wrapper :content [:div (merge {:class "px-4 sm:px-6 lg:px-8 md:py-8"}
                                          wrapper-attrs)
                              [:div {:class "sm:flex sm:items-center h-10"}
                               [:div {:class "sm:flex-auto"}
                                [:h1 {:class "text-xl font-semibold text-gray-900"}
                                 page-title]]

                               [:div {:class "mt-4 sm:mt-0 sm:ml-16 sm:flex flex-row gap-2"}
                                page-title-buttons]]
                              [:div {:class "mt-8"}
                               content]]
                    :router router)
      (base/html)))
