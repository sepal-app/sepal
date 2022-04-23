(ns sepal.app.routes.login
  (:require [honey.sql :as sql]
            [next.jdbc :as jdbc]
            [reitit.core :as r]
            [rum.core :as rum :refer [defc]]
            [sepal.app.html :as html]
            [sepal.app.http-response :refer [found see-other]]
            [sepal.app.ui.form :refer [anti-forgery-field]]))

(defc page-content [& {:keys [error form-values]}]
  [:div
   [:h2 {:class "text-2xl"} "Login"]
   [:form {:method "post" :action "/login"}
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
  (html/root-template
   {:content (page-content :error error :form-values form-values)}))

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
(defn user->session [user]
  ;; use (java.time.Instant/ofEpochMilli (:login-time session)) to get the login
  ;; time back as an instant
  (-> (select-keys user [:user/id :user/email])
      (assoc :login-time (inst-ms (java.time.Instant/now)))))

(defn handler [{:keys [context flash params ::r/router request-method]}]
  (let [{:keys [db]} context
        {:keys [email password]} params]
    (if (= request-method :post)
      (let [user (verify-password db email password)
            error (when-not user {:message "Invalid password"})
            session (when-not error (user->session user))]
        (tap> (str "register session: " session))
        (if-not error
          (-> (found router :root)
              (assoc :session session))
          (-> (see-other router :login)
              (assoc :flash {:error error
                             :email email}))))
      (-> (page :error (get-in flash [:error :message])
                :form-values {:email (:email flash)})
          (html/render-html)))))
