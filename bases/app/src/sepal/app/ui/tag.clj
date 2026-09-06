(ns sepal.app.ui.tag
  (:require [ring.middleware.anti-forgery :refer [*anti-forgery-token*]]
            [sepal.app.json :as json]
            [sepal.app.ui.empty :as ui.empty]
            [sepal.app.ui.form :as ui.form]
            [sepal.app.ui.icons.heroicons :as heroicons]))

(defn- chip [& {:keys [tag remove-url]}]
  [:span {:class "spl-chip"}
   (:tag/name tag)
   [:button {:type "button"
             :class "spl-btn spl-btn--sm spl-btn--icon spl-btn--ghost"
             :aria-label (str "Remove tag " (:tag/name tag))
             :hx-headers (json/js {"X-CSRF-Token" *anti-forgery-token*})
             :hx-delete remove-url
             :hx-confirm (str "Remove tag \"" (:tag/name tag) "\"?")}
    (heroicons/outline-x :class "size-3")]])

(defn chips [& {:keys [tags remove-url-fn]}]
  (if (seq tags)
    [:div {:class "flex flex-wrap gap-2"}
     (for [tag tags] ^{:key (:tag/id tag)} (chip :tag tag :remove-url (remove-url-fn tag)))]
    (ui.empty/empty-state
      :title "No tags yet"
      :body "Ad-hoc groupings you can filter this list by later.")))

(defn add-form [& {:keys [action all-tags]}]
  [:div
   [:datalist {:id "tag-options"}
    (for [t all-tags] ^{:key (:tag/id t)} [:option {:value (:tag/name t)}])]
   (ui.form/form
     {:hx-post action
      :hx-swap "none"
      :class "flex items-end gap-2"}
     (ui.form/anti-forgery-field)
     (ui.form/input-field :label "Tag"
                          :name "tag-name"
                          :required true
                          :input-attrs {:list "tag-options"}
                          :help "Start typing an existing tag, or type a new name.")
     [:button {:type "submit" :class "spl-btn spl-btn--primary"} "Add"])])

(defn section [& {:keys [tags all-tags action remove-url-fn]}]
  [:div {:class "grid gap-4"}
   (add-form :action action :all-tags all-tags)
   (chips :tags tags :remove-url-fn remove-url-fn)])
