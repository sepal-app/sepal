(ns sepal.app.routes.auth.page
  (:require [sepal.app.flash :as flash]
            [sepal.app.html :as html]
            [sepal.app.ui.base :as base]))

(defn page [& {:keys [content flash]}]
  (-> [:div {:x-data true
             :x-cloak true}
       [:div
        [:div {:class "absolute top-0 left-0 right-0 bottom-0"}
         [:img {:src (html/static-url "img/auth/jose-fontano-WVAVwZ0nkSw-unsplash_1080x1620.jpg")
                :class "h-screen w-full object-cover object-center -z-10"
                :alt "login banner"}]]
        [:div {:class "grid grid-cols-3"}
         [:div {:class "col-start-1 col-span-3 lg:col-start-2 lg:col-span-1 flex flex-col justify-center z-10 lg:bg-white/60 h-screen shadow"}
          [:div {:class "bg-white/95 lg:bg-white/80 p-8 lg:block sm:max-lg:flex sm:max-lg:flex-col sm:max-lg:items-center"}
           content]]]

        ;; TODO: Need to raise the z-index of the flash banner
        (flash/banner (:messages flash))]
       [:script {:type "module"
                 :src (html/static-url "js/auth/page.ts")}]]
      (base/html)))
