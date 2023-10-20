(ns sepal.app.ui.sidebar
  (:require [sepal.app.globals :as g]
            [sepal.app.html :as html]
            [sepal.app.router :refer [url-for]]
            [sepal.app.ui.icons.bootstrap :as bootstrap]
            [sepal.app.ui.icons.heroicons :as heroicons]))

(defn static-sidebar-item [text & {:keys [href icon current]}]
  [:a
   {:href href
    :class  (cond-> [:hover:bg-gray-50 :hover:text-gray-900 :group :flex :items-center
                     :px-2 :py-2 :text-sm :font-medium :rounded-md]
              current
              (conj :text-gray-900 :bg-gray-100)

              (not current)
              (conj :text-gray-600 :hover:bg-gray-50 :hover:text-gray-900)

              (some? href)
              (conj :pointer-events-none)

              :always
              html/attr)}
   (when icon
     [:div
      {:class "mr-3"}
      icon])
   text])


(defn static [& {:keys []}]
  [:div {:class (html/attr :hidden :md:flex :md:w-64 :md:flex-col :md:fixed :md:inset-y-0)}
   [:div {:class (html/attr :flex-1 :flex :flex-col :min-h-0 :border-r :border-gray-200 :bg-white)}
    [:div {:class (html/attr :flex-1 :flex :flex-col :pt-5 :pb-4 :overflow-y-auto)}
     [:div {:class (html/attr :flex :items-center :flex-shrink-0 :px-4)}
      [:h1 {:class (html/attr :text-2xl)}
       "Sepal"]
      [:span {:class (html/attr :ml-2)}
       "[beta]"]]
     [:div {:class (html/attr :px-4 :pt-2)}
      (when g/*organization*
        [:a {:href (url-for g/*router* :org/detail {:id (:id g/*organization*)}) }
         (:organization/name g/*organization*)])]
     [:nav {:class (html/attr :mt-5 :flex-1 :px-2 :bg-white :space-y-1)}
      (static-sidebar-item "Activity"
                           :path (url-for g/*router* :activity/list)
                           :icon (heroicons/outline-clock)
                           :current? false)
      (static-sidebar-item "Accessions"
                           :path (url-for g/*router* :accession/list)
                           :icon (heroicons/outline-rectangle-group )
                           :current? false)
      (static-sidebar-item "Items"
                           :path (url-for g/*router* :item/list)
                           :icon (heroicons/outline-tag)
                           :current? false)
      (static-sidebar-item "Taxa"
                           :path (url-for g/*router* :taxon/list)
                           :icon (bootstrap/flower1)
                           :current? false)
      (static-sidebar-item "Locations"
                           :path (url-for g/*router* :location/list)
                           :icon (heroicons/outline-map-pin)
                           :current? false)
      (static-sidebar-item "Media"
                           :path (url-for g/*router* :media/list)
                           :icon (heroicons/outline-photo)
                           :current? false)]]
    [:div
     {:x-data "{expanded: false}",
      :role "region",
      :class (html/attr :flex-shrink-0 :flex :border-t :border-gray-200)}
     [:button
      {:x-on:click "expanded = !expanded",
       :aria-expanded "expanded",
       :class (html/attr :flex-shrink-0 :w-full :group :block :p-4)}
      [:div
       {:class "flex items-center"}
       [:div
        (if (:avatar-s3-key g/*viewer*)
          [:img {:class "inline-block h-9 w-9 rounded-full",
                 :src "{{ current_user.avatar_url(50)|default('', true)}}",
                 :alt ""}]
          (heroicons/user-circle :size 38))]
       [:div {:class "overflow-hidden"}
        (when (:user/name g/*viewer*)
          [:p
           {:class
            "text-md text-ellipsis text-left font-medium text-gray-700 group-hover:text-gray-900 block overflow-hidden"}
           (:user/name g/*viewer*)])
        [:p
         {:class
          "text-sm text-ellipsis text-left font-medium text-gray-500 group-hover:text-gray-700 block overflow-hidden"}
         (:user/email g/*viewer*)]]]
      [:div
       {:x-show "expanded", :x-collapse "", :class "mt-3"}
       [:a
        {:href (url-for g/*router* :user/settings)
         :class
         "text-gray-600 hover:bg-gray-50 hover:text-gray-900 hover:bg-gray-50 hover:text-gray-900 group flex items-center px-2 py-2 text-sm font-medium rounded-md"}
        "Settings"]
       (if g/*organization*
         [:a
          {:href (url-for g/*router* :organization/switch)
           :class
           "text-gray-600 hover:bg-gray-50 hover:text-gray-900 hover:bg-gray-50 hover:text-gray-900 group flex items-center px-2 py-2 text-sm font-medium rounded-md"}
          "Switch organizations"])
       [:a
        {:href (url-for g/*router* :auth/logout)
         :class "text-gray-600 hover:bg-gray-50 hover:text-gray-900 hover:bg-gray-50 hover:text-gray-900 group flex items-center px-2 py-2 text-sm font-medium rounded-md"}
        "Logout"]]]]]]
  )
