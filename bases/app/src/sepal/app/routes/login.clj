(ns sepal.app.routes.login
  (:require [clojure.data.json :as json]
            [ring.util.http-response :refer [found ok see-other]]
            [rum.core :as rum :refer [defc]]
            [sepal.supabase.interface.auth :refer [sign-in-with-email-and-password]]
            [sepal.app.html :as html]))

(defc page-content [& {:keys []}]
  [:div
   [:h2 {:class "text-2xl"} "Login"]
   [:form {:method "post" :action "/login"}
    [:fieldset {:class "flex flex-col"}
     (html/anti-forgery-field)
     [:input {:type "email"
              :name "email"}]
     [:input {:type "password"
              :name "password"}]
     [:button {:type "submit"} "Login"]]]])

(defn page [& {:keys [error form-values]}]
  (-> (html/root-template
       {:content (page-content :error error :form-values form-values)})))

(defn handler [{:keys [params request-method] :as req}]
  (let [{:keys [context]} req
        {:keys [supabase]} context
        {:keys [email password]} params]
    (if (= request-method :post)
      (let [session (sign-in-with-email-and-password supabase email password)
            error (:error session)]
        (if-not error
          (-> (found "/")
              (assoc :session session))
          (-> (see-other "/login")
              (assoc :flash error))))
      (-> (page)
          (html/render-html)))))
