(ns sepal.app.ui.tabs)

(defn item [label & {:keys [active] :as props}]
  (let [anchor-props (dissoc props :active :label)]
    [:a (merge anchor-props
               {:role "tab"
                :class (cond-> "tab text-accent hover:text-accent px-6"
                         active (str " tab-active"))})
     [:span {:class "text-primary-content"}
      label]]))

(defn tabs [items]
  [:div {:role "tablist"
         :class "tabs tabs-box"}
   items])
