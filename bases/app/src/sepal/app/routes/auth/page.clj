(ns sepal.app.routes.auth.page
  (:require [sepal.app.flash :as flash]
            [sepal.app.html :as html]
            [sepal.app.ui.base :as base]))

(defn page [& {:keys [content flash]}]
  (-> [:div {:x-data true
             :x-cloak true}
       [:div
        [:div {:class "absolute top-0 left-0 right-0 bottom-0"}
         [:img {:src (html/static-url "app/routes/auth/img/jose-fontano-WVAVwZ0nkSw-unsplash_1080x1620.jpg")
                :class "h-screen w-full object-cover object-center -z-10"
                :alt "login banner"}]]
        ;; The card is centred in the viewport rather than stretched down a
        ;; column: it used to fill the full height of a grid third, so on a
        ;; phone the form sat against the top edge with the photograph behind
        ;; the rest.
        [:div {:class "min-h-screen flex items-center justify-center p-6 relative z-10"}
         [:div {:class "spl-auth-card w-full max-w-md"}
          content]]

        ;; TODO: Need to raise the z-index of the flash banner
        (flash/banner (:messages flash))]
       [:script {:type "module"
                 :src (html/static-url "app/routes/auth/page.ts")}]]
      (base/html)))
