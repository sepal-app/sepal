(ns sepal.app.routes.settings.users.archive
  (:require [sepal.app.flash :as flash]
            [sepal.app.html :as html]
            [sepal.app.http-response :as http]
            [sepal.app.routes.settings.users.index :as users.index]
            [sepal.user.interface :as user.i]
            [sepal.user.interface.activity :as user.activity]
            [zodiac.core :as z]))

(defn handler [& {:keys [::z/context path-params viewer]}]
  (let [{:keys [db]} context
        user-id (parse-long (:id path-params))
        target-user (user.i/get-by-id db user-id)]

    (cond
      (nil? target-user)
      (http/not-found)

      ;; Can't archive yourself
      (= (:user/id viewer) user-id)
      (-> (html/render-partial
            (users.index/users-table-container db viewer))
          (assoc :status 422)
          (flash/error "You cannot archive yourself"))

      ;; Can't archive the last admin
      (and (= :admin (:user/role target-user))
           (<= (user.i/count-by-role db :admin) 1))
      (-> (html/render-partial
            (users.index/users-table-container db viewer))
          (assoc :status 422)
          (flash/error "Cannot archive the last admin. Promote another user to admin first."))

      :else
      (do
        (user.i/archive! db user-id)
        (user.activity/create! db (:user/id viewer) target-user {:status :archived})
        (let [updated-user (user.i/get-by-id db user-id)
              message (format "User %s archived" (:user/email updated-user))]
          (-> (html/render-partial
                (users.index/users-table-container db viewer))
              (flash/success message)))))))
