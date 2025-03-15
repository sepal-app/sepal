(ns sepal.app.routes.auth.register
  (:require [sepal.app.flash :as flash]
            [sepal.app.html :as html]
            [sepal.app.http-response :as http]
            [sepal.app.params :as params]
            [sepal.app.routes.auth.page :as page]
            [sepal.app.routes.auth.routes :as auth.routes]
            [sepal.app.routes.dashboard.routes :as dashboard.routes]
            [sepal.app.session :as session]
            [sepal.app.ui.form :as form]
            [sepal.error.interface :as error.i]
            [sepal.user.interface :as user.i]
            [sepal.user.interface.spec :as user.spec]
            [zodiac.core :as z]))

(defn form [& {:keys [errors email invitation next]}]
  (form/form
    {:method "post"
     :action (z/url-for auth.routes/register)}
    (form/anti-forgery-field)
    (form/hidden-field :name "next" :value next)
    (when invitation
      (form/hidden-field :name "invitation" :value invitation))

    (form/input-field :label "Email"
                      :name "email"
                      :value email
                      :required true
                      :type "email"
                      :errors (:email errors))

    ;; TODO: bump
    ;; [:p
    ;;  [:a {:href "/forgot_password"}
    ;;   "Forgot password?"]]
    ]
    (form/input-field :label "Password"
                      :name "password"
                      :type "password"
                      :required true
                      :errors (:password errors))
    (form/input-field :label "Confirm password"
                      :name "confirm-password"
                      :type "password"
                      :required true
                      :errors (:confirm-password errors))

    [:div {:class (html/attr "flex" "flex-row" "mt-4" "justify-between" "items-center")}
     [:button {:type "submit"
               ;; :x-bind:disabled "submitting"
               :class (html/attr "inline-flex" "justify-center" "py-2" "px-4" "border"
                                 "border-transparent" "shadow-sm" "text-sm" "font-medium"
                                 "rounded-md" "text-white" "bg-green-700" "hover:bg-green-700"
                                 "focus:outline-none" "focus:ring-2" "focus:ring-offset-2"
                                 "focus:ring-green-500")}
      "Create account"]
     ]

    [:div {:class "mt-4"}
     ;; TODO
     [:a {:href "/login"} "Already have an account?"]]))

(defn render [& {:keys [email field-errors invitation next flash]}]
  (page/page :content [:div
                       [:h1 {:class "text-3xl pb-6"} "Welcome to Sepal"]
                       (form :email email
                             :invitation invitation
                             :next next
                             :errors field-errors)]
             :flash flash))

(def RegisterForm
  [:and
   [:map {:closed true}
    form/AntiForgeryField
    [:email user.spec/email]
    [:password :string]
    [:confirm-password :string]
    [:next {:optional true} [:maybe :string]]
    [:invitation {:optional true} [:maybe :string]]]

   [:fn {:error/message "passwords don't match"
         :error/path [:confirm-password]}
    (fn [{:keys [password confirm-password]}]
      (= password confirm-password))]])

(defn handler [{:keys [::z/context form-params request-method flash] :as request}]
  (let [{:keys [db]} context]
    (case request-method
      :post
      (let [{:keys [email password] :as data} (params/decode RegisterForm form-params)]
        (cond
          (error.i/error? data)
          (-> (http/found auth.routes/register)
              (flash/error "Could not register user")
              (assoc-in [:flash :values] data))

          (user.i/exists? db email)
          (-> (http/see-other auth.routes/register)
              (flash/error "User already exists"))

          :else
          (let [user (user.i/create! db {:email email :password password})
                session (session/user->session user)]
            (-> (http/see-other dashboard.routes/index)
                (assoc :session session)))))

      ;; else
      (render :next ""
              :flash flash))))
