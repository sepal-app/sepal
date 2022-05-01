(ns sepal.app.ui.table)

(defn table
  "A table component.

  columns: A list of map with keys :name and :cell
  rows: A list of data. Each row is passed to (:cell column)
  "
  [& {:keys [columns rows]}]
  [:table {:class "min-w-full border-separate"
           :style {:border-spacing 0}}
   [:thead {:class "bg-gray-50"}
    [:tr
     (for [col columns]
       [:th {:scope "col"
             :class "sticky top-0 z-10 border-b border-gray-300 bg-gray-50 bg-opacity-75 py-3.5 pl-4 pr-3 text-left text-sm font-semibold text-gray-900 backdrop-blur backdrop-filter sm:pl-6 lg:pl-8"}
        (:name col)])]]
   [:tbody {:class "bg-white"}
    (for [row rows]
      [:tr
       (for [col columns]
         [:td {:class "whitespace-nowrap border-b border-gray-200 py-4 pl-4 pr-3 text-sm font-medium text-gray-900 sm:pl-6 lg:pl-8"}
          ((:cell col) row)])])]])

;; (defn table2 [& {:keys [columns]}]
;;   [:div {:class "px-4 sm:px-6 lg:px-8"}
;;    [:div {:class "sm:flex sm:items-center"}
;;     [:div {:class "sm:flex-auto"}
;;      [:h1 {:class "text-xl font-semibold text-gray-900"} "Users"]
;;      [:p {:class "mt-2 text-sm text-gray-700"}
;;       "A list of all the users in your account including their name, title, email and role."]]
;;     [:div {:class "mt-4 sm:mt-0 sm:ml-16 sm:flex-none"}
;;      [:button {:type "button"
;;                :class "inline-flex items-center justify-center rounded-md border border-transparent bg-indigo-600 px-4 py-2 text-sm font-medium text-white shadow-sm hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2 sm:w-auto"}
;;       "Add user"]]]
;;    [:div {:class "mt-8 flex flex-col"}
;;     [:div {:class "-my-2 -mx-4 sm:-mx-6 lg:-mx-8"}
;;      [:div {:class "inline-block min-w-full py-2 align-middle"}
;;       [:div {:class "shadow-sm ring-1 ring-black ring-opacity-5"}
;;        [:table {:class "min-w-full border-separate"
;;                 :style "border-spacing: 0"}
;;         [:thead {:class "bg-gray-50"}
;;          [:tr
;;           [:th {:scope "col"
;;                 :class "sticky top-0 z-10 border-b border-gray-300 bg-gray-50 bg-opacity-75 py-3.5 pl-4 pr-3 text-left text-sm font-semibold text-gray-900 backdrop-blur backdrop-filter sm:pl-6 lg:pl-8"}
;;            "Name"]
;;           [:th {:scope "col"
;;                 :class "sticky top-0 z-10 hidden border-b border-gray-300 bg-gray-50 bg-opacity-75 px-3 py-3.5 text-left text-sm font-semibold text-gray-900 backdrop-blur backdrop-filter sm:table-cell"} "Title"]
;;           [:th {:scope "col"
;;                 :class "sticky top-0 z-10 hidden border-b border-gray-300 bg-gray-50 bg-opacity-75 px-3 py-3.5 text-left text-sm font-semibold text-gray-900 backdrop-blur backdrop-filter lg:table-cell"}
;;            "Email"]
;;           [:th {:scope "col"
;;                 :class "sticky top-0 z-10 border-b border-gray-300 bg-gray-50 bg-opacity-75 px-3 py-3.5 text-left text-sm font-semibold text-gray-900 backdrop-blur backdrop-filter"}
;;            "Role"]
;;           [:th {:scope "col"
;;                 :class "sticky top-0 z-10 border-b border-gray-300 bg-gray-50 bg-opacity-75 py-3.5 pr-4 pl-3 backdrop-blur backdrop-filter sm:pr-6 lg:pr-8"}
;;            [:span {:class "sr-only"}
;;             "Edit"]]]]
;;         [:tbody {:class "bg-white"}
;;          [:tr
;;           [:td {:class "whitespace-nowrap border-b border-gray-200 py-4 pl-4 pr-3 text-sm font-medium text-gray-900 sm:pl-6 lg:pl-8"}
;;            "Lindsay Walton"]
;;           [:td {:class "whitespace-nowrap border-b border-gray-200 px-3 py-4 text-sm text-gray-500 hidden sm:table-cell"}
;;            "Front-end Developer"]
;;           [:td {:class "whitespace-nowrap border-b border-gray-200 px-3 py-4 text-sm text-gray-500 hidden lg:table-cell"}
;;            "lindsay.walton@example.com"]
;;           [:td {:class "whitespace-nowrap border-b border-gray-200 px-3 py-4 text-sm text-gray-500"}
;;            "Member"]
;;           [:td {:class "relative whitespace-nowrap border-b border-gray-200 py-4 pr-4 pl-3 text-right text-sm font-medium sm:pr-6 lg:pr-8"}
;;            [:a {:href "#"
;;                 :class "text-indigo-600 hover:text-indigo-900"}
;;             "Edit"
;;             [:span {:class "sr-only"}
;;              ", Lindsay Walton"]]]]
;;          ;; "<!-- More people... -->"
;;          ]]]]]]])
