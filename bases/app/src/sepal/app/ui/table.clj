(ns sepal.app.ui.table
  (:require [lambdaisland.uri :as uri]
            [sepal.app.ui.icons.heroicons :as icon]))

(defn table
  "A table component.

  columns: A list of map with keys :name and :cell
  rows: A list of data. Each row is passed to (:cell column)
  "
  [& {:keys [columns rows]}]
  [:table {:class "min-w-full border-separate"
           :style {:border-spacing 0}}
   [:thead {:class "bg-base-200"}
    [:tr
     (for [col columns]
       [:th {:scope "col"
             :class "sticky top-0 z-10 border-b border-base-300 bg-base-200 py-3.5 pl-4 pr-3 text-left text-sm font-semibold text-gray-900 backdrop-blur backdrop-filter sm:pl-6 lg:pl-8"}
        (:name col)])]]
   [:tbody {:class "bg-base-100"}
    (for [row rows]
      [:tr
       (for [col columns]
         [:td {:class "whitespace-nowrap border-b border-base-200 py-4 pl-4 pr-3 text-sm font-medium text-gray-900 sm:pl-6 lg:pl-8"}
          ((:cell col) row)])])]])

(defn card-table
  ([table]
   (card-table table nil))
  ([table paginator]
   [:div {:class "overflow-x-auto rounded-box border bg-base-100 border-base-300"}
    table
    paginator]))

(defn- page-button [& {:keys [active? label href]}]
  [:a {:href href
       :class (cond->> "relative inline-flex items-center px-4 py-2 border text-sm font-medium"
                active?
                (str " z-10 bg-blue-50 border-blue-500 text-blue-600 r")
                :always
                (str " bg-base-100 border-gray-300 text-gray-500 hover:bg-gray-50"))}
   label])

(defn paginator [& {:keys [current-page page-size total href]
                    :or {total 0
                         href "#"}
                    :as _args}]
  (let [page-start (if (zero? total)
                     0
                     (-> current-page
                         (- 1)
                         (* page-size)
                         (+ 1)))
        page-href (fn [page] (-> href (uri/parse)
                                 (uri/assoc-query :page page)
                                 (uri/uri-str)))
        num-pages (int (Math/ceil (/ total page-size)))
        page-end (if (or (= current-page num-pages)
                         (zero? total))
                   total
                   (+ page-start page-size))
        previous-page-href (if (= current-page 1)
                             "#"
                             (page-href (- current-page 1)))
        next-page-href (if (= current-page num-pages)
                         "#"
                         (page-href (+ current-page 1)))
        buttons (cond
                  ;; If there are fewer than 5 pages, just build that many
                  (< num-pages 6)
                  [:div (for [page (range 1 (inc num-pages))]
                          (page-button :label page
                                       :active? (= current-page page)
                                       :href (page-href page)))]
                  ;; There are more than 5 pages of results, make sure we don't
                  ;; have negative page buttons for small current pages
                  (< current-page 4)
                  [:div (for [page (range 1 6)]
                          (page-button :label page
                                       :active? (= current-page page)
                                       :href (page-href page)))]
                  :else
                  [:div (for [page (range (- current-page 2) (+ current-page 3))]
                          (page-button :label page
                                       :active? (= current-page page)
                                       :href (page-href page)))])]

    [:div {:class "bg-base-100 px-4 py-3 flex items-center justify-between sm:px-6"}
     [:div {:class "flex-1 flex justify-between sm:hidden"}
      [:a {:href previous-page-href
           :class "relative inline-flex items-center px-4 py-2 border border-gray-300 text-sm font-medium rounded-md text-gray-700 bg-white hover:bg-gray-50"}
       "Previous"]
      [:a {:href next-page-href
           :class "ml-3 relative inline-flex items-center px-4 py-2 border border-gray-300 text-sm font-medium rounded-md text-gray-700 bg-base-100 hover:bg-gray-50"}
       "Next"]]
     [:div {:class "hidden sm:flex-1 sm:flex sm:items-center sm:justify-between"}
      [:div
       [:p {:class "text-sm text-gray-700"}
        "Showing "
        [:span {:class "font-medium"} page-start]
        " to "
        [:span {:class "font-medium"} page-end]
        " of "
        [:span {:class "font-medium"} total]
        " results"]]
      [:div
       [:nav {:class "relative z-0 inline-flex rounded-md -space-x-px"
              :aria-label "Pagination"}
        [:a {:href (page-href 1)
             :class "relative inline-flex items-center px-2 py-2 rounded-l-md border border-gray-300 bg-base-100 text-sm font-medium text-gray-500 hover:bg-gray-50"}
         [:span {:class "sr-only"}
          "First"]
         (icon/backwards-left)]
        [:a {:href previous-page-href
             :class "relative inline-flex items-center px-2 py-2 border border-gray-300 bg-base-100 text-sm font-medium text-gray-500 hover:bg-gray-50"}
         [:span {:class "sr-only"}
          "Previous"]
         (icon/chevron-left)]
        buttons
        [:a {:href next-page-href
             :class "relative inline-flex items-center px-2 py-2 border border-gray-300 bg-base-100 text-sm font-medium text-gray-500 hover:bg-gray-50"}
         [:span {:class "sr-only"} "Next"]
         (icon/chevron-right)]
        [:a {:href (page-href num-pages)
             :class "relative inline-flex items-center px-2 py-2 rounded-r-md border border-gray-300 bg-base-100 text-sm font-medium text-gray-500 hover:bg-gray-50"}
         [:span {:class "sr-only"}
          "Last"]
         (icon/backwards-right)]]]]]))
