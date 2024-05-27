(ns sepal.app.ui.pages.list
  (:require [sepal.app.ui.page :as page]))

(def list-container-id "list-container")

(defn render [& {:keys [page-title-buttons content page-title table-actions router]}]
  (page/page :content [:div
                       [:form {:method "get"
                               :hx-get " "
                               :hx-trigger "keyup delay:200ms,change"
                               :hx-select (str "#" list-container-id)
                               :hx-target (str "#" list-container-id)
                               :hx-push-url "true"
                               :hx-swap "outerHTML"}
                        [:div {:class "flex justify-between mt-8"}
                         [:div {:class "flex flex-row items-center"}
                          table-actions]]
                        [:div {:id list-container-id
                               :class "mt-4 flex flex-col"}
                         [:div {:class "-my-2 -mx-4 overflow-x-auto sm:-mx-6 lg:-mx-8"}
                          [:div {:class "inline-block min-w-full py-2 align-middle md:px-6 lg:px-8"}
                           [:div {:class "overflow-hidden shadow ring-1 ring-black ring-opacity-5 md:rounded-lg"}
                            content]]]]]]
             :page-title-buttons page-title-buttons
             :page-title page-title
             :router router))
