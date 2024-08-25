(ns sepal.app.routes.register.index
  (:require [reitit.core :as r]
            [sepal.app.flash :as flash]
            [sepal.app.html :as html]
            [sepal.app.http-response :as http]
            [sepal.app.router :refer [url-for]]
            [sepal.app.session :as session]
            [sepal.app.ui.base :as base]
            [sepal.app.ui.form :as form]
            [sepal.user.interface :as user.i]
            [sepal.user.interface.spec :as user.spec]
            [sepal.validation.interface :as validation.i]))

(defn form [& {:keys [request email invitation next router]}]
  [:form {:method "post"
          :action (url-for router :register/index)}
   (form/anti-forgery-field)
   (form/hidden-field :name "next" :value next)
   (when invitation
     (form/hidden-field :name "invitation" :value invitation))

   (form/input-field :label "Email"
                     :name "email"
                     :value email
                     :required true
                     :type "email"
                     :errors (flash/field-error request :email))
   (form/input-field :label "Password"
                     :name "password"
                     :type "password"
                     :required true
                     ;; :value (:password params)
                     :errors (flash/field-error request :password))
   ;; TODO: Validate that the password match client side
   (form/input-field :label "Confirm password"
                     :name "confirm-password"
                     :type "password"
                     :required true
                     ;; :value (:confirm-password params)
                     :errors (flash/field-error request :confirm-password))

   [:div {:class (html/attr "flex" "flex-row" "mt-4" "justify-between" "items-center")}
    [:button {:type "submit"
              ;; :x-bind:disabled "submitting"
              :class (html/attr "inline-flex" "justify-center" "py-2" "px-4" "border"
                                "border-transparent" "shadow-sm" "text-sm" "font-medium"
                                "rounded-md" "text-white" "bg-green-700" "hover:bg-green-700"
                                "focus:outline-none" "focus:ring-2" "focus:ring-offset-2"
                                "focus:ring-green-500")}
     "Create account"]
    ;; TODO:
    ;; [:p
    ;;  [:a {:href "/forgot_password"}
    ;;   "Forgot password?"]]
    ]

   [:div {:class "mt-4"}
    ;; TODO
    [:a {:href "/login"} "Already have an account?"]]])

(defn render [& {:keys [email #_field-errors invitation next request router flash]}]
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
                 :request request
                 :router router)]]

         ;; TODO: Non field errors

          ;; (when error
          ;;   [:div {:class "rounded-md bg-red-50 p-4 text-red-800"
          ;;          :x-show "!submitting"}
          ;;    error])
         ]]
       (flash/banner (:messages flash))]
      (base/html)
      (html/render-html)))

(def RegisterForm
  [:and
   [:map {:closed true}
    form/AntiForgeryField
    [:email user.spec/email]
    [:password {} :string]
    [:confirm-password :string]
    [:next {:optional true} [:maybe :string]]
    [:invitation {:optional true} [:maybe :string]]]

   [:fn {:error/message "passwords don't match"
         :error/path [:confirm-password]}
    (fn [{:keys [password confirm-password]}]
      (= password confirm-password))]])

(defn handler [{:keys [context params request-method ::r/router] :as request}]
  (let [{:keys [db]} context
        {:keys [email password]} params]
    (case request-method
      :post
      (cond
        (validation.i/invalid? RegisterForm params)
        (-> (http/see-other router :register/index)
            (flash/set-field-errors (validation.i/validate RegisterForm params)))

        (user.i/exists? db email)
        (-> (http/see-other router :register/index)
            (flash/error "User already exists"))

        :else
        (let [user (user.i/create! db {:email email :password password})
              session (session/user->session user)]
          (-> (http/see-other router :root)
              (assoc :session session))))

      ;; else
      (render :next ""
              :request request
              :router router))))
