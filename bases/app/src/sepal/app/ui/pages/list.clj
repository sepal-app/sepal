(ns sepal.app.ui.pages.list
  (:require [sepal.app.ui.page :as page])
  )

(defn render [& {:keys [page-title-buttons content page-title table-actions router]}]
  (page/page :content [:div
                       [:form {:method "get"}
                        [:div
                         {:class "flex justify-between mt-8"}
                         [:div
                          {:class "flex flex-row"}
                          table-actions]]
                        [:div
                         {:class "mt-4 flex flex-col"}
                         [:div
                          {:class "-my-2 -mx-4 overflow-x-auto sm:-mx-6 lg:-mx-8"}
                          [:div
                           {:class "inline-block min-w-full py-2 align-middle md:px-6 lg:px-8"}
                           [:div
                            {:class "overflow-hidden shadow ring-1 ring-black ring-opacity-5 md:rounded-lg"}
                            content]]]]]]
             :page-title-buttons page-title-buttons
             :page-title page-title
             :router router))
