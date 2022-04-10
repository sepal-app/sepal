(ns sepal.supabase.interface.auth
  (:require [clojure.data.json :as json]
            [hato.client :as hc]
            [camel-snake-kebab.core :as csk]
            [camel-snake-kebab.extras :as cske]
            [integrant.core :as ig]
            [next.jdbc :as jdbc]))

(defprotocol AuthService
  ;; (sign-up-with-email-and-password [email password])
  ;; (sign-up-with-phone-and-password [email password])

  (sign-in-with-email-and-password [this email password])
  ;; (sign-in-with-email-magic-link [email])

  ;; (reset-password-for-email [])

  ;; (get-user [])
  ;; (update-user [& {:keys [email password data]}])
  ;; (verify-token [this token])
  )

(defn api-> [{:keys [body]}]
  (->> (json/read-str body)
       (cske/transform-keys csk/->kebab-case-keyword)))

(defrecord SupabaseAuth [url api-key]
  AuthService
  (sign-in-with-email-and-password [_ email password]
    (-> (hc/post (str url "/auth/v1/token?grant_type=password")
                 {:headers {"content-type" "application/json"
                            "apikey" api-key}
                  :body (json/write-str {:email email :password password :returnSecureToken true})
                  :throw-exceptions? false})
        api->)))

(defn create-auth [{:keys [api-key url] :as cfg}]
  (SupabaseAuth. url api-key))

(defmethod ig/init-key ::auth [_ cfg]
  (create-auth cfg))
