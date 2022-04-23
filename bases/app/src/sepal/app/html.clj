(ns sepal.app.html
  (:require [rum.core :as rum :refer [defc]]))

(defn root-template [& {:keys [content]}]
  [:html {:lang "en" :class "h-full bg-gray-100"}
   [:head
    [:meta {:charset "UTF-8"}]
    [:meta {:name "viewerport"
            :content "width=device-width, initial-scale=1.0"}]
    [:script {:src "https://cdn.tailwindcss.com?plugins=forms,typography,aspect-ratio,line-clamp"}]
    [:script {:defer true
              :src "https://unpkg.com/alpinejs@3.x.x/dist/cdn.min.js"}]]
   [:body {:class "h-full"} content]])

(defn html-response [body]
  {:status 200
   :headers {"content-type" "text/html"}
   :body body})

(defn render-html [html]
  (-> html
      (rum/render-static-markup)
      (html-response)))
