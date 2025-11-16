(ns sepal.app.ui.pages.list
  (:require [sepal.app.ui.icons.heroicons :as heroicons]
            [sepal.app.ui.page :as ui.page]))

(def list-container-id "list-container")

(defn search-field [q]
  [:div {:class "flex flex-row"}
   [:input {:name "q"
            :class "input input-md w-fill max-w-xs bg-white w-96"
            :type "search"
            :value q
            :placeholder "Search..."}]
   [:button
    {:type "button",
     :class ["inline-flex" "items-center" "mx-2" "px-2.5" "py-1.5" "border"
             "border-gray-300" "shadow-sm" "text-xs" "font-medium" "rounded"
             "text-gray-700" "bg-white" "hover:bg-gray-50" "focus:outline-none"
             "focus:ring-2" "focus:ring-offset-2" "focus:ring-indigo-500"]
     :onclick "document.getElementById('q').value = null; this.form.submit()"}
    (heroicons/outline-x :size 20)]])

;; TODO: rename this to a noun
(defn render [& {:keys [page-title-buttons content page-title table-actions]}]
  (ui.page/page :content (ui.page/page-inner
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
                               [:div {:class "overflow-hidden shadow ring-1 ring-black/5 md:rounded-lg"}
                                content]]]]])
                :page-title-buttons page-title-buttons
                :page-title page-title))
