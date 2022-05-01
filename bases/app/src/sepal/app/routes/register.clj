(ns sepal.app.routes.register
  (:require [honey.sql :as sql]
            [next.jdbc :as jdbc]
            [ring.util.http-response :refer [found see-other]]
            [rum.core :as rum :refer [defc]]
            [sepal.app.html :as html]
            [sepal.app.ui.form :refer [anti-forgery-field]]
            [sepal.user.interface :as user.interface]
            [sepal.validation.interface :refer [error?]]))

(defc page-content [& {:keys [error form-values]}]
  [:div
   [:h2 {:class "text-2xl"} "Create an account"]
   [:form {:method "post" :action "/register"}
    [:fieldset {:class "flex flex-col"}
     (anti-forgery-field)
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

;; (defn create-account [db email password]
;;   (let [stmt (-> {:insert-into [:public.user]
;;                   :values [{:email email
;;                             :password [:crypt password [:gen_salt "bf"]]}]
;;                   :returning [:*]}
;;                  (sql/format))]
;;     (try
;;       (jdbc/execute-one! db stmt)
;;       (catch Exception e
;;         {:error {:message (ex-message e)}}))))

(defn user->session [user]
  ;; use (java.time.Instant/ofEpochMilli (:login-time session)) to get the login
  ;; time back as an instant
  (-> (select-keys user [:user/id :user/email])
      (assoc :login-time (inst-ms (java.time.Instant/now)))))

(defn handler [{:keys [flash params request-method] :as req}]
  (let [{:keys [context]} req
        {:keys [db]} context
        {:keys [email password]} params]
    (if (= request-method :post)
      (let [result (user.interface/create! db {:email email :password password})
            _ (tap> (str "register user: " result))]
        (if-not (error? result)
          (-> (found "/")
              (assoc :session (user->session result)))
          (-> (see-other "/register")
              (assoc :flash {:error result
                             :email email}))))
      (-> (page :error (get-in flash [:error :message])
                :form-values {:email (:email flash)})
          (html/render-html)))))
