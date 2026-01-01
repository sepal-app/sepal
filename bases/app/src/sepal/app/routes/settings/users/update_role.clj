(ns sepal.app.routes.settings.users.update-role
  (:require [sepal.app.flash :as flash]
            [sepal.app.html :as html]
            [sepal.app.http-response :as http]
            [sepal.app.routes.settings.routes :as settings.routes]
            [sepal.app.routes.settings.users.index :as users.index]
            [sepal.user.interface :as user.i]
            [sepal.user.interface.activity :as user.activity]
            [zodiac.core :as z]))

(defn- validate-role-change [db target-user new-role]
  (cond
    ;; If demoting the last admin, block it
    (and (= :admin (:user/role target-user))
         (not= :admin new-role)
         (<= (user.i/count-by-role db :admin) 1))
    {:error "Cannot remove the last admin. Promote another user to admin first."}

    :else nil))

(defn- self-demotion?
  "Returns true if the viewer is demoting themselves from admin."
  [viewer target-user new-role]
  (and (= (:user/id viewer) (:user/id target-user))
       (= :admin (:user/role viewer))
       (not= :admin new-role)))

(defn handler [& {:keys [::z/context path-params form-params viewer]}]
  (let [{:keys [db]} context
        user-id (parse-long (:id path-params))
        new-role (keyword (get form-params "role"))
        target-user (user.i/get-by-id db user-id)]

    (cond
      (nil? target-user)
      (http/not-found)

      ;; Validation error
      (some? (validate-role-change db target-user new-role))
      (let [{:keys [error]} (validate-role-change db target-user new-role)]
        (-> (html/render-partial
              (users.index/users-table-container db viewer))
            (assoc :status 422)
            (flash/error error)))

      ;; Self-demotion: update role and redirect to profile (they lose admin access)
      (self-demotion? viewer target-user new-role)
      (do
        (user.i/update! db user-id {:role new-role})
        (user.activity/create! db (:user/id viewer) target-user {:role new-role})
        (-> (http/see-other settings.routes/profile)
            (flash/success (format "Your role has been changed to %s" (name new-role)))))

      :else
      (do
        (user.i/update! db user-id {:role new-role})
        (user.activity/create! db (:user/id viewer) target-user {:role new-role})
        (let [updated-user (user.i/get-by-id db user-id)
              message (format "User %s role changed to %s"
                              (:user/email updated-user)
                              (name new-role))]
          (-> (html/render-partial
                (users.index/users-table-container db viewer))
              (flash/success message)))))))
