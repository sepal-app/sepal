(ns sepal.app.ui.layout)

(defn sepal-logo [])

(defn sidebar [{:keys [items]}]
  [:div {:class "flex flex-col flex-grow border-r border-gray-200 pt-5 pb-4 bg-white overflow-y-auto"}
   [:div {:class "flex items-center flex-shrink-0 px-4 space-y-5"}
    [:div {:class "h-8 w-auto"}
     (sepal-logo)]]
   [:div {:class "mt-5 flex-grow flex flex-col"}
    [:nav {:class "flex-1 bg-white space-y-1"
           :aria-label "Sidebar"}
     (for [item items] item)
     ;; Current:
     ;;   bg-indigo-50 border-indigo-600 text-indigo-600
     ;; Default:
     ;;   border-transparent text-gray-600 hover:bg-gray-50 hover:text-gray-900
     ;;
     ]]])

(defn page-header
  [{:keys [title button]}]
  [:div {:class "flex"}
   [:div {:class "flex flex-grow"}
    [:hi {:class "text-2xl"} title]]
   (when button
     [:div {:class "flex items-end"}
      button])])

(defn page-layout [{:keys [content header]}]
  [:div {:class "flex flex-row flex-grow h-full"}
   [:div {:class "flex flex-grow max-w-xs"}
    (sidebar {:items []
              ;; [(sidebar-item {:label "Organizations"
              ;;                        :icon (heroicon/outline-office-building)
              ;;                        :href "/orgs"})
              ;;         (sidebar-item {:label "Users"
              ;;                        :icon (heroicon/outline-user-group)
              ;;                        :href "/user"})]
              })]
   [:div {:class "flex flex-grow flex-basis py-6 px-8"}
    [:div {:class "flex flex-col w-full"}
     [:div {:class "mb-8"}
      header]
     content]]])
