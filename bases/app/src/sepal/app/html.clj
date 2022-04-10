(ns sepal.app.html
  (:require [rum.core :as rum :refer [defc]]
            [ring.middleware.anti-forgery :refer [*anti-forgery-token*]]))

(defn root-template [{:keys [content]}]
  [:html {:lang "en" :class "h-full bg-gray-100"}
   [:head
    [:meta {:charset "UTF-8"}]
    [:meta {:name "viewerport"
            :content "width=device-width, initial-scale=1.0"}]
    [:script {:src "https://cdn.tailwindcss.com?plugins=forms,typography,aspect-ratio,line-clamp"}]]
   [:body {:class "h-full"} content]])

(defn anti-forgery-field []
  [:input {:type "hidden"
           :name "__anti-forgery-token"
           :id "__anti-forgery-token"
           :value *anti-forgery-token*}])

(defn html-response [body]
  {:status 200
   :headers {"content-type" "text/html"}
   :body body})

(defn render-html [html]
  (-> html
      (rum/render-static-markup)
      (html-response)))
