(ns sepal.app.routes.login
  (:require [honey.sql :as sql]
            [next.jdbc :as jdbc]
            [ring.util.http-response :refer [found see-other]]
            [rum.core :as rum :refer [defc]]
            [sepal.app.html :as html]))

(defc page-content [& {:keys [error form-values]}]
  [:div
   [:h2 {:class "text-2xl"} "Login"]
   [:form {:method "post" :action "/login"}
    [:fieldset {:class "flex flex-col"}
     (html/anti-forgery-field)
     [:input {:type "email"
              :name "email"
              :value (:email form-values)}]
     [:input {:type "password"
              :name "password"}]
     [:button {:type "submit"} "Login"]]]
   (when error
     [:div error])])

(defn page [& {:keys [error form-values]}]
  (-> (html/root-template
       {:content (page-content :error error :form-values form-values)})))

(defn verify-password [db email password]
  (let [stmt (-> {:select :*
                  :from :public.user
                  :where [:and
                          [:= :email email]
                          [[:= :password
                            [:'crypt password :password]]]]}
                 (sql/format {:pretty true}))]
    (-> (jdbc/execute! db stmt)
        (first))))

(defn user->session [user]
  ;; use (java.time.Instant/ofEpochMilli (:login-time session)) to get the login
  ;; time back as an instant
  (-> (select-keys user [:user/id :user/email])
      (assoc :login-time (inst-ms (java.time.Instant/now)))))

(defn handler [{:keys [context flash params request-method]}]
  (let [{:keys [db]} context
        {:keys [email password]} params]
    (if (= request-method :post)
      (let [user (verify-password db email password)
            error (when-not user {:message "Invalid password"})
            session (when-not error (user->session user))]
        (if-not error
          (-> (found "/")
              (assoc :session session))
          (-> (see-other "/login")
              (assoc :flash {:error error
                             :email email}))))
      (-> (page :error (get-in flash [:error :message])
                :form-values {:email (:email flash)})
          (html/render-html)))))
