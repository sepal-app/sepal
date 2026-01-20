(ns sepal.app.routes.setup.admin
  (:require [sepal.app.flash :as flash]
            [sepal.app.html :as html]
            [sepal.app.http-response :as http]
            [sepal.app.routes.auth.routes :as auth.routes]
            [sepal.app.routes.setup.layout :as layout]
            [sepal.app.routes.setup.routes :as setup.routes]
            [sepal.app.routes.setup.shared :as setup.shared]
            [sepal.app.ui.form :as form]
            [sepal.error.interface :as error.i]
            [sepal.user.interface :as user.i]
            [sepal.validation.interface :as validation.i]
            [zodiac.core :as z]))

(def FormParams
  [:and
   [:map {:closed true}
    form/AntiForgeryField
    [:email [:re {:error/message "Invalid email address"}
             #"^[^\s@]+@[^\s@]+\.[^\s@]+$"]]
    [:password [:string {:min 8 :error/message "Password must be at least 8 characters"}]]
    [:password_confirmation :string]
    [:full_name [:string {:min 1 :error/message "Full name is required"}]]]
   [:fn {:error/message "Passwords do not match"
         :error/path [:password_confirmation]}
    (fn [{:keys [password password_confirmation]}]
      (= password password_confirmation))]])

(defn admin-form [& {:keys [values errors]}]
  (form/form
    {:method "post"
     :action (z/url-for setup.routes/admin)}
    (form/anti-forgery-field)

    (form/input-field :label "Full name"
                      :name "full_name"
                      :value (:full_name values)
                      :errors (:full_name errors)
                      :required true)

    (form/input-field :label "Email"
                      :name "email"
                      :type "email"
                      :value (:email values)
                      :errors (:email errors)
                      :required true)

    (form/input-field :label "Password"
                      :name "password"
                      :type "password"
                      :value (:password values)
                      :errors (:password errors)
                      :required true
                      :minlength 8)

    (form/input-field :label "Confirm password"
                      :name "password_confirmation"
                      :type "password"
                      :value (:password_confirmation values)
                      :errors (:password_confirmation errors)
                      :required true)

    ;; Submit button inside the form
    [:div {:class "flex justify-end mt-6"}
     [:button {:type "submit"
               :class "btn btn-primary"}
      "Create Account →"]]))

(defn render-admin-exists
  "Render the view when an admin already exists - prompt to login."
  []
  (layout/layout
    :current-step 1
    :content
    (layout/step-card
      :title "Admin Account Exists"
      :content
      [:div {:class "space-y-4"}
       [:div {:class "alert alert-info"}
        [:span "An admin account already exists. Please log in to continue setup."]]
       [:p "If you created an admin account via the CLI, you need to log in before continuing with the setup wizard."]]
      :next-button
      [:a {:href (z/url-for auth.routes/login)
           :class "btn btn-primary"}
       "Log in to continue →"])))

(defn render-admin-complete
  "Render read-only view of admin account when already created and logged in."
  [& {:keys [user flash-messages]}]
  (layout/layout
    :current-step 1
    :flash-messages flash-messages
    :content
    [:div {:class "card bg-base-100 border border-base-300 shadow-sm w-full max-w-2xl"}
     [:div {:class "card-body"}
      [:h2 {:class "card-title text-2xl mb-4"} "Admin Account"]
      [:div {:class "alert alert-success mb-4"}
       [:span "✓ Admin account has been created"]]

      [:div {:class "space-y-4"}
       (form/input-field :label "Full name"
                         :name "full_name"
                         :value (:user/full-name user)
                         :input-attrs {:disabled true})
       (form/input-field :label "Email"
                         :name "email"
                         :type "email"
                         :value (:user/email user)
                         :input-attrs {:disabled true})]

      [:div {:class "flex justify-end mt-6"}
       [:a {:href (z/url-for setup.routes/server)
            :class "btn btn-primary"}
        "Next →"]]]]))

(defn render-create-admin
  "Render the admin creation form."
  [& {:keys [values errors flash-messages]}]
  (layout/layout
    :current-step 1
    :flash-messages flash-messages
    :content
    [:div {:class "card bg-base-100 border border-base-300 shadow-sm w-full max-w-2xl"}
     [:div {:class "card-body"}
      [:h2 {:class "card-title text-2xl mb-4"} "Create Admin Account"]
      [:p {:class "mb-4 text-base-content/70"}
       "Create the first administrator account for your Sepal instance."]
      (admin-form :values values :errors errors)]]))

(defn handler [{:keys [::z/context flash form-params request-method session]}]
  (let [{:keys [db]} context
        admin-exists? (setup.shared/admin-exists? db)
        logged-in? (some? (:user/id session))]

    ;; If admin exists and user is logged in, show read-only view
    (cond
      (and admin-exists? logged-in?)
      (let [user (user.i/get-by-id db (:user/id session))]
        (setup.shared/set-current-step! db 1)
        (html/render-page (render-admin-complete :user user
                                                 :flash-messages (:messages flash))))

      ;; If admin exists but not logged in, show login prompt
      admin-exists?
      (html/render-page (render-admin-exists))

      ;; Otherwise, handle admin creation
      :else
      (case request-method
        :post
        (let [result (validation.i/validate-form-values FormParams form-params)]
          (if (error.i/error? result)
            (html/render-page (render-create-admin :values form-params
                                                   :errors (validation.i/humanize result)))
            (let [{:keys [email password full_name]} result]
              (if (user.i/exists? db email)
                ;; Check email doesn't already exist
                (html/render-page (render-create-admin :values form-params
                                                       :errors {:email ["An account with this email already exists"]}))
                ;; Create the admin user
                (let [user-result (user.i/create! db {:email email
                                                      :password password
                                                      :full-name full_name
                                                      :role :admin
                                                      :status :active})]
                  (if (error.i/error? user-result)
                    (-> (http/see-other setup.routes/admin)
                        (flash/error "Failed to create admin account"))
                    (do
                      (setup.shared/set-current-step! db 2)
                      (-> (http/see-other setup.routes/server)
                          (flash/success "Admin account created successfully")
                          ;; Log the user in
                          (assoc :session {:user/id (:user/id user-result)})))))))))

        ;; GET request - show the form
        (html/render-page (render-create-admin :flash-messages (:messages flash)))))))
