(ns sepal.app.ui.base
  (:require [sepal.app.html :as html]))

(defn html [content & {:keys [title]}]
  [:html {:data-theme "emerald"}
   [:head
    [:meta {:charset "utf-8"}]
    [:meta {:http-equiv "x-ua-compatible" :content "ie=edge"}]
    [:title (or title "Sepal")]
    [:meta {:name "description" :content ""}]
    [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
    [:style "[x-cloak] {display: none !important;}"]
    [:link {:rel "stylesheet"
            :href (html/static-url "app/css/main.css")}]]
   [:body {:hx-ext "alpine-morph"
           :hx-swap "morph"}
    content]])
