(ns sepal.app.ui.base
  (:require [sepal.app.html :as html]))

(defn html [content & {:keys [title]}]
  [:html
   [:head
    [:meta {:charset "utf-8"}]
    [:meta {:http-equiv "x-ua-compatible" :content "ie=edge"}]
    [:title (or title "Sepal")]
    [:meta {:name "description" :content ""}]
    [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
    [:link {:rel "stylesheet"
            :href (html/static-url "css/main.min.css")}]
    [:script {:type "module"
              :src (html/static-url "js/shared.min.js")}]]
   [:body {:class "h-full"}
    content]])
