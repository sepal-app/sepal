(ns sepal.app.routes.tag.index
  (:require [sepal.app.http-response :as http]
            [sepal.app.routes.tag.routes :as tag.routes]
            [sepal.app.ui.empty :as ui.empty]
            [sepal.app.ui.page :as ui.page]
            [sepal.tag.interface :as tag.i]
            [zodiac.core :as z]))

(defn- row [tag]
  [:tr
   [:td [:a {:class "spl-link" :href (z/url-for tag.routes/detail {:id (:tag/id tag)})}
         (:tag/name tag)]]
   [:td (:tag/description tag)]
   [:td (:tag/link-count tag)]])

(defn- table [tags]
  (if (seq tags)
    [:table {:class "spl-table"}
     [:thead [:tr [:th "Name"] [:th "Description"] [:th "Links"]]]
     [:tbody (for [tag tags] (row tag))]]
    (ui.empty/empty-state
      :title "No tags yet"
      :body "Ad-hoc groupings a curator can filter a list by later.")))

(defn render [& {:keys [tags]}]
  (ui.page/page :content (table tags)
                :breadcrumbs ["Tags"]))

(defn handler [{:keys [::z/context]}]
  (let [{:keys [db]} context]
    ;; Below the migration that added the tag tables this section does not
    ;; exist: nothing to list, and nowhere to put a tag if you made one. The
    ;; rail omits the entry, so this is the answer for a bookmark or a typed
    ;; URL — a 404, not an empty page implying tags are merely unused.
    (if-not (tag.i/available? context)
      (http/not-found)
      (render :tags (tag.i/list-all context db)))))
