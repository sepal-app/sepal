(ns sepal.app.ui.tabs)

(defn tabs [items]
  [:div {:role "tablist"
         :class "tabs tabs-border"}
   (for [{:keys [label href active]} items]
     [:a {:role "tab"
          :class (cond-> "tab text-accent hover:text-accent px-6"
                   active (str " tab-active"))
          :href href}
      [:span {:class "text-primary-content"}
       label]])])
