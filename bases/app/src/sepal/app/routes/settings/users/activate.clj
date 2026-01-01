(ns sepal.app.routes.settings.users.activate
  (:require [sepal.app.flash :as flash]
            [sepal.app.html :as html]
            [sepal.app.routes.settings.users.index :as users.index]
            [sepal.user.interface :as user.i]
            [sepal.user.interface.activity :as user.activity]
            [zodiac.core :as z]))

(defn handler [& {:keys [::z/context path-params viewer]}]
  (let [{:keys [db]} context
        user-id (parse-long (:id path-params))
        target-user (user.i/get-by-id db user-id)]
    (user.i/activate! db user-id)
    (user.activity/create! db (:user/id viewer) target-user {:status :active})
    (let [updated-user (user.i/get-by-id db user-id)
          message (format "User %s activated" (:user/email updated-user))]
      (-> (html/render-partial
            (users.index/users-table-container db viewer :show-archived true))
          (flash/success message)))))
