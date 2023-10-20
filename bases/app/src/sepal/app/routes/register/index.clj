(ns sepal.app.routes.register.index
  (:require
   [reitit.core :as r]
   [sepal.app.flash :as flash]
   [sepal.app.html :as html]
   [sepal.app.http-response :as http]
   [sepal.app.router :refer [url-for]]
   [sepal.app.session :as session]
   [sepal.app.ui.base :as base]
   [sepal.app.ui.form :as form]
   [sepal.user.interface :as user.i]
   [sepal.validation.interface :as validation.i :refer [error?]]))

(defn render [& {:keys [_error invitation next request router]}]
  (let [{:keys [params]} request]

    ;; TODO: We need a way to keep the values in the form field after the
    ;; submit. Maybe we could use htmx so that we intercept the redirect and
    ;; inject the form into the page with the errors and values in tact
    (-> [:div
         [:div {:class "grid grid-cols-3 gap-12 pl-8"}
          [:div {:class "col-start-1 flex flex-col justify-center"}
           [:h1 {:class "mb-12 text-3xl"} "Welcome to Sepal"]
           [:form {:method "post"
                   :action (url-for router :register/index)}
            (form/anti-forgery-field)
            (form/hidden-field :name "next" :value next)
            (when invitation
              (form/hidden-field :name "invitation" :value invitation))

            (form/input-field :label "Email"
                              :name "email"
                              :value (:email params)
                              :required true
                              :type "email"
                              :errors (flash/field-error request :email))
            (form/input-field :label "Password"
                              :name "password"
                              :type "password"
                              :required true
                              :value (:password params)
                              :errors (flash/field-error request :password))
            ;; TODO: Validate that the password match client side
            (form/input-field :label "Confirm password"
                              :name "confirm-password"
                              :type "password"
                              :required true
                              :value (:confirm-password params)
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
             [:a {:href "/login"} "Already have an account?"]]]

           ;; TODO: Non field errors

           ;; (when error
           ;;   [:div {:class "rounded-md bg-red-50 p-4 text-red-800"
           ;;          :x-show "!submitting"}
           ;;    error])

           (flash/banner (get-in request [:flash :messages]))]

          [:div {:class "col-span-2"}
           [:img {:src (html/static-url "img/auth/jose-fontano-WVAVwZ0nkSw-unsplash_1080x1620.jpg")
                  :class "h-screen w-full object-cover object-center"
                  :alt "login banner"}]]]]

        (base/html)
        (html/render-html))))

(def RegisterForm
  [:and
   [:map {:closed true}
    form/AntiForgeryField
    ;; TODO: Use validate.i/email-re
    [:email :string]
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
        (do
          (-> (http/see-other router :register/index)
              (flash/set-field-errors (validation.i/validate RegisterForm params))))

        (user.i/exists? db email)
        (do
          (-> (http/see-other router :register/index)
              (flash/error "User already exists")))

        :else
        (do
          (let [user (user.i/create! db {:email email :password password})
                session (session/user->session user)]
            (-> (http/see-other router :root)
                (assoc :session session)))))

      ;; else
      (render :next ""
              :request request
              :router router))))
