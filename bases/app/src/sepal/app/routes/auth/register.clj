(ns sepal.app.routes.auth.register
  (:require [sepal.app.flash :as flash]
            [sepal.app.html :as html]
            [sepal.app.http-response :as http]
            [sepal.app.params :as params]
            [sepal.app.routes.auth.page :as page]
            [sepal.app.routes.auth.routes :as auth.routes]
            [sepal.app.session :as session]
            [sepal.app.ui.form :as form]
            [sepal.user.interface :as user.i]
            [sepal.user.interface.spec :as user.spec]
            [zodiac.core :as z]))

(defn form [& {:keys [request email invitation next]}]
  [:form {:method "post"
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
    ;; TODO: bump
    ;; [:p
    ;;  [:a {:href "/forgot_password"}
    ;;   "Forgot password?"]]
    ]

   [:div {:class "mt-4"}
    ;; TODO
    [:a {:href "/login"} "Already have an account?"]]])

(defn render [& {:keys [email #_field-errors invitation next request flash]}]
  (page/page :content [:div
                       [:h1 {:class "text-3xl pb-6"} "Welcome to Sepal"]
                       (form :email email
                             :invitation invitation
                             :next next
                             :request request)]
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

(defn handler [{:keys [::z/context form-params request-method] :as request}]
  (let [{:keys [db]} context
        {:keys [email password]} (params/decode RegisterForm form-params)]

    (case request-method
      :post
      (cond
        ;; (validation.i/invalid? RegisterForm params)
        ;; (do
        ;;   ;; (tap> (validation.i/validate RegisterForm params))
        ;;   (-> (http/see-other auth.routes/register)
        ;;       (flash/set-field-errors (validation.i/validate RegisterForm params))))

        ;; TODO: This doesn't seem to be showing the error
        (user.i/exists? db email)
        (-> (http/see-other auth.routes/register)
            (flash/error "User already exists"))

        :else
        (let [user (user.i/create! db {:email email :password password})
              session (session/user->session user)]
          (-> (http/see-other :root)
              (assoc :session session))))

      ;; else
      (render :next ""
              :request request))))
