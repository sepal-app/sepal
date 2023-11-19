(ns sepal.app.routes.login
  (:require [reitit.core :as r]
            [sepal.database.interface :as db.i]
            [sepal.app.flash :as flash]
            [sepal.app.html :as html]
            [sepal.app.http-response :as http]
            [sepal.app.router :refer [url-for]]
            [sepal.app.session :as session]
            [sepal.app.ui.base :as base]
            [sepal.app.ui.form :as form]))

(defn verify-password [db email password]
  (->> {:select
        :*
        :from :public.user
        :where [:and
                [:= :email email]
                [[:= :password
                  [:'crypt password :password]]]]}
       (db.i/execute-one! db)))

(defn send-verification-email [email]
  ;; See https://app.sendgrid.com/guide/integrate/langs/java

;;   // using SendGrid's Java Library
;; // https://github.com/sendgrid/sendgrid-java
;; import com.sendgrid.*;
;; import java.io.IOException;

;; public class Example {
;;   public static void main(String[] args) throws IOException {
;;     Email from = new Email("test@example.com");
;;     String subject = "Sending with SendGrid is Fun";
;;     Email to = new Email("test@example.com");
;;     Content content = new Content("text/plain", "and easy to do anywhere, even with Java");
;;     Mail mail = new Mail(from, subject, to, content);

;;     SendGrid sg = new SendGrid(System.getenv("SENDGRID_API_KEY"));
;;     Request request = new Request();
;;     try {
;;       request.setMethod(Method.POST);
;;       request.setEndpoint("mail/send");
;;       request.setBody(mail.build());
;;       Response response = sg.api(request);
;;       System.out.println(response.getStatusCode());
;;       System.out.println(response.getBody());
;;       System.out.println(response.getHeaders());
;;     } catch (IOException ex) {
;;       throw ex;
;;     }
;;   }
;; }
  )

(defn render [& {:keys [email field-errors invitation next router flash]}]
  (tap> (str "email: " email))

  (-> [:div
       [:div {:class "grid grid-cols-3 gap-12 pl-8"}
        [:div {:class "col-start-1 flex flex-col justify-center"}
         [:h1 {:class "mb-12 text-3xl"}]
         [:form {:method "post"
                 :action (url-for router :auth/login)}
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
            [:a {:href "/forgot_password"}
             "Forgot password?"]]]

          [:div {:class "mt-4"}
           [:a {:href (url-for router :register/index)} "Don't have an account?"]]]

;; TODO: Non field errors

          ;; (when error
          ;;   [:div {:class "rounded-md bg-red-50 p-4 text-red-800"
          ;;          :x-show "!submitting"}
          ;;    error])
         ]
        [:div {:class "col-span-2"}
         [:img {:src (html/image-url "img/auth/jose-fontano-WVAVwZ0nkSw-unsplash_1080x1620.jpg")
                :class "h-screen w-full object-cover object-center"
                :alt "login banner"}]]]
       (flash/banner (:messages flash))]

      (base/html)
      (html/render-html)))

(defn handler [{:keys [context flash params request-method ::r/router]}]
  (let [{:keys [db]} context
        {:keys [email password invitation next]} params]
    (case request-method
      :post
      (let [user (verify-password db email password)
            error (when-not user "Invalid password")
            session (when-not error (session/user->session user))]
        (if-not error
          (-> (http/see-other router :root)
              (assoc :session session))
          ;; TODO: pass params on redirect
          (-> (http/see-other router :auth/login)
              (flash/error error))))

      ;; else
      (render :next next
              :email email
              :invitation invitation
              :next next
              :router router
              :field-errors nil
              :flash flash))))
