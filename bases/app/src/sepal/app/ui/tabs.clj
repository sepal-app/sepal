(ns sepal.app.ui.tabs)

(defn tab-item [& {:keys [label href active? first? last?]
                   :or {href "#"}}]
  ;; TODO: if active then disable clicking
  [:a {:href href
       :role "tab"
       :class (cond->  "group relative min-w-0 flex-1 overflow-hidden bg-white px-4 py-4 text-center text-sm font-medium text-gray-500 hover:bg-gray-50 hover:text-gray-700 focus:z-10"
                first? (str " rounded-l-lg")
                last? (str " rounded-r-lg"))
       :aria-current (when active? "page")}
   [:span label]
   [:span {:aria-hidden (when-not active? "true"),
           :class (cond-> "absolute inset-x-0 bottom-0 h-0.5"
                    active? (str " bg-accent")
                    (not active?) (str " bg-transparent"))}]])

(defn tabs [& {:keys [active items]}]
  (let [[head & middle] (butlast items)
        tail (last items)]
    ;; TODO: It would be nice to hx-boost the tabs by default but it can cause
    ;; issues if the tab content has some specific js that needs to be
    ;; initialized
    [:div
     [:div {:class "sm:hidden"}
      [:label {:for "tabs", :class "sr-only"} "Select a tab"]
      [:select {:id "tabs",
                :name "tabs",
                ;; TODO: Use an onChange listener to change tabs
                :class "block w-full rounded-md border-gray-300 focus:border-indigo-500 focus:ring-indigo-500"}
       [:option {:selected ""} "My Account"]
       [:option "Company"]
       [:option "Team Members"]
       [:option "Billing"]]]
     [:div {:class "hidden sm:block"}
      [:nav {:class "isolate flex divide-x divide-gray-200 rounded-lg shadow",
             :role "tablist"
             :aria-label "Tabs"}
       (tab-item :label (:label head)
                 :href  (:href head)
                 :active? (= active (:key head))
                 :first? true)
       (for [item middle]
         (tab-item :label (:label item)
                   :href  (:href item)
                   :active? (= active (:key item))))
       (tab-item :label (:label tail)
                 :href  (:href tail)
                 :active? (= active (:key tail))
                 :last? true)]]]))
