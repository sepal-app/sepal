(ns sepal.app.ui.sidebar.static
  (:require [clojure.string :as s]
            [sepal.app.globals :as g]
            [sepal.app.html :as html]
            [sepal.app.router :refer [url-for]]
            [sepal.app.ui.icons.bootstrap :as bootstrap]
            [sepal.app.ui.icons.heroicons :as heroicons]))

(defn sidebar-item [text & {:keys [href icon current]}]
  [:a {:href href
       :class  (cond->> ["hover:bg-gray-50" "hover:text-gray-900" "group" "flex" "items-center"
                         "px-2" "py-2" "text-sm" "font-medium" "rounded-md"]
                 current
                 (concat ["text-gray-900 bg-gray-100"])

                 (not current)
                 (concat ["text-gray-700 hover:bg-gray-50 hover:text-gray-900"])

                 (nil? href)
                 (concat ["pointer-events-none"])

                 :always
                 (s/join " "))}
   (when icon
     [:div {:class "mr-3"}
      icon])
   text])

(defn sidebar [& {:keys []}]
  [:div
   [:div {:class (html/attr "hidden" "md:flex" "md:w-64" "md:flex-col" "md:fixed" "md:inset-y-0")}
    [:div {:class (html/attr "flex-1" "flex" "flex-col" "min-h-0" "border-r" "border-gray-200" "bg-white")}
     [:div {:class (html/attr "flex-1" "flex" "flex-col" "pt-5" "pb-4" "overflow-y-auto")}
      [:div {:class (html/attr "flex" "items-center" "flex-shrink-0" "px-4")}
       [:h1 {:class "text-2xl"}
        "Sepal"]
       [:span {:class "ml-2"}
        "[beta]"]]
      [:div {:class "px-4 pt-2"}
       (when g/*organization*
         [:a {:href (url-for g/*router* :org/detail {:id (:id g/*organization*)})}
          (:organization/name g/*organization*)])]
      [:nav {:class "mt-5 flex-1 px-2 bg-white space-y-1"}
       (sidebar-item "Activity"
                     :href (url-for g/*router* :org/activity {:org-id (:organization/id g/*organization*)})
                     :icon (heroicons/outline-clock)
                     :current? false)
       (sidebar-item "Accessions"
                     :href (url-for g/*router* :org/accessions {:org-id (:organization/id g/*organization*)})
                     :icon (heroicons/outline-rectangle-group)
                     :current? false)
       (sidebar-item "Material"
                     :href (url-for g/*router* :org/materials {:org-id (:organization/id g/*organization*)})
                     :icon (heroicons/outline-tag)
                     :current? false)
       (sidebar-item "Taxa"
                     :href (url-for g/*router* :org/taxa {:org-id (:organization/id g/*organization*)})
                     :icon (bootstrap/flower1)
                     :current? false)
       (sidebar-item "Locations"
                     :href (url-for g/*router* :org/locations {:org-id (:organization/id g/*organization*)})
                     :icon (heroicons/outline-map-pin)
                     :current? false)
       (sidebar-item "Media"
                     :href (url-for g/*router* :org/media {:org-id (:organization/id g/*organization*)})
                     :icon (heroicons/outline-photo)
                     :current? false)]]
     [:div {:x-data "{expanded: false}"
            :role "region"
            :class "flex-shrink-0 flex border-t border-gray-200 bg-green-50/50"}
      [:button {:x-on:click "expanded = !expanded"
                :aria-expanded "expanded"
                :class "flex-shrink-0 w-full group block p-4"}
       [:div {:class "flex items-center"}
        [:div
         (if (:avatar-s3-key g/*viewer*)
           [:img {:class "inline-block h-9 w-9 rounded-full"
                  :src "{{ current_user.avatar_url(50)|default('', true)}}"
                  :alt ""}]
           (heroicons/user-circle :size 38))]
        [:div {:class "overflow-hidden"}
         (when (:user/name g/*viewer*)
           [:p
            {:class (html/attr "text-md" "text-ellipsis" "text-left" "font-medium" "text-gray-700"
                               "group-hover:text-gray-900" "block" "overflow-hidden")}
            (:user/name g/*viewer*)])
         [:p
          {:class (html/attr "text-sm" "text-ellipsis" "text-left" "font-medium" "text-gray-500"
                             "group-hover:text-gray-700" "block" "overflow-hidden")}
          (:user/email g/*viewer*)]]]
       [:div {:x-show "expanded"
              :x-collapse ""
              :class "mt-3"}
        ;; [:a {:href (url-for g/*router* :user/settings)
        ;;      :class (html/attr "text-gray-600" "hover:bg-gray-50" "hover:text-gray-900" "hover:bg-gray-50"
        ;;                        "hover:text-gray-900" "group" "flex" "items-center" "px-2" "py-2" "text-sm"
        ;;                        "font-medium" "rounded-md")}
        ;;  "Settings"]
        ;; (if g/*organization*
        ;;   [:a {:href (url-for g/*router* :organization/switch)
        ;;        :class (html/attr "text-gray-600" "hover:bg-gray-50" "hover:text-gray-900" "hover:bg-gray-50"
        ;;                          "hover:text-gray-900" "group" "flex" "items-center" "px-2" "py-2" "text-sm"
        ;;                          "font-medium" "rounded-md")}
        ;;    "Switch organizations"])
        [:a {:href (url-for g/*router* :auth/logout)
             :class (html/attr "text-gray-600" "hover:bg-gray-50" "hover:text-gray-900" "hover:bg-gray-50"
                               "hover:text-gray-900" "group" "flex" "items-center" "px-2" "py-2" "text-sm"
                               "font-medium" "rounded-md")}
         "Logout"]]]]]]])
