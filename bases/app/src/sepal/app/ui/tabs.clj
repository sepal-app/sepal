(ns sepal.app.ui.tabs)

(defn item [label & {:keys [active] :as props}]
  (let [anchor-props (dissoc props :active :label)]
    [:a (merge anchor-props
               {:role "tab"
                :class (cond-> "tab text-accent hover:text-accent px-6"
                         active (str " tab-active"))})
     [:span {:class "text-primary-content"}
      label]]))

(defn ^:deprecated tabs [items]
  [:div {:role "tablist"
         :class "tabs tabs-border"}
   (for [{:keys [label href active]} items]
     [:a {:role "tab"
          :class (cond-> "tab text-accent hover:text-accent px-6"
                   active (str " tab-active"))
          :href href}
      [:span {:class "text-primary-content"}
       label]])])

(defn tabs2 [items]
  [:div {:role "tablist"
         :class "tabs tabs-border"}
   items])
