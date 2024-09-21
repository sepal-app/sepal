(ns sepal.app.routes.auth.login
  (:require [reitit.core :as r]
            [sepal.app.flash :as flash]
            [sepal.app.html :as html]
            [sepal.app.http-response :as http]
            [sepal.app.router :refer [url-for]]
            [sepal.app.routes.auth.routes :as auth.routes]
            [sepal.app.session :as session]
            [sepal.app.ui.base :as base]
            [sepal.app.ui.form :as form]
            [sepal.user.interface :as user.i]))

(defn send-verification-email [email]
  ;; TODO: bump
  )

(defn form [& {:keys [email invitation next router]}]
  [:form {:method "post"
          :action (url-for router auth.routes/login)}
   (form/anti-forgery-field)
   (when invitation
     (form/hidden-field :name "invitation" :value invitation))
   (form/input-field :label "Email" :name "email" :value email)
   (form/input-field :label "Password" :name "password" :type "password")
   (form/hidden-field :name "next" :value next)
   [:div {:class "flex flex-row mt-4 justify-between items-center"}
    [:button {:type "submit"
              :x-bind:disabled "submitting"
              :class (html/attr "inline-flex" "justify-center" "py-2" "px-4" "border"
                                "border-transparent" "shadow-sm" "text-sm" "font-medium"
                                "rounded-md" "text-white" "bg-green-700" "hover:bg-green-700"
                                "focus:outline-none" "focus:ring-2" "focus:ring-offset-2"
                                "focus:ring-green-500")}
     "Login"]
    [:p
     [:a {:href (url-for router auth.routes/forgot-password)}
      "Forgot password?"]]]

   [:div {:class "mt-4"}
    [:a {:href (url-for router auth.routes/register)} "Don't have an account?"]]])

(defn render [& {:keys [email #_field-errors invitation next router flash]}]
  (-> [:div
       [:div {:class "absolute top-0 left-0 right-0 bottom-0"}
        [:img {:src (html/static-url "img/auth/jose-fontano-WVAVwZ0nkSw-unsplash_1080x1620.jpg")
               :class "h-screen w-full object-cover object-center -z-10"
               :alt "login banner"}]]
       [:div {:class "grid grid-cols-3"}
        [:div {:class "col-start-1 col-span-3 lg:col-start-2 lg:col-span-1 flex flex-col justify-center z-10 lg:bg-white/60 h-screen shadow"}
         [:div {:class "bg-white/95 lg:bg-white/80 p-8 lg:block sm:max-lg:flex sm:max-lg:flex-col sm:max-lg:items-center"}
          [:div
           [:h1 {:class "text-3xl pb-6"} "Welcome to Sepal"]
           (form :email email
                 :invitation invitation
                 :next next
                 :router router)]]]]
       (flash/banner (:messages flash))]
      (base/html)
      (html/render-html)))

(defn handler [{:keys [context flash params request-method ::r/router]}]
  (let [{:keys [db]} context
        ;; TODO: we need to params encode this because we're getting the params
        ;; with string keys
        {:strs [email password invitation next]} params]
    ;; TODO: Put the email in the session so we can put the value in the input
    ;; after the redirect on error
    (case request-method
      :post
      (let [user (user.i/verify-password db email password)
            error (when-not user "Invalid password")
            session (when-not error (session/user->session user))]
        (if-not error
          (-> (http/see-other router :root)
              (assoc :session session))
          ;; TODO: pass params on redirect
          (-> (http/see-other router auth.routes/login)
              (flash/error error))))

      ;; else
      (render :next next
              :email email
              :invitation invitation
              :next next
              :router router
              :field-errors nil
              :flash flash))))
