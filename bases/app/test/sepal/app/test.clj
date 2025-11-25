(ns sepal.app.test
  (:require [peridot.core :as peri]
            [sepal.app.test.system :refer [*app*]]
            [sepal.test.interface :as test.i]))

(defn login
  "Login and return a peridot session"
  [email password]
  (let [{:keys [response] :as sess} (-> (peri/session *app*)
                                        (peri/request "/login"))
        token (test.i/response-anti-forgery-token response)
        sess (-> sess
                 (peri/request "/login"
                               :request-method :post
                               :params {:__anti-forgery-token token
                                        :email email
                                        :password password})
                 (peri/follow-redirect)
                 (peri/follow-redirect))]
    sess))
