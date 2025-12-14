(ns sepal.app.session
  (:import [java.time Instant]))

(defn user->session [user]
  ;; use (java.time.Instant/ofEpochMilli (:login-time session)) to get the login
  ;; time back as an instant
  (-> (select-keys user [:user/id :user/email :user/full-name :user/role])
      (assoc :login-time (inst-ms (Instant/now)))))
